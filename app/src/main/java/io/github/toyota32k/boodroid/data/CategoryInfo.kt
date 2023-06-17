package io.github.toyota32k.ytremote.data

import android.graphics.Color
import androidx.lifecycle.MutableLiveData
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

data class CategoryInfo(val label:String, val color:Color,val sort:Int) {
    constructor(j:JSONObject) :this(
            j.getString("label"),
            Color.valueOf(Color.parseColor(j.getString("color"))),
            j.getString("sort").toInt(10))

//    val icon: Drawable? by lazy {
//        VectorDrawable.createFromStream()
//    }


//    companion object {
//        val all = CategoryInfo("All", Color.valueOf(Color.parseColor("Blue")), 0/*, "M17.9,17.39C17.64,16.59 16.89,16 16,16H15V13A1,1 0 0,0 14,12H8V10H10A1,1 0 0,0 11,9V7H13A2,2 0 0,0 15,5V4.59C17.93,5.77 20,8.64 20,12C20,14.08 19.2,15.97 17.9,17.39M11,19.93C7.05,19.44 4,16.08 4,12C4,11.38 4.08,10.78 4.21,10.21L9,15V16A2,2 0 0,0 11,18M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z"*/)
//    }
}

class CategoryList() {
    val list = MutableLiveData<List<CategoryInfo>>(emptyList())
    val hasData:Boolean
        get() = list.value?.isNotEmpty() ?: false
    private val busy = AtomicBoolean(false)
    val currentLabel = MutableLiveData<String>("All")

//    val categoryInfo = combineLatest(currentLabel, list) {label, list->
//        if(label==null || list==null) {
//            CategoryInfo.all
//        } else {
//            list.find { it.label==label } ?: CategoryInfo.all
//        }
//    }

    var category:String?
        get() = currentLabel.value ?: "All"
        set(v) { currentLabel.value = (v ?: "All") }

    fun reset() {
        list.value = emptyList()
        currentLabel.value = "All"
    }

    fun update() {
        if(!AppViewModel.instance.capability.value.hasCategory) {
            reset()
        }
        if(hasData) return
        if(busy.getAndSet(true)) return
        if(!AppViewModel.instance.settings.isValid) return
        CoroutineScope(Dispatchers.IO).launch {
            val url = AppViewModel.instance.settings.urlToListCategories()
            val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()

            val list = try {
                val json = NetClient.executeAndGetJsonAsync(req)
                val jsonList = json.getJSONArray("categories") ?: throw IllegalStateException("Server Response Null List.")
                jsonList.toIterable().map { j -> CategoryInfo(j as JSONObject) }.sortedBy { it.sort }.toList()
            } catch (e: Throwable) {
                UtLogger.stackTrace(e)
                null
            }
            withContext(Dispatchers.Main) {
                if(list!=null) {
                    this@CategoryList.list.value = list
                    if(!list.any { it.label == currentLabel.value }) {
                        currentLabel.value = "All"
                    }
                } else {
                    reset()
                }
                busy.set(false)
            }
        }
    }
}