package io.github.toyota32k.boodroid.dialog


import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.multiVisibilityBinding
import io.github.toyota32k.binder.radioGroupBinding
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.*
import io.github.toyota32k.boodroid.databinding.DialogSettingsBinding
import io.github.toyota32k.boodroid.databinding.ListItemHostBinding
import io.github.toyota32k.boodroid.viewmodel.SettingViewModel
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.getViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class SettingsDialog : UtDialogEx() {
    private val viewModel by lazy { getViewModel<SettingViewModel>() }

    override fun preCreateBodyView() {
        isDialog = true
        scrollable = true
        cancellable = false
        draggable = true
        title = viewModel.version
        guardColor = GuardColor.THEME_SEE_THROUGH
        if(isPhone) {
            widthOption = WidthOption.FULL
            heightOption = HeightOption.FULL
            scrollable = true
        } else {
            widthOption = WidthOption.LIMIT(500)
            heightOption = HeightOption.AUTO_SCROLL
        }
        gravityOption = GravityOption.CENTER
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
    }

    private val binderWithCapability = Binder()
    private lateinit var controls: DialogSettingsBinding
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
//        binderWithCapability = Binder() // binder.dispose()で disposeされるので、毎回作成
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
//                .radioGroupBinding(themeSelector, viewModel.theme, ThemeSetting.idResolver)
//                .checkBinding(useDynamicColor, viewModel.useDynamicColor)
//                .radioGroupBinding(colorVariationSelector, viewModel.colorVariation, ColorVariation.idResolver)
                .textBinding(categoryButton, viewModel.category)
                .visibilityBinding(emptyListMessage, viewModel.hostCount.map { it==0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(ratingSelector,ratingLabel), combine(viewModel.sourceType,viewModel.capability) { st,cap-> st == SourceType.DB && cap?.hasRating == true }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(markSelector,markLabel), combine(viewModel.sourceType, viewModel.capability) { st,cap->st == SourceType.DB && cap?.hasMark == true }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(categoryButton,categoryLabel), combine(viewModel.sourceType, viewModel.capability) {st,cap-> st == SourceType.DB && cap?.hasCategory == true}, hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .checkBinding(showTitleCheckbox, viewModel.showTitleOnScreen)
                .bindCommand(viewModel.commandAddToList, addToListButton)
                .bindCommand(viewModel.commandCategory, categoryButton, callback=this@SettingsDialog::selectCategory)
//                .multiEnableBinding(arrayOf(colorVariationSelector, chkColorPink, chkColorBlue, chkColorGreen, chkColorPurple), viewModel.useDynamicColor, BoolConvert.Inverse)
                .enableBinding(rightButton, viewModel.capability.map { it!=null }, alphaOnDisabled = 0.4f)
                .recyclerViewBindingEx(hostList) {
                    options(
                        list = viewModel.hostList,
                        inflater = ListItemHostBinding::inflate,
                        bindView = { itemControls, itemBinder, view, host->
                            itemControls.nameText.text = if(host.name.isBlank()) "no name" else host.name
                            itemControls.addressText.text = host.address
                            itemBinder.reset()
                            itemBinder
                                .owner(owner)
                                .bindCommand( LiteCommand(viewModel::onActiveHostSelected), itemControls.itemContainer, host)
                                .bindCommand( LiteCommand(viewModel::editHost), itemControls.editButton, host)
                                .bindCommand( LiteCommand(viewModel::removeHost), itemControls.delButton, host)
                                .visibilityBinding(itemControls.checkMark, viewModel.activeHost.map { it == host }, hiddenMode = VisibilityBinding.HiddenMode.HideByInvisible)
                        }
                    )
                }
                .bindCommand(viewModel.commandComplete) {
                    complete(if(viewModel.result) IUtDialog.Status.POSITIVE else IUtDialog.Status.NEGATIVE)
                }
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