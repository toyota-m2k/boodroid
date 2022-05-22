package io.github.toyota32k.boodroid.dialog

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.bindit.*
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.*
import io.github.toyota32k.boodroid.viewmodel.SettingViewModel
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.listChildren

class SettingsDialog : UtDialog(isDialog=true) {
    lateinit var viewModel: SettingViewModel
        private set
    private val binder = Binder()

    override fun preCreateBodyView() {
        super.preCreateBodyView()
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

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val owner = requireActivity()
        return inflater.inflate(R.layout.dialog_settings).also { root->
            val sourceTypeSelector: RadioGroup = root.findViewById(R.id.source_type_selector)
            val ratingSelector: MaterialButtonToggleGroup = root.findViewById(R.id.rating_selector)
            val themeSelector: RadioGroup = root.findViewById(R.id.theme_selector)
            val colorVariationSelector:RadioGroup = root.findViewById(R.id.color_variation_selector)
            val markSelector: MaterialButtonToggleGroup = root.findViewById(R.id.mark_selector)
//            val hostAddrEdit: EditText = root.findViewById(R.id.host_addr_edit)
            val addToListButton: View = root.findViewById(R.id.add_to_list_button)
            val hostList: RecyclerView = root.findViewById(R.id.host_list)
            val categoryButton: Button = root.findViewById(R.id.category_button)
            val emptyListMessage: TextView = root.findViewById(R.id.empty_list_message)
//            hostList.layoutManager = LinearLayoutManager(context)

//            logger.debug("DLG: sourceType=${viewModel.sourceType.value}")

            binder.register(
                RadioGroupBinding.create(owner, sourceTypeSelector, viewModel.sourceType, SourceType.idResolver, BindingMode.TwoWay),
                MaterialRadioButtonGroupBinding.create(owner, ratingSelector, viewModel.rating, Rating.idResolver, BindingMode.TwoWay),
                RadioGroupBinding.create(owner, themeSelector, viewModel.theme, ThemeSetting.idResolver, BindingMode.TwoWay),
                RadioGroupBinding.create(owner, colorVariationSelector, viewModel.colorVariation, ColorVariation.idResolver, BindingMode.TwoWay),
                MaterialToggleButtonGroupBinding.create(owner, markSelector, viewModel.markList, Mark.idResolver, BindingMode.TwoWay),
//                EditTextBinding.create(owner, hostAddrEdit, viewModel.editingHost),
                TextBinding.create(owner, categoryButton, viewModel.categoryList.currentLabel.map { it ?: "All" }),
                VisibilityBinding.create(owner, emptyListMessage, viewModel.hostCount.map { it==0 }, hiddenMode = VisibilityBinding.HiddenMode.HideByGone),
                MultiEnableBinding.create(owner, views = (ratingSelector.listChildren<MaterialButton>() + markSelector.listChildren<MaterialButton>() + categoryButton).toList().toTypedArray(), data=viewModel.sourceType.map { it==SourceType.DB }, alphaOnDisabled = 0.5f),
                viewModel.commandAddToList.connectAndBind(owner, addToListButton) { viewModel.addHost() },
//                viewModel.commandAddToList.connectViewEx(hostAddrEdit),
                viewModel.commandCategory.connectAndBind(owner, categoryButton, this::selectCategory),

                RecyclerViewBinding.create(owner, hostList, viewModel.hostList, R.layout.list_item_host) { binder, view, address ->
                    val textView = view.findViewById<TextView>(R.id.address_text)
                    textView.text = address
                    binder.register(
                        Command().connectAndBind(owner, view.findViewById(R.id.item_container)) {  viewModel.activeHost.value = address },
                        Command().connectAndBind(owner, view.findViewById(R.id.del_button)) {  viewModel.removeHost(address) },
                        VisibilityBinding.create(owner, view.findViewById(R.id.check_mark), viewModel.activeHost.map { it==address }, BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible),
                    )
                },

                viewModel.activeHost.disposableObserve(owner) { activatedHost->
                    if(!activatedHost.isNullOrEmpty()) {
                        val editing = viewModel.editingHost.value
                        if(editing.isNullOrBlank() || viewModel.hostList.contains(editing)) {
                            viewModel.editingHost.value = activatedHost
                        }
                    }
                },

                viewModel.theme.disposableObserve(owner) { theme->
                    val mode = theme?.mode ?: return@disposableObserve
                    if(AppCompatDelegate.getDefaultNightMode()!=mode) {
                        AppCompatDelegate.setDefaultNightMode(mode)
                    }
                },
                viewModel.commandComplete.bind(owner) {
                    complete(if(viewModel.result) IUtDialog.Status.POSITIVE else IUtDialog.Status.NEGATIVE)
                }
            )
//            logger.debug("DLG: sourceType=${viewModel.sourceType.value}, ${SourceType.idResolver.id2value(sourceTypeSelector.checkedRadioButtonId) }")
        }
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        logger.debug("DLG: sourceType=${viewModel.sourceType.value}, ${SourceType.idResolver.id2value(view.findViewById<RadioGroup>(R.id.source_type_selector).checkedRadioButtonId) }")
//
//    }

    private var listPopup: ListPopupWindow? = null
    private fun selectCategory(view: View?) {
        val adapter = ArrayAdapter(context, R.layout.list_item_category,viewModel.categoryList.list.value?.map {it.label}?.toTypedArray() ?: arrayOf("All"))
        listPopup = ListPopupWindow(context)
            .apply {
                setAdapter(adapter)
                anchorView = view
                setOnItemClickListener { _, _, position, _ ->
                    val cat = adapter.getItem(position)
                    if(null!=cat) {
                        viewModel.categoryList.category = cat
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