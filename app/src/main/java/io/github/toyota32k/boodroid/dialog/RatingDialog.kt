package io.github.toyota32k.boodroid.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.VisibilityBinding
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.drawableBinding
import io.github.toyota32k.binder.genericBinding
import io.github.toyota32k.binder.headlessBinding
import io.github.toyota32k.binder.multiEnableBinding
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
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.utils.android.dp
import io.github.toyota32k.utils.lifecycle.asConstantLiveData
import kotlinx.coroutines.flow.combine

class RatingDialog : UtDialogEx() {
    private lateinit var controls: DialogRatingBinding
    private val viewModel by lazy { getViewModel<RatingViewModel>() }

    override fun preCreateBodyView() {
        draggable = true
        widthOption = WidthOption.LIMIT(480)

        heightOption = HeightOption.AUTO_SCROLL
        gravityOption = GravityOption.CENTER
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.OK
        noHeader = true
        noFooter = true
//        bodyContainerMargin = 8.dp.px(requireContext())
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
                .checkBinding(offlineSwitch, viewModel.offline)
                .visibilityBinding(busyPanel, viewModel.busy)
                .headlessBinding(viewModel.prepared) {
                    if (it==true) {
                        busyPanel.background = Color.argb(0,0,0,0).toDrawable()
                        expProgressRing.isVisible = false
                    }
                }
                .visibilityBinding(uploadProgressRing, viewModel.offlineDataHandling)
                .multiVisibilityBinding(arrayOf(syncSelectionLabel, syncSelectionButtons), viewModel.supportSyncItemSelection.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(ratingSelector, ratingLabel), viewModel.supportRating.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(markSelector, markLabel), viewModel.supportMark.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
                .multiVisibilityBinding(arrayOf(categoryButton, categoryLabel), viewModel.supportCategory.asConstantLiveData(), hiddenMode = VisibilityBinding.HiddenMode.HideByGone)
//                .multiEnableBinding(arrayOf(syncUpButton, syncDownButton, ratingSelector, markSelector,categoryButton), viewModel.busy, boolConvert = BoolConvert.Inverse)
                .bindCommand(LiteUnitCommand(this@RatingDialog::selectCategory), categoryButton)
                .bindCommand(LiteUnitCommand(this@RatingDialog::syncUp), syncUpButton)
                .bindCommand(LiteUnitCommand(this@RatingDialog::syncDown), syncDownButton)
                .observe(viewModel.hasError) {error->
                    if(error) {
                        onError()
                    }
                }
                .genericBinding(uploadProgressRing, viewModel.uploadProgress) { view, progress ->
                    view.progress = progress?:0
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
        listPopup.anchorView = controls.categoryButton
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

    override fun confirmToCompletePositive(): Boolean {
        return !viewModel.busy.value
    }

    override fun confirmToCompleteNegative(): Boolean {
        return !viewModel.offlineDataHandling.value
    }


    companion object {
        fun show(item: VideoItem) {
            UtImmortalTask.launchTask(RatingDialog::class.java.name) {
                createViewModel<RatingViewModel> { prepare(item, immortalCoroutineScope) }
                showDialog(taskName) { RatingDialog() }
            }
        }
    }
}