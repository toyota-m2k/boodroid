package io.github.toyota32k.dialog

import android.content.Context
import androidx.annotation.StringRes
import java.lang.ref.WeakReference

interface IUtStringTable {
    @StringRes
    fun resId(type: UtStandardString): Int

    @StringRes
    operator fun get(str:UtStandardString):Int {
        return resId(str)
    }
}

enum class UtStandardString(@StringRes private val resId:Int) {
    OK(R.string.ut_dialog_ok),
    CANCEL(R.string.ut_dialog_cancel),
    CLOSE(R.string.ut_dialog_close),
    DONE(R.string.ut_dialog_done),
    YES(R.string.ut_dialog_yes),
    NO(R.string.ut_dialog_no),
    BACK(R.string.ut_dialog_back),
    NONE(0);

    val text : String
        get() = getText(this)

    val id : Int @StringRes
        get() = getId(this)

    companion object {
        private var context:WeakReference<Context>? = null
        private var table:IUtStringTable? = null
        @JvmStatic
        @JvmOverloads
        fun setContext(context:Context, table:IUtStringTable?=null) {
            this.context = WeakReference(context)
            this.table = table
        }
        @StringRes
        private fun getId(type:UtStandardString) : Int {
            return table?.get(type) ?: type.resId
        }
        private fun getText(type:UtStandardString) : String {
            return context?.get()?.getString(getId(type)) ?: ""
        }
    }
}
