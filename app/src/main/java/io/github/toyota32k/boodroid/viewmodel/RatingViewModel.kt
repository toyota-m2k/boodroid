package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.ViewModel
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.common.UtImmortalTaskContextSource
import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RatingViewModel : ViewModel(), IUtImmortalTaskMutableContextSource by UtImmortalTaskContextSource() {
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
                    val url = AppViewModel.url.reputation(item.id)
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
        val url = AppViewModel.url.reputation
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
            /**
         * タスク開始時の初期化用
         */
        /**
         * タスク開始時の初期化用
         */
        fun createBy(task: IUtImmortalTask, initialize:((RatingViewModel)->Unit)?=null) : RatingViewModel
                = UtImmortalViewModelHelper.createBy(RatingViewModel::class.java, task, initialize)

            /**
         * ダイアログから取得する用
         */

        /**
         * ダイアログから取得する用
         */
        fun instanceFor(dialog: IUtDialog):RatingViewModel
                = UtImmortalViewModelHelper.instanceFor(RatingViewModel::class.java, dialog)
    }
}