package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.exposedDropdownMenuBinding
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.ThemeSelector
import io.github.toyota32k.boodroid.databinding.DialogColorVariationBinding
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.application
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ColorVariationDialog : UtDialogEx() {
    class ColorVariationViewModel : UtDialogViewModel() {
        val dayNightMode = MutableStateFlow(AppViewModel.instance.settings.nightMode)
        val themeInfo = MutableStateFlow(AppViewModel.instance.settings.themeInfo)
        val contrastLevel = MutableStateFlow(AppViewModel.instance.settings.contrastLevel)

        fun save() {
            Settings(AppViewModel.instance.settings,
                nightMode = dayNightMode.value,
                themeInfo = themeInfo.value,
                contrastLevel = contrastLevel.value).save(application)
        }
    }
    private val viewModel by lazy { getViewModel<ColorVariationViewModel>() }
    private lateinit var controls: DialogColorVariationBinding

    override fun preCreateBodyView() {
        title = getString(R.string.color_variation)
        heightOption = HeightOption.COMPACT
        widthOption = WidthOption.LIMIT(400)
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        gravityOption = GravityOption.CENTER
        draggable = true
    }

    override fun createBodyView(savedInstanceState: Bundle?,inflater: IViewInflater): View {
        controls = DialogColorVariationBinding.inflate(inflater.layoutInflater)
        binder
            .exposedDropdownMenuBinding(controls.dayNightDropdown, viewModel.dayNightMode, ThemeSelector.NightMode.entries)
            .exposedDropdownMenuBinding(controls.colorContrastDropdown, viewModel.contrastLevel, ThemeSelector.ContrastLevel.entries)
            .exposedDropdownMenuBinding(controls.themeDropdown, viewModel.themeInfo, Settings.ThemeList.themes) { toLabel { it.label } }
        return controls.root
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }

    companion object {
        fun show() {
            UtImmortalTask.launchTask(this::class.java.name) {
                createViewModel<ColorVariationViewModel>()
                if (showDialog(taskName) { ColorVariationDialog() }.status.ok ) {
                    withOwner {
                        val activity = it.asActivity() as? MainActivity ?: return@withOwner
                        val settings = AppViewModel.instance.settings
                        if (ThemeSelector.defaultInstance.isThemeChanged(settings.themeInfo,settings.contrastLevel)) {
                            activity.restartActivityToUpdateTheme()
                            activity.finish()
                        } else {
                            ThemeSelector.defaultInstance.applyNightMode(settings.nightMode)
                        }
                    }
                }
            }
        }
    }
}