package io.github.toyota32k.boodroid.data

import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.IIDValueResolver
import io.github.toyota32k.binder.MaterialRadioButtonUnSelectableGroupBinding
import io.github.toyota32k.binder.MaterialToggleButtonGroupBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.utils.asMutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class MarkInfo(val mark:Int, val label:String, val svgPath:String) {
    constructor(j: JSONObject) :this(
        j.getInt("mark"),
        j.getString("label"),
        j.optString("svg", ""),
    )
}
class MarkList(private val list:List<MarkInfo>) : List<MarkInfo> by list {
    fun isValidMark(m:Int):Boolean {
        return firstOrNull { it.mark == m } != null
    }
    fun isValidMarks(m:List<Int>):Boolean {
        return m.all { isValidMark(it) }
    }

    inner class IDResolver : IIDValueResolver<Int> {
        override fun id2value(id: Int): Int {
            val n = viewIds.indexOf(id)
            if(n<0||size<=n) return 0
            return this@MarkList[n].mark
        }

        override fun value2id(v: Int): Int {
            val index = this@MarkList.indexOfFirst { it.mark == v }
            return if(index<0|| viewIds.size<=index) 0 else viewIds[index]
        }
    }

    val idResolver: IIDValueResolver<Int> get() = IDResolver()

    companion object {
        val emptyList: MarkList = MarkList(emptyList())

        val viewIds: Array<Int> = arrayOf(
            R.id.tg_mark_star,
            R.id.tg_mark_flag,
            R.id.tg_mark_heart,
        )
        val defaultIcons: Array<Int> = arrayOf(
            R.drawable.ic_star,
            R.drawable.ic_flag,
            R.drawable.ic_heart,
        )

        suspend fun getMarkList(capability: Capability): MarkList {
            if (!capability.hasMark) {
                return emptyList
            }

            val list = withContext(Dispatchers.IO) {
                val url = capability.baseUrl + "marks"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                try {
                    val json = NetClient.executeAndGetJsonAsync(req)
                    val jsonList = json.getJSONArray("marks")
                        ?: throw IllegalStateException("Server Response Null List.")
                    jsonList.toIterable().map { j -> MarkInfo(j as JSONObject) }.toList()
                } catch (e: Throwable) {
                    UtLogger.stackTrace(e)
                    null
                }
            }
            return if (list.isNullOrEmpty()) {
                emptyList
            } else {
                MarkList(list)
            }
        }
    }
}

fun Binder.bindMarkList(view: MaterialButtonToggleGroup, data:MutableStateFlow<List<Int>>, markList: MarkList): Binder {
    val owner = requireOwner
    return add(MaterialToggleButtonGroupBinding.create(owner, view, data.asMutableLiveData(owner), markList.idResolver))
}
fun Binder.bindMarkListRadio(view: MaterialButtonToggleGroup, data:MutableStateFlow<Int>, markList: MarkList): Binder {
    val owner = requireOwner
    return add(MaterialRadioButtonUnSelectableGroupBinding.create(owner, view, data.asMutableLiveData(owner), markList.idResolver))
}
