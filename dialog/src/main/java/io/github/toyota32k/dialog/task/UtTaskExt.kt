package io.github.toyota32k.dialog.task

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtMessageBox
import io.github.toyota32k.dialog.UtStandardString
import java.lang.IllegalStateException

/**
 * ImmortalTask内からメッセージボックスを表示するための拡張関数
 */
suspend fun UtImmortalTaskBase.showConfirmMessageBox(title:String?, message:String?, okLabel:String= UtStandardString.OK.text) {
    showDialog("internalConfirm") { UtMessageBox.createForConfirm(title,message,okLabel) }
}

suspend fun UtImmortalTaskBase.showOkCancelMessageBox(title:String?, message:String?, okLabel:String= UtStandardString.OK.text, cancelLabel:String= UtStandardString.CANCEL.text) : Boolean {
    return showDialog("internalOkCancel") { UtMessageBox.createForOkCancel(title,message,okLabel, cancelLabel) }.status.ok
}

suspend fun UtImmortalTaskBase.showYesNoMessageBox(title:String?, message:String?, yesLabel:String= UtStandardString.YES.text, noLabel:String= UtStandardString.NO.text) : Boolean {
    return showDialog("internalYesNo") { UtMessageBox.createForYesNo(title,message,yesLabel,noLabel) }.status.yes
}

/**
 * ImmortalTask内からアクティビティを取得するための拡張関数
 */
suspend fun UtImmortalTaskBase.getActivity():FragmentActivity? {
    return UtImmortalTaskManager.mortalInstanceSource.getOwner().asActivity()
}

val UtDialog.immortalTask:IUtImmortalTask get() {
    val taskName = immortalTaskName ?: throw IllegalStateException("no task name")
    return UtImmortalTaskManager.taskOf(taskName)?.task ?: throw IllegalStateException("no such task: $taskName")
}

val UtDialog.immortalTaskContext: IUtImmortalTaskContext get() {
    val taskName = immortalTaskName ?: throw IllegalStateException("no task name")
    return UtImmortalTaskManager.taskOf(taskName)?.task?.immortalTaskContext ?: throw IllegalStateException("no such task: $taskName")
}

inline fun <reified VM> UtImmortalTaskBase.createViewModel(application: Application): VM where VM : AndroidViewModel, VM:IUtImmortalTaskMutableContextSource {
    return ViewModelProvider(this.immortalTaskContext, ViewModelProvider.AndroidViewModelFactory(application))[VM::class.java].also { vm->
        vm.immortalTaskContext = this.immortalTaskContext
    }
}

inline fun <reified VM> UtImmortalTaskBase.createViewModel(): VM where VM : ViewModel, VM:IUtImmortalTaskMutableContextSource {
    return ViewModelProvider(this.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[VM::class.java].also { vm->
        vm.immortalTaskContext = this.immortalTaskContext
    }
}

inline fun <reified VM:ViewModel> IUtImmortalTaskContext.getViewModel():VM  {
    return ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[VM::class.java]
}

inline fun <reified VM:AndroidViewModel> IUtImmortalTaskContext.getViewModel(application: Application):VM  {
    return ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))[VM::class.java]
}
