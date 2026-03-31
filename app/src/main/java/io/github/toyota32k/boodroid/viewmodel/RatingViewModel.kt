package io.github.toyota32k.boodroid.viewmodel

import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.logger.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RatingViewModel : UtDialogViewModel() {
    private lateinit var current:VideoItem
    private val id:String get() = current.id
    val name = MutableStateFlow("")
    val rating = MutableStateFlow(0)
    val mark = MutableStateFlow(0)
    val category = MutableStateFlow("")
    var supportSyncItemSelection: Boolean = false
    var supportRating: Boolean = false
    var supportMark: Boolean = false
    var supportCategory: Boolean = false
    val busy = MutableStateFlow(true)
    val hasError = MutableStateFlow(false)

    private lateinit var categoryOrg:String
    private var ratingOrg:Int = 0
    private var markOrg:Int = 0

    fun prepare(item: VideoItem, scope: CoroutineScope) {
        current = item
        name.value = item.name
        busy.value = true
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

                rating.onEach { r->
                    if(r!=ratingOrg) {
                        putToServer()
                    }
                }.onCompletion {
                    logger.debug("rating.onCompletion")
                }.launchIn(scope)
                mark.onEach { m ->
                    if (m != markOrg) {
                        putToServer()
                    }
                }.onCompletion {
                    logger.debug("mark.onCompletion")
                }.launchIn(scope)
                category.onEach { c ->
                    if (c != categoryOrg) {
                        putToServer()
                    }
                }.onCompletion {
                    logger.debug("category.onCompletion")
                }.launchIn(scope)
            } catch (e:Throwable) {
                logger.stackTrace(e)
                hasError.value = true
            } finally {
                busy.value = false
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

    companion object {
        val logger = UtLog("RD", BooApplication.logger)
    }
}