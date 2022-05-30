@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.github.toyota32k.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle

open class UtMessageBox : UtDialogBase(), DialogInterface.OnClickListener {
    var title:String? by bundle.stringNullable
    var message:String? by bundle.stringNullable
    var okLabel:String? by bundle.stringNullable
    var cancelLabel:String? by bundle.stringNullable
    var otherLabel:String? by bundle.stringNullable

    protected open fun getAlertBuilder():AlertDialog.Builder {
        val builder = AlertDialog.Builder(requireContext())
        title?.let { builder.setTitle(it) }
        message?.let { builder.setMessage(it) }
        okLabel?.let { builder.setPositiveButton(it, this) }
        cancelLabel?.let { builder.setNegativeButton(it, this) }
        otherLabel?.let { builder.setNeutralButton(it, this) }
        return builder
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return getAlertBuilder().create()
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        when(which) {
            DialogInterface.BUTTON_POSITIVE -> { complete(IUtDialog.Status.POSITIVE) }
            DialogInterface.BUTTON_NEUTRAL  -> { complete(IUtDialog.Status.NEUTRAL) }
            DialogInterface.BUTTON_NEGATIVE -> { complete(IUtDialog.Status.NEGATIVE) }
            else -> {}
        }
    }

    companion object {
        /**
         * アクティビティから呼び出すOKボタンだけの確認メッセージ
         */
        fun createForConfirm(title:String?, message:String?, okLabel:String= UtStandardString.OK.text) : UtMessageBox {
            return UtMessageBox().apply {
                this.title = title
                this.message = message
                this.okLabel = okLabel
            }
        }

        fun createForOkCancel(title:String?, message:String?, okLabel:String= UtStandardString.OK.text, cancelLabel:String= UtStandardString.CANCEL.text) : UtMessageBox {
            return UtMessageBox().apply {
                this.title = title
                this.message = message
                this.okLabel = okLabel
                this.cancelLabel = cancelLabel
            }
        }

        fun createForYesNo(title:String?, message:String?, yesLabel:String= UtStandardString.YES.text, noLabel:String= UtStandardString.NO.text) : UtMessageBox {
            return createForOkCancel(title,message,yesLabel,noLabel)
        }
    }
}
