package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.editTextBinding
import io.github.toyota32k.boodroid.data.HostAddressEntity
import io.github.toyota32k.boodroid.databinding.DialogHostAddressBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class HostAddressDialog : UtDialogEx() {
    class HostAddressDialogViewModel: UtDialogViewModel() {
        val name = MutableStateFlow("")
        val address = MutableStateFlow("")
    }

    companion object {
        suspend fun getHostAddress(initialHost: HostAddressEntity?): HostAddressEntity? {
            return UtImmortalTask.awaitTaskResult(HostAddressDialog::class.java.name) {
                val vm = createViewModel<HostAddressDialogViewModel> {
                    if(initialHost!=null) {
                        name.value = initialHost.name
                        address.value = initialHost.address
                    }
                }
                if(showDialog(taskName) { HostAddressDialog() }.status.ok) {
                    HostAddressEntity(vm.name.value, vm.address.value)
                } else null
            }
        }
    }

    private val viewModel by lazy { getViewModel<HostAddressDialogViewModel>() }
    private lateinit var controls: DialogHostAddressBinding

    override fun preCreateBodyView() {
        parentVisibilityOption = ParentVisibilityOption.NONE
        draggable = true
        guardColor = GuardColor.DIM
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return DialogHostAddressBinding.inflate(inflater.layoutInflater).apply {
            controls = this
            binder
            .editTextBinding(hostName, viewModel.name)
            .editTextBinding(hostAddress, viewModel.address)
            .dialogRightButtonEnable(viewModel.address.map { it.isNotBlank() })
        }.root
    }

}