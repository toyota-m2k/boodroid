package io.github.toyota32k.boodroid.dialog

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.CheckBinding
import io.github.toyota32k.binder.MaterialRadioButtonGroupBinding
import io.github.toyota32k.binder.MaterialToggleButtonGroupBinding
import io.github.toyota32k.binder.MultiEnableBinding
import io.github.toyota32k.binder.RadioGroupBinding
import io.github.toyota32k.binder.RecyclerViewBinding
import io.github.toyota32k.binder.TextBinding
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.materialRadioButtonGroupBinding
import io.github.toyota32k.binder.multiEnableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.radioGroupBinding
import io.github.toyota32k.binder.recyclerViewBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.*
import io.github.toyota32k.boodroid.databinding.DialogSettingsBinding
import io.github.toyota32k.boodroid.viewmodel.SettingViewModel
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.listChildren
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class SettingsDialog : UtDialog() {
    lateinit var viewModel: SettingViewModel
        private set
    private val binder = Binder()

    override fun preCreateBodyView() {
        super.preCreateBodyView()
        isDialog = true
        viewModel = SettingViewModel.instanceFor(this)
        scrollable = true
        cancellable = false
        draggable = true
        title = viewModel.version
        guardColor = Color.argb(0xD0, 0xFF, 0xFF, 0xFF)
        if(isPhone) {
            widthOption = WidthOption.FULL
            heightOption = HeightOption.FULL
            scrollable = true
        } else {
            heightOption = HeightOption.AUTO_SCROLL
            gravityOption = GravityOption.CENTER
            setLimitWidth(500)
        }
        gravityOption = GravityOption.CENTER
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
    }

    private lateinit var binderWithCapability:Binder
    private lateinit var controls: DialogSettingsBinding
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        binderWithCapability = Binder() // binder.dispose()で disposeされるので、毎回作成
        val owner = requireActivity()
        binder.owner(owner)
        binderWithCapability.owner(owner)

        controls = DialogSettingsBinding.inflate(inflater.layoutInflater).apply {
            viewModel.capability.onEach {cap->
                binderWithCapability.reset()
                if(cap!=null) {
                    binderWithCapability
                        .bindRatingList(ratingSelector, viewModel.rating, cap.ratingList)
                        .bindMarkList(markSelector, viewModel.marks, cap.markList)
                }
            }.launchIn(lifecycleScope)
            binder
                .add(binderWithCapability)
                .radioGroupBinding(sourceTypeSelector, viewModel.sourceType, SourceType.idResolver)
                .radioGroupBinding(themeSelector, viewModel.theme, ThemeSetting.idResolver)
                .checkBinding(useDynamicColor, viewModel.useDynamicColor)
                .radioGroupBinding(colorVariationSelector, viewModel.colorVariation, ColorVariation.idResolver)
                .textBinding(categoryButton, viewModel.category)
                .visibilityBinding(emptyListMessage, viewModel.hostCount.map { it==0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(ratingSelector,ratingLabel), combine(viewModel.sourceType,viewModel.capability) { st,cap-> st == SourceType.DB && cap?.hasRating == true }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(markSelector,markLabel), combine(viewModel.sourceType, viewModel.capability) { st,cap->st == SourceType.DB && cap?.hasMark == true }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(categoryButton,categoryLabel), combine(viewModel.sourceType, viewModel.capability) {st,cap-> st == SourceType.DB && cap?.hasCategory == true}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .checkBinding(showTitleCheckbox, viewModel.showTitleOnScreen)
                .bindCommand(viewModel.commandAddToList, addToListButton)
                .bindCommand(viewModel.commandCategory, categoryButton, callback=this@SettingsDialog::selectCategory)
                .multiEnableBinding(arrayOf(colorVariationSelector, chkColorPink, chkColorBlue, chkColorGreen, chkColorPurple), viewModel.useDynamicColor, BoolConvert.Inverse)
                .enableBinding(rightButton, viewModel.capability.map { it!=null }, alphaOnDisabled = 0.4f)
                .recyclerViewBinding(hostList, viewModel.hostList, R.layout.list_item_host) { binder, view, host ->
                    view.findViewById<TextView>(R.id.name_text).text = if(host.name.isBlank()) "no name" else host.name
                    view.findViewById<TextView>(R.id.address_text).text = host.address
                    binder.reset()
                    binder
                        .owner(owner)
                        .bindCommand(LiteUnitCommand { viewModel.onActiveHostSelected(host) }, view.findViewById<View>(R.id.item_container))
                        .bindCommand(LiteCommand(viewModel::editHost), view.findViewById(R.id.edit_button), host)
                        .bindCommand(LiteCommand(viewModel::removeHost), view.findViewById(R.id.del_button), host)
                        .visibilityBinding(view.findViewById(R.id.check_mark), viewModel.activeHost.map { it==host }, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
                }
                .add(
                    viewModel.theme.disposableObserve(owner) { theme->
                        val mode = theme.mode
                        if(AppCompatDelegate.getDefaultNightMode()!=mode) {
                            AppCompatDelegate.setDefaultNightMode(mode)
                        }
                    },
                    viewModel.commandComplete.bind(owner) {
                        complete(if(viewModel.result) IUtDialog.Status.POSITIVE else IUtDialog.Status.NEGATIVE)
                    }
                )

        }
        return controls.root
    }


//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        logger.debug("DLG: sourceType=${viewModel.sourceType.value}, ${SourceType.idResolver.id2value(view.findViewById<RadioGroup>(R.id.source_type_selector).checkedRadioButtonId) }")
//
//    }

    private var listPopup: ListPopupWindow? = null
    private fun selectCategory() {
        val cap = viewModel.capability.value ?: return
        if(!cap.hasCategory || cap.categoryList.size<=1) return
        val adapter = ArrayAdapter(context, R.layout.list_item_category, cap.categoryList.map {it.label})
        listPopup = ListPopupWindow(context)
            .apply {
                setAdapter(adapter)
                anchorView = controls.categoryButton
                setOnItemClickListener { _, _, position, _ ->
                    val cat = adapter.getItem(position)
                    if(null!=cat) {
                        viewModel.category.value = cat
                    }
                    listPopup?.dismiss()
                    listPopup = null
                }
                show()
            }
    }

    override fun onPositive() {
        viewModel.checkOnClosing(true)
    }

    override fun onNegative() {
        viewModel.checkOnClosing(false)
    }

}