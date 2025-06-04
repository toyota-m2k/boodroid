package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.boodroid.databinding.DialogSaveImageBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class SaveImageDialog : UtDialogEx() {
    class SaveImageViewModel : UtDialogViewModel() {
        val saveAsFile = MutableStateFlow(false)
        val setWallpaper = MutableStateFlow(false)
        val isReady = combine(saveAsFile, setWallpaper) { s, w -> s || w }
    }

    lateinit var controls: DialogSaveImageBinding
    private val viewModel: SaveImageViewModel by lazy { getViewModel<SaveImageViewModel>() }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        title = "Snapshot"
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.COMPACT
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
            .checkBinding(controls.setWallpaper, viewModel.setWallpaper)
            .dialogRightButtonEnable(viewModel.isReady)
        return controls.root
    }
}