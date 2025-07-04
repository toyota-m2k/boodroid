package io.github.toyota32k.boodroid.data

import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.IBinding
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.MaterialRadioButtonUnSelectableGroupBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.PathUtil
import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.utils.lifecycle.asMutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class RatingInfo(val rating:Int, val label:String, val svgPath:String) {
    constructor(j: JSONObject) :this(
        j.getInt("rating"),
        j.getString("label"),
        j.optString("svg", ""),
    )
    val drawable: Drawable? = PathUtil.shapeDrawableFromPath(svgPath)
}
class RatingList(private val list: List<RatingInfo>, val default:Int) : List<RatingInfo> by list {
    fun isValidRating(v:Int):Boolean {
        return firstOrNull { it.rating == v } != null
    }

    inner class IDResolver : IIDValueResolver<Int> {
        override fun id2value(id: Int): Int {
            val n = viewIds.indexOf(id)
            if(n<0||size<=n) return default
            return this@RatingList[n].rating
        }

        override fun value2id(v: Int): Int {
            val index = this@RatingList.indexOfFirst { it.rating == v }
            return if(index<0||viewIds.size<=index) 0 else viewIds[index]
        }

    }

    val idResolver: IIDValueResolver<Int> get() = IDResolver()

    fun bind(owner:LifecycleOwner, view: MaterialButtonToggleGroup, data:MutableStateFlow<Int>):IBinding {
        for(i in 0.until(viewIds.size)) {
            val btn = view.findViewById<MaterialButton>(viewIds[i])
            if(i>=this.size) {
                btn.visibility = View.GONE
            } else {
                btn.visibility = View.VISIBLE
                btn.icon = this[i].drawable ?: ResourcesCompat.getDrawable(view.resources, defaultIcons[i], null)
            }
        }
        return MaterialRadioButtonUnSelectableGroupBinding.create(owner, view, data.asMutableLiveData(owner), idResolver)
    }

    companion object {
        val emptyList:RatingList = RatingList(emptyList(), 0)

        val viewIds: Array<Int> = arrayOf(
            R.id.tg_rating_dreadful,
            R.id.tg_rating_bad,
            R.id.tg_rating_normal,
            R.id.tg_rating_good,
            R.id.tg_rating_excellent,
        )
        val defaultIcons: Array<Int> = arrayOf(
            R.drawable.ic_dreadful,
            R.drawable.ic_bad,
            R.drawable.ic_normal,
            R.drawable.ic_good,
            R.drawable.ic_excellent,
        )
        suspend fun getRatingList(capability: Capability):RatingList {
            if (!capability.hasRating) {
                return emptyList
            }

            return withContext(Dispatchers.IO) {
                val url = capability.baseUrl + "ratings"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                try {
                    val json = NetClient.executeAndGetJsonAsync(req)
                    val default = json.safeGetInt("default", 0)
                    val jsonList = json.getJSONArray("ratings")
                        ?: throw IllegalStateException("Server Response Null List.")
                    RatingList(
                        jsonList.toIterable().map { j -> RatingInfo(j as JSONObject) }.toList(),
                        default)
                } catch (e: Throwable) {
                    Data.logger.stackTrace(e)
                    emptyList
                }
            }
        }
    }
}

fun Binder.bindRatingList(view: MaterialButtonToggleGroup, data:MutableStateFlow<Int>, ratingList: RatingList):Binder {
    return add(ratingList.bind(requireOwner, view, data))
}
