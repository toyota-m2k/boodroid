package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.boodroid.databinding.DialogWallpaperBinding
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class WallpaperDialog : UtDialogEx() {
    class WallpaperViewModel : UtDialogViewModel() {
        val saveFile = MutableStateFlow<Boolean>(false)
        val useCropHint = MutableStateFlow(false)
        val lockScreen = MutableStateFlow(false)
        val homeScreen = MutableStateFlow(false)
        val isReady = combine(lockScreen, homeScreen, saveFile) { lock, home, save -> lock || home || save }
    }
    private lateinit var controls: DialogWallpaperBinding
    private val viewModel: WallpaperViewModel by lazy { getViewModel() }

    override fun preCreateBodyView() {
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
        title = "Wallpaper"
        gravityOption = GravityOption.CENTER
        widthOption = WidthOption.COMPACT
        heightOption = HeightOption.COMPACT
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogWallpaperBinding.inflate(inflater.layoutInflater)
        binder
            .owner(this)
            .checkBinding(controls.lockScreen, viewModel.lockScreen)
            .checkBinding(controls.homeScreen, viewModel.homeScreen)
            .checkBinding(controls.useCropHint, viewModel.useCropHint)
            .checkBinding(controls.saveFile, viewModel.saveFile)
            .multiEnableBinding(arrayOf(controls.lockScreen, controls.homeScreen, controls.useCropHint), viewModel.saveFile, BoolConvert.Inverse)
            .dialogRightButtonEnable(viewModel.isReady)
        return controls.root
    }
}