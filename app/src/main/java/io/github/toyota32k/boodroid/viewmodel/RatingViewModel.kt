package io.github.toyota32k.boodroid.viewmodel

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
            } catch (e:Throwable) {
                logger.stackTrace(e)
                hasError.value = true
            } finally {
                busy.value = false
            }
        }
    }

    fun putToServer() {
        if(hasError.value) return
        val url = AppViewModel.url.reputation ?: return
        val json = JSONObject()
            .put("id", id)
        var modified = false
        if(rating.value!=ratingOrg) {
            modified = true
            json.put("rating", rating.value)
        }
        if(mark.value!=markOrg) {
            modified = true
            json.put("mark", mark.value)
        }
        if(category.value!=categoryOrg) {
            modified = true
            json.put("category", category.value)
        }
        if(!modified) return
        val req = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NetClient.executeAsync(req).close()
            } catch (e:Throwable) {
                logger.stackTrace(e)
            }
        }
    }

    companion object {
        val logger = UtLog("RD", BooApplication.logger)
    }
}