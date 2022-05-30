package io.github.toyota32k.dialog

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

@Suppress("unused")
interface IUtDialog {
    /**
     * ダイアログの状態
     */
    enum class Status {
        UNKNOWN,
        POSITIVE,
        NEGATIVE,
        NEUTRAL;

        val finished : Boolean
            get() = this != UNKNOWN

        val negative: Boolean
            get() = this == NEGATIVE
        val cancel: Boolean
            get() = this == NEGATIVE
        val no: Boolean
            get() = this == NEGATIVE
        val positive: Boolean
            get() = this == POSITIVE
        val ok: Boolean
            get() = this == POSITIVE
        val yes: Boolean
            get() = this == POSITIVE
        val neutral: Boolean
            get() = this == NEUTRAL
    }
    val status: Status

    var immortalTaskName:String?

    var doNotResumeTask:Boolean     // タスクから表示されたダイアログなどで、同じTaskNameを共有利用してサブダイアログを表示する場合、親タスクが終了してしまわないように trueをセットする

    /**
     * ダイアログを表示する
     */
    fun show(activity: FragmentActivity, tag:String?)
//    fun show(fragment: Fragment, tag:String?)

    /**
     * ダイアログ状態をセットして閉じる
     */
    fun complete(status: Status = Status.POSITIVE)

    /**
     * ダイアログを（キャンセルして）閉じる
     */
    fun cancel()

    fun ensureArguments(): Bundle

    val asFragment: DialogFragment
}

