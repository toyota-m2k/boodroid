package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.map
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.bindit.*
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.Mark
import io.github.toyota32k.boodroid.data.Rating
import io.github.toyota32k.boodroid.data.SourceType
import io.github.toyota32k.boodroid.viewmodel.SettingViewModel
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.utils.disposableObserve

class SettingsDialog : UtDialog() {
    private lateinit var viewModel: SettingViewModel
    private val binder = Binder()

    init {
        scrollable = true
        cancellable = false
        gravityOption = GravityOption.CENTER
        draggable = true
        setLimitWidth(500)
        heightOption = HeightOption.AUTO_SCROLL
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val owner = requireActivity()
        viewModel = SettingViewModel.instanceFor(owner).prepare(owner)
        return inflater.inflate(R.layout.dialog_settings).also { root->
            val sourceTypeSelector: RadioGroup = root.findViewById(R.id.sourcr_type_selector)
            val ratingSelector: MaterialButtonToggleGroup = root.findViewById(R.id.rating_selector)
            val markSelector: MaterialButtonToggleGroup = root.findViewById(R.id.mark_selector)
            val hostAddrEdit: EditText = root.findViewById(R.id.host_addr_edit)
            val addToListButton: View = root.findViewById(R.id.add_to_list_button)
            val hostList: RecyclerView = root.findViewById(R.id.host_list)
            val categoryButton: Button = root.findViewById(R.id.category_button)
            hostList.layoutManager = LinearLayoutManager(context)

            binder.register(
                RadioGroupBinding.create(owner, sourceTypeSelector, viewModel.sourceType, SourceType.idResolver, BindingMode.TwoWay),
                MaterialRadioButtonGroupBinding.create(owner, ratingSelector, viewModel.rating, Rating.idResolver, BindingMode.TwoWay),
                MaterialToggleButtonGroupBinding.create(owner, markSelector, viewModel.markList, Mark.idResolver, BindingMode.TwoWay),
                EditTextBinding.create(owner, hostAddrEdit, viewModel.editingHost),
                TextBinding.create(owner, categoryButton, viewModel.categoryList.currentLabel.map { it ?: "All" }),
                viewModel.commandAddToList.connectAndBind(owner, addToListButton) { viewModel.addHost() },
                viewModel.commandAddToList.connectViewEx(hostAddrEdit),
                viewModel.commandCategory.connectAndBind(owner, categoryButton, this::selectCategory),

                RecycleViewBinding.create(owner, hostList, viewModel.hostList.value!!, R.layout.list_item_host) { binder, view, address ->
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
                        if(editing.isNullOrBlank()|| viewModel.hostList.value?.contains(editing) == true) {
                            viewModel.editingHost.value = activatedHost
                        }
                    }
                }
            )
        }
    }

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
        viewModel.save(context)
        super.onPositive()
    }

}