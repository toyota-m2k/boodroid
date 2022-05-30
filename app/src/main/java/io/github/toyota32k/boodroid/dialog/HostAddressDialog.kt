package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.EditTextBinding
import io.github.toyota32k.bindit.EnableBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.UtImmortalTaskContextSource
import io.github.toyota32k.boodroid.data.HostAddressEntity
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.IUtImmortalTask
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtImmortalViewModelHelper
import io.github.toyota32k.utils.asMutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class HostAddressDialog : UtDialog(isDialog=true) {
    class HostAddressDialogViewModel: ViewModel(), IUtImmortalTaskMutableContextSource by UtImmortalTaskContextSource() {
        val name = MutableStateFlow("")
        val address = MutableStateFlow("")

        companion object {
            /**
             * タスク開始時の初期化用
             */
            fun createBy(task: IUtImmortalTask, initialHost:HostAddressEntity?): HostAddressDialogViewModel
                    = UtImmortalViewModelHelper.createBy(HostAddressDialogViewModel::class.java, task).apply {
                        if(initialHost!=null) {
                            name.value = initialHost.name
                            address.value = initialHost.address
                        }
                    }
            /**
             * ダイアログから取得する用
             */
            fun instanceFor(dialog: IUtDialog): HostAddressDialogViewModel
                    = UtImmortalViewModelHelper.instanceFor(HostAddressDialogViewModel::class.java, dialog)

        }
    }

    companion object {
        suspend fun getHostAddress(initialHost: HostAddressEntity?): HostAddressEntity? {
            return UtImmortalSimpleTask.executeAsync(HostAddressDialog::class.java.name) {
                val vm = HostAddressDialogViewModel.createBy(this, initialHost)
                if(showDialog(taskName) { HostAddressDialog() }.status.ok) {
                    HostAddressEntity(vm.name.value, vm.address.value)
                } else null
            }
        }
    }

    private var binder = Binder()
    private val viewModel by lazy { HostAddressDialogViewModel.instanceFor(this) }

    override fun preCreateBodyView() {
        super.preCreateBodyView()
        parentVisibilityOption = ParentVisibilityOption.NONE
        draggable = true
//        animationEffect = false
        guardColor = GuardColor.DIM.color
        if(isPhone) {
            widthOption = WidthOption.FULL
        } else {
            heightOption = HeightOption.COMPACT
        }
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.OK)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return inflater.inflate(R.layout.dialog_host_address).also { dlg->
            binder.register(
                EditTextBinding.create(this, dlg.findViewById(R.id.host_name), viewModel.name.asMutableLiveData(this)),
                EditTextBinding.create(this, dlg.findViewById(R.id.host_address), viewModel.address.asMutableLiveData(this)),
                EnableBinding.create(this, rightButton, viewModel.address.map { it.isNotBlank() }.asLiveData()),
            )
        }
    }

    override fun onPositive() {
        super.onPositive()
    }

}