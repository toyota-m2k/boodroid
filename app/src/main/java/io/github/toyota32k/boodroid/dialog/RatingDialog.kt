package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.data.bindMarkListRadio
import io.github.toyota32k.boodroid.data.bindRatingList
import io.github.toyota32k.boodroid.databinding.DialogRatingBinding
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.boodroid.viewmodel.RatingViewModel
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox

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

    lateinit var controls: DialogRatingBinding
    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val owner = requireActivity()
        controls = DialogRatingBinding.inflate(inflater.layoutInflater)
        viewModel = RatingViewModel.instanceFor(this)
        val cap = AppViewModel.instance.capability.value

        return controls.apply {
            binder
                .owner(owner)
                .bindRatingList(ratingSelector, viewModel.rating, cap.ratingList)
                .bindMarkListRadio(markSelector, viewModel.mark, cap.markList)
                .textBinding(itemName, viewModel.name)
                .textBinding(categoryButton, viewModel.category)
                .visibilityBinding(busyPanel, viewModel.busy)
                .bindCommand(LiteUnitCommand(this@RatingDialog::selectCategory), categoryButton)
                .observe(viewModel.hasError) {error->
                    if(error) {
                        onError()
                    }
                }
        }.root
    }

    private fun onError() {
        UtImmortalSimpleTask.run("rating error") {
            showConfirmMessageBox(null, "cannot access the video item.")
            onNegative()
            true
        }
    }
    private fun selectCategory() {
        val cap = AppViewModel.instance.capability.value
        val adapter = ArrayAdapter(context, R.layout.list_item_category, cap.categoryList.map { it.label }.toTypedArray())
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