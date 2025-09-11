package io.github.toyota32k.boodroid.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.ListPopupWindow
import io.github.toyota32k.binder.ILabelResolverCreator
import io.github.toyota32k.binder.command.IUnitCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PopupMenu(@param:LayoutRes val itemLayoutId:Int = androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, val dropdownWidth:Int?=null) {
    /**
     * ListPopupWindow の ポップアップメニューの幅に、WRAP_CONTENT が指定されると anchorView の幅になってしまう。
     * それが適切な場合もあるが、アイコンボタンをAnchorにすると、幅が小さくなって使えない。
     * これを回避するため、自力で幅を計算できるようにしておく。
     * PopupMenuの第２引数（dropdownWidth）に nullを渡すと発動する。
     */
    fun measureContentWidth(context: Context, adapter: ListAdapter): Int {
        var maxWidth = 0
        val measure = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        for (i in 0 until adapter.count) {
            val view = adapter.getView(i, null, null)
            view.measure(measure, measure)
            maxWidth = maxOf(maxWidth, view.measuredWidth)
        }
        return maxWidth + (context.resources.displayMetrics.density * 16).toInt()
    }
    suspend fun showMenu(anchorView: View, itemList:List<String>):Int {
        return suspendCoroutine { cont ->
            val context = anchorView.context
            val adapter = ArrayAdapter(context, itemLayoutId, itemList)
            ListPopupWindow(context).apply {
                var result = -1
                setAdapter(adapter)
                this.anchorView = anchorView
                width = dropdownWidth ?: measureContentWidth(context, adapter)
                setOnItemClickListener { _, _, position, _ ->
                    result = position
                    dismiss()
                }
                setOnDismissListener {
                    cont.resume(result)
                }
                show()
            }
        }
    }
}

class PopupMenuEx<T>(
    val itemList:List<T>, @param:LayoutRes
    val itemLayoutId:Int = androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
    resolver:(ILabelResolverCreator<T>.()->Unit)?=null,
    ) : ILabelResolverCreator<T> {
    private var toLabel = { it: T -> it.toString() }
    private var toItem = { it: String -> itemList.firstOrNull { v -> toLabel(v) == it } ?: itemList.first() }
    override fun toLabel(fn: (T) -> String) {
        toLabel = fn
    }
    override fun toItem(fn: (String) -> T) {
        toItem = fn
    }
    init {
        resolver?.invoke(this)
    }

    suspend fun showMenu(anchorView: View, dropdownWidth:Int?=null):T? {
        val sel = PopupMenu(itemLayoutId, dropdownWidth).showMenu(anchorView, itemList.map { toLabel(it) })
        if(sel>=0) {
            return itemList[sel]
        } else {
            return null
        }
    }
}

data class MenuCommand(val label:String, val command:IUnitCommand)

class PopupCommandMenu(
    @param:LayoutRes val itemLayoutId:Int = androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
    ) {
    private val commands = mutableListOf<MenuCommand>()
    operator fun plus(cmd: MenuCommand): PopupCommandMenu {
        commands.add(cmd)
        return this
    }
    fun add(cmd:MenuCommand) : PopupCommandMenu {
        commands.add(cmd)
        return this
    }
    fun addIf(condition:Boolean, cmd:MenuCommand) : PopupCommandMenu {
        if(condition) commands.add(cmd)
        return this
    }

    fun showMenu(anchorView: View, dropdownWidth: Int?=null) {
        CoroutineScope(Dispatchers.Main).launch {
            PopupMenuEx(commands, itemLayoutId) {
                toLabel { it.label }
            }.showMenu(anchorView, dropdownWidth)?.command?.invoke()
        }
    }
}