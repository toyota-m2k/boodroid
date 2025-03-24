package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.auth.Authentication
import io.github.toyota32k.boodroid.databinding.DialogPasswordBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PasswordDialog : UtDialogEx() {
    class PasswordViewModel : UtDialogViewModel() {
        lateinit var authentication: Authentication
        val password = MutableStateFlow("")
        val message = MutableStateFlow("")
        val ready : Flow<Boolean> = password.map { it.isNotEmpty() }
        val busy = MutableStateFlow(false)

        val complete = ReliableCommand<Boolean>()
        val commandCheck = LiteUnitCommand {
            busy.value = true
            viewModelScope.launch {
                try {
                    if(authentication.authWithPassword(password.value)) {
                        complete.invoke(true)
                    }
                    message.value = "Try again ..."
                } catch(e:Throwable) {
                    message.value = "Fatal Error."
                } finally {
                    busy.value = false
                }
            }
        }

//        companion object {
//            fun create(taskName:String, authentication: Authentication): PasswordViewModel {
//                return UtImmortalTaskManager.taskOf(taskName)?.task?.createViewModel<PasswordViewModel>()?.apply { this.authentication = authentication } ?: throw IllegalStateException("no task")
//            }
//
//            fun instanceFor(dlg: PasswordDialog): PasswordViewModel {
//                return ViewModelProvider(dlg.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[PasswordViewModel::class.java]
//            }
//        }
    }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        title = requireActivity().getString(R.string.password)
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        enableFocusManagement()
            .setInitialFocus(R.id.password)
            .register(R.id.password)
    }

    lateinit var controls: DialogPasswordBinding
    private val viewModel: PasswordViewModel by lazy { getViewModel<PasswordViewModel>() }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogPasswordBinding.inflate(inflater.layoutInflater)
        binder
            .owner(this)
            .editTextBinding(controls.password, viewModel.password)
            .textBinding(controls.message, viewModel.message)
            .visibilityBinding(bodyGuardView, viewModel.busy, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(progressRingOnTitleBar, viewModel.busy, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .visibilityBinding(controls.message, viewModel.message.map{ it.isNotEmpty() },BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone)
            .enableBinding(rightButton, viewModel.ready.combine(viewModel.busy){ready,busy->ready&&!busy})
            .enableBinding(leftButton, viewModel.busy, BoolConvert.Inverse)
            .bindCommand(viewModel.complete,  leftButton, false)    // cancel
            .bindCommand(viewModel.commandCheck, rightButton)   // done
            .bindCommand(viewModel.commandCheck, controls.password)
            .bindCommand(viewModel.complete) { if(it) onPositive() else onNegative() }
        return controls.root
    }
}