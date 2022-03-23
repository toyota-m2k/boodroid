package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.common.UtImmortalTaskContextSource
import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.data.Mark
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.Rating
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.ytremote.data.CategoryList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RatingViewModel : ViewModel(), IUtImmortalTaskMutableContextSource by UtImmortalTaskContextSource() {
    private lateinit var current:VideoItem
    private val id:String get() = current.id
    val name = MutableLiveData("")
    val rating = MutableLiveData(Rating.NORMAL)
    val mark = MutableLiveData<Mark>(Mark.NONE)
    val category = MutableLiveData<String>("")
    val busy = MutableLiveData<Boolean>(true)
    val hasError = MutableLiveData(false)
    val categoryList = CategoryList().apply { update() }

    private lateinit var categoryOrg:String
    private lateinit var ratingOrg:Rating
    private lateinit var markOrg:Mark

    fun prepare(item: VideoItem, scope: CoroutineScope) {
        current = item
        name.value = item.name
        busy.value = true
        hasError.value = false
        val vm = AppViewModel.instance

        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val url = vm.settings.urlReputation() + "?id=${item.id}"
                    val req = Request.Builder().url(url).get().build()
                    NetClient.executeAndGetJsonAsync(req)
                }
                ratingOrg = Rating.valueOf(json.safeGetInt("rating", Rating.NORMAL.v))
                markOrg = Mark.valueOf(json.safeGetInt("mark", Mark.NONE.v))
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
        if(hasError.value==true) return
        val vm = AppViewModel.instance
        val url = vm.settings.urlReputation()
        val json = JSONObject()
            .put("id", id)
        if(rating.value!=null && rating.value!=ratingOrg) {
            json.put("rating", rating.value!!.v)
        }
        if(mark.value!=null && mark.value!=markOrg) {
            json.put("mark", mark.value!!.v)
        }
        if(category.value!=null && category.value!=categoryOrg) {
            json.put("category", category.value)
        }
        val req = Request.Builder()
            .url(url)
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            NetClient.executeAsync(req).close()
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