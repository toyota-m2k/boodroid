package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import com.google.android.material.button.MaterialButtonToggleGroup
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.MaterialRadioButtonGroupBinding
import io.github.toyota32k.binder.MaterialRadioButtonUnSelectableGroupBinding
import io.github.toyota32k.binder.TextBinding
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.Mark
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.viewmodel.RatingViewModel
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.utils.disposableObserve

class RatingDialog : UtDialog(isDialog=true) {
    private lateinit var viewModel: RatingViewModel
    private val binder = Binder()

    override fun preCreateBodyView() {
        super.preCreateBodyView()
        draggable = true
        widthOption = WidthOption.COMPACT
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.OK)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val owner = requireActivity()
        viewModel = RatingViewModel.instanceFor(this)
        return inflater.inflate(R.layout.dialog_rating).also { root->
            val nameText:TextView = root.findViewById(R.id.item_name)
            val ratingSelector: MaterialButtonToggleGroup = root.findViewById(R.id.rating_selector)
            val markSelector: MaterialButtonToggleGroup = root.findViewById(R.id.mark_selector)
            val categoryButton: Button = root.findViewById(R.id.category_button)
            val busyPanel:View = root.findViewById(R.id.busy_panel)
            binder.register(
                TextBinding.create(owner, nameText, viewModel.name),
                MaterialRadioButtonGroupBinding.create(owner, ratingSelector, viewModel.rating, io.github.toyota32k.boodroid.data.Rating.idResolver, BindingMode.TwoWay),
                MaterialRadioButtonUnSelectableGroupBinding.create(owner, markSelector, viewModel.mark, Mark.idResolver, BindingMode.TwoWay),
                TextBinding.create(owner, categoryButton, viewModel.category),
                VisibilityBinding.create(owner, busyPanel, viewModel.busy),
                Command(this::selectCategory).attachView(categoryButton),
                viewModel.hasError.disposableObserve(owner) { error->
                    if(error==true) {
                        UtImmortalSimpleTask.run("rating error") {
                            showConfirmMessageBox(null, "cannot access the video item.")
                            onNegative()
                            true
                        }
                    }
                }
            )
        }
    }

    private fun selectCategory(view:View?) {
        val adapter = ArrayAdapter(context, R.layout.list_item_category,viewModel.categoryList.list.value?.map {it.label}?.toTypedArray() ?: arrayOf("All"))
        val listPopup = ListPopupWindow(context)
        listPopup.setAdapter(adapter)
        listPopup.anchorView = view
        listPopup.setOnItemClickListener { _, _, position, _ ->
                    val cat = adapter.getItem(position)
                    if(null!=cat) {
                        viewModel.category.value = cat
                    }
                    listPopup.dismiss()
                }
        listPopup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binder.reset()
    }

    override fun onPositive() {
        super.onPositive()
        viewModel.putToServer()
    }

    companion object {
        fun show(item: VideoItem) {
            UtImmortalSimpleTask.run("rating") {
                RatingViewModel.createBy(this) { it.prepare(item, immortalCoroutineScope) }
                showDialog(taskName) { RatingDialog() }
                true
            }
        }
    }
}