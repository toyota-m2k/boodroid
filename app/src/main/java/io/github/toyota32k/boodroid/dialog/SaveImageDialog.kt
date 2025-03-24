package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.boodroid.databinding.DialogSaveImageBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class SaveImageDialog : UtDialogEx() {
    class SaveImageViewModel : UtDialogViewModel() {
        val saveAsFile = MutableStateFlow(false)
        val lockScreen = MutableStateFlow(false)
        val homeScreen = MutableStateFlow(false)
    }

    lateinit var controls: DialogSaveImageBinding
    private val viewModel: SaveImageViewModel by lazy { getViewModel<SaveImageViewModel>() }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        title = "Snapshot"
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.LIMIT(400)
        heightOption = HeightOption.COMPACT
    }

    override fun createBodyView(
        savedInstanceState: Bundle?,
        inflater: IViewInflater
    ): View {
        controls = DialogSaveImageBinding.inflate(inflater.layoutInflater)
        binder
            .owner(this)
            .checkBinding(controls.saveAsFile, viewModel.saveAsFile)
            .checkBinding(controls.lockScreen, viewModel.lockScreen)
            .checkBinding(controls.homeScreen, viewModel.homeScreen)
            .enableBinding(
                rightButton,
                combine(
                    viewModel.saveAsFile,
                    viewModel.lockScreen,
                    viewModel.homeScreen
                ) { s, l, h -> s || l || h }
            )
        return controls.root
    }
}