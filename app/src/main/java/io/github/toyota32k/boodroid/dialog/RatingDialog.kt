package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.multiVisibilityBinding
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
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.utils.lifecycle.asConstantLiveData
import kotlinx.coroutines.flow.combine

class RatingDialog : UtDialogEx() {
    private lateinit var controls: DialogRatingBinding
    private val viewModel by lazy { getViewModel<RatingViewModel>() }

    override fun preCreateBodyView() {
        isDialog = true
        draggable = true
        widthOption = WidthOption.COMPACT
        heightOption = HeightOption.COMPACT
        gravityOption = GravityOption.CENTER
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        noHeader = true
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        val owner = requireActivity()
        controls = DialogRatingBinding.inflate(inflater.layoutInflater)
        val cap = AppViewModel.instance.capability.value

        return controls.apply {
            binder
                .bindRatingList(ratingSelector, viewModel.rating, cap.ratingList)
                .bindMarkListRadio(markSelector, viewModel.mark, cap.markList)
                .textBinding(itemName, viewModel.name)
                .textBinding(categoryButton, viewModel.category)
                .visibilityBinding(busyPanel, viewModel.busy)
                .multiVisibilityBinding(arrayOf(syncSelectionLabel, syncSelectionButtons), viewModel.supportSyncItemSelection.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(ratingSelector, ratingLabel), viewModel.supportRating.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(markSelector, markLabel), viewModel.supportMark.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(categoryButton, categoryLabel), viewModel.supportCategory.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .bindCommand(LiteUnitCommand(this@RatingDialog::selectCategory), categoryButton)
                .bindCommand(LiteUnitCommand(this@RatingDialog::syncUp), syncUpButton)
                .bindCommand(LiteUnitCommand(this@RatingDialog::syncDown), syncDownButton)
                .observe(viewModel.hasError) {error->
                    if(error) {
                        onError()
                    }
                }
        }.root
    }

    private fun onError() {
        UtImmortalTask.launchTask("rating error") {
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

    private fun syncUp() {
        AppViewModel.instance.syncToServer()
        onPositive()
    }
    private fun syncDown() {
        AppViewModel.instance.syncFromServer()
        onPositive()
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
            UtImmortalTask.launchTask("rating") {
                createViewModel<RatingViewModel> { prepare(item, immortalCoroutineScope) }
                showDialog(taskName) { RatingDialog() }
                true
            }
        }
    }
}