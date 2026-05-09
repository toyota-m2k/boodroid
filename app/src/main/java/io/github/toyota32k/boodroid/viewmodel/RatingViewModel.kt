package io.github.toyota32k.boodroid.viewmodel

import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.offline.OfflineManager.Companion.keyUrl
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.logger.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RatingViewModel : UtDialogViewModel(), OfflineManager.IDownloadProgress {
    private lateinit var current:VideoItem
    private val id:String get() = current.id
    val name = MutableStateFlow("")
    val rating = MutableStateFlow(0)
    val mark = MutableStateFlow(0)
    val category = MutableStateFlow("")
    val offline = MutableStateFlow(false)
    var supportSyncItemSelection: Boolean = false
    var supportRating: Boolean = false
    var supportMark: Boolean = false
    var supportCategory: Boolean = false
    val busy = MutableStateFlow(true)
    val prepared = MutableStateFlow(false)
    val hasError = MutableStateFlow(false)

    val offlineDataHandling = MutableStateFlow(false)
    val uploadProgress = MutableStateFlow<Int>(0)

    private lateinit var categoryOrg:String
    private var ratingOrg:Int = 0
    private var markOrg:Int = 0

    fun prepare(item: VideoItem, scope: CoroutineScope) {
        current = item
        name.value = item.name
        hasError.value = false
        this.supportSyncItemSelection = AppViewModel.instance.supportSyncItemSelection
        this.supportRating = AppViewModel.instance.supportRating
        this.supportMark = AppViewModel.instance.supportMark
        this.supportCategory = AppViewModel.instance.supportCategory

        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val url = AppViewModel.url.reputation(item.id) ?: return@withContext JSONObject()
                    val req = Request.Builder().url(url).get().build()
                    NetClient.executeAndGetJsonAsync(req)
                }
                val cap = AppViewModel.instance.capability.value
                ratingOrg = json.safeGetInt("rating", cap.ratingList.default)
                markOrg = json.safeGetInt("mark", 0)
                categoryOrg = json.safeGetNullableString("category") ?: "unchecked"
                rating.value = ratingOrg
                mark.value = markOrg
                category.value = categoryOrg
                offline.value = OfflineManager.instance.isRegistered(item)

                rating.onEach { r->
                    if(r!=ratingOrg) {
                        putToServer()
                    }
                }.launchIn(scope)
                mark.onEach { m ->
                    if (m != markOrg) {
                        putToServer()
                    }
                }.launchIn(scope)
                category.onEach { c ->
                    if (c != categoryOrg) {
                        putToServer()
                    }
                }.launchIn(scope)
                offline.onEach { o ->
                    offlineDataHandling.value = true
                    busy.value = true
                    try {
                        val offlineList = mutableListOf<IMediaSource>(
                            *OfflineManager.instance.getOfflineVideos().toTypedArray()
                        )
                        val index = offlineList.indexOfFirst { it.keyUrl() == current.keyUrl() }
                        if (o && index < 0) {
                            offlineList.add(current)
                        } else if (!o && index >= 0) {
                            offlineList.removeAt(index)
                        } else {
                            return@onEach
                        }
                        OfflineManager.instance.setOfflineVideos(offlineList, AppViewModel.instance.preferAudioOnOfflineMode,this@RatingViewModel)
                    } finally {
                        offlineDataHandling.value = false
                        busy.value = false
                    }
                }.launchIn(scope)
            } catch (e:Throwable) {
                logger.stackTrace(e)
                hasError.value = true
            } finally {
                busy.value = false
                prepared.value = true
            }
        }
    }

    private suspend fun putToServer() {
        if(hasError.value) return
        val url = AppViewModel.url.reputation ?: return
        val json = JSONObject()
            .put("id", id)
        var modified = false
        if(rating.value!=ratingOrg) {
            modified = true
            ratingOrg = rating.value
            json.put("rating", rating.value)
        }
        if(mark.value!=markOrg) {
            modified = true
            markOrg = mark.value
            json.put("mark", mark.value)
        }
        if(category.value!=categoryOrg) {
            modified = true
            categoryOrg = category.value
            json.put("category", category.value)
        }
        if(!modified) return
        busy.value = true
        val req = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            try {
                NetClient.executeAsync(req).close()
            } catch (e:Throwable) {
                logger.stackTrace(e)
            }
        }
        busy.value = false
    }

    // region IDownloadProgress

    override fun reset() {
        uploadProgress.value = 0
    }

    override fun setMessage(msg: String) {
        logger.debug(msg)
    }

    override fun setCountProgress(totalCount: Int, currentIndex: Int) {
        logger.debug {"count: $totalCount, index: $currentIndex" }
    }

    override fun setBytesProgress(contentLength: Long, receivedBytes: Long) {
        uploadProgress.value = if (contentLength!=0L) (receivedBytes*100/contentLength).toInt() else 0
    }
    // endregion

    companion object {
        val logger = UtLog("RD", BooApplication.logger)
    }
}