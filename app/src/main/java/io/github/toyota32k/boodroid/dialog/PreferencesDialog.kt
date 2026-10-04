package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.editIntBinding
import io.github.toyota32k.binder.exposedDropdownMenuBinding
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.common.PackageUtil
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.databinding.DialogPreferencesBinding
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.application
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.lib.themes.BuiltInThemeList
import io.github.toyota32k.lib.themes.ContrastLevel
import io.github.toyota32k.lib.themes.NightMode
import io.github.toyota32k.lib.themes.ThemeDelegate
import io.github.toyota32k.lib.themes.ThemeDelegate.ApplyMode
import kotlinx.coroutines.flow.MutableStateFlow

class PreferencesDialog : UtDialogEx() {
    class PreferencesViewModel : UtDialogViewModel() {
        val dayNightMode = MutableStateFlow(AppViewModel.instance.settings.nightMode)
        val themeData = MutableStateFlow(AppViewModel.instance.settings.themeData)
        val contrastLevel = MutableStateFlow(AppViewModel.instance.settings.contrastLevel)
        val showTitleOnScreen = MutableStateFlow(AppViewModel.instance.settings.showTitleOnScreen)
        val loopPlayback = MutableStateFlow(AppViewModel.instance.settings.loopPlayback)
        val slideInterval = MutableStateFlow(AppViewModel.instance.settings.slideInterval)

        fun save() {
            Settings(AppViewModel.instance.settings,
                nightMode = dayNightMode.value,
                themeData = themeData.value,
                contrastLevel = contrastLevel.value,
                showTitleOnScreen = showTitleOnScreen.value,
                slideInterval = slideInterval.value)
            .save(application)
        }
    }
    private val viewModel by lazy { getViewModel<PreferencesViewModel>() }
    private lateinit var controls: DialogPreferencesBinding

    override fun preCreateBodyView() {
        val context = requireContext()
        title = "${PackageUtil.appName(context)} v${PackageUtil.getVersion(context)}"
        heightOption = HeightOption.AUTO_SCROLL
        widthOption = WidthOption.LIMIT(400)
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        gravityOption = GravityOption.CENTER
        draggable = true
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogPreferencesBinding.inflate(inflater.layoutInflater)
        binder
            .exposedDropdownMenuBinding(controls.dayNightDropdown, viewModel.dayNightMode, NightMode.entries)
            .exposedDropdownMenuBinding(controls.colorContrastDropdown, viewModel.contrastLevel, ContrastLevel.entries)
            .exposedDropdownMenuBinding(controls.themeDropdown, viewModel.themeData, BuiltInThemeList.themes) { toLabel { it.label } }
            .checkBinding(controls.showTitleCheckbox, viewModel.showTitleOnScreen)
            .editIntBinding(controls.slideIntervalInput, viewModel.slideInterval)
        return controls.root
    }

    override fun onPositive() {
        viewModel.save()
        super.onPositive()
    }

    companion object {
        fun show() {
            UtImmortalTask.launchTask(this::class.java.name) {
                createViewModel<PreferencesViewModel>()
                if (showDialog(taskName) { PreferencesDialog() }.status.ok ) {
                    withOwner {
                        val activity = it.asActivity() as? MainActivity ?: return@withOwner
                        val settings = AppViewModel.instance.settings
                        ThemeDelegate.defaultDelegate.applyTheme(activity, settings.themeData, settings.contrastLevel, settings.nightMode, ApplyMode.RECREATE)
                    }
                }
            }
        }
    }

}