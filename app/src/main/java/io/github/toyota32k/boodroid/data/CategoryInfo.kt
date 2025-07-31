package io.github.toyota32k.boodroid.data

import android.graphics.Color
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.common.toIterable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import androidx.core.graphics.toColorInt
import java.util.function.IntFunction

data class CategoryInfo(val label:String, val color:Color,val sort:Int, val svgPath:String) {
    companion object {
        val all = CategoryInfo("All", Color.valueOf("Blue".toColorInt()), 0, "M17.9,17.39C17.64,16.59 16.89,16 16,16H15V13A1,1 0 0,0 14,12H8V10H10A1,1 0 0,0 11,9V7H13A2,2 0 0,0 15,5V4.59C17.93,5.77 20,8.64 20,12C20,14.08 19.2,15.97 17.9,17.39M11,19.93C7.05,19.44 4,16.08 4,12C4,11.38 4.08,10.78 4.21,10.21L9,15V16A2,2 0 0,0 11,18M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z")
        private fun JSONObject.getStringOrNull(key:String):String? {
            return if(has(key)) getString(key) else null
        }
        fun fromJson(j:JSONObject): CategoryInfo? {
            val label = j.getStringOrNull("label") ?: return null
            val colorStr = j.getStringOrNull("color")
            val color = if(colorStr!=null) Color.valueOf(colorStr.toColorInt()) else Color.valueOf(Color.BLACK)
            val sort = j.getStringOrNull("sort")?.toIntOrNull(10) ?: 0
            val svg = j.getStringOrNull("svg") ?: "M168H8V16H16V8Z"
            return CategoryInfo(label, color, sort, svg)
        }
    }
}

class CategoryList(private val list:List<CategoryInfo>, val unchecked:String) : List<CategoryInfo> by list {

    fun isValidCategory(c:String):Boolean {
        return c=="All" || firstOrNull { it.label == c } != null
    }

    companion object {
        val emptyList = CategoryList(listOf(CategoryInfo.all), "")
        suspend fun getCategoryList(capability: Capability): CategoryList {
            if (!capability.hasCategory) {
                return emptyList
            }

            return withContext(Dispatchers.IO) {
                val url = capability.baseUrl + "categories"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                try {
                    val json = NetClient.executeAndGetJsonAsync(req)
                    val unchecked = json.safeGetNullableString("unchecked") ?: "Unchecked"
                    val jsonList = json.getJSONArray("categories")
                        ?: throw IllegalStateException("Server Response Null List.")
                    val list = jsonList.toIterable()
                        .mapNotNull { j -> CategoryInfo.fromJson(j as JSONObject) }
                        .sortedBy { it.sort }.toList().let { org ->
                            if(org.isEmpty()||org[0].label!="All") {
                                listOf(CategoryInfo.all)+ org
                            } else org
                        }
                    CategoryList(list, unchecked)
                } catch (e: Throwable) {
                    Data.logger.stackTrace(e)
                    emptyList
                }
            }
        }
    }
}