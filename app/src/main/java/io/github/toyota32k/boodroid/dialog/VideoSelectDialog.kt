package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.observe
import io.github.toyota32k.binder.recyclerViewBindingEx
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.databinding.DialogVideoSelectBinding
import io.github.toyota32k.boodroid.databinding.ListItemVideoCheckBinding
import io.github.toyota32k.boodroid.offline.CachedVideoItem
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.showYesNoMessageBox
import io.github.toyota32k.lib.player.common.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class VideoSelectDialog : UtDialogEx() {
    class VideoSelectDialogViewModel: UtDialogViewModel() {
        val videoList = ObservableList<CachedVideoItem>().apply { addAll(OfflineManager.instance.getOfflineVideos()) }
        val enableFilter = MutableStateFlow(AppViewModel.instance.offlineFilter)
        val totalTime = MutableStateFlow(0L)
        val isSelected:Boolean
            get() = videoList.firstOrNull { it.filter>0 }!=null
//        val isSelected: StateFlow<Boolean> = MutableStateFlow(false)
//        val commandSelectAll = Command { selectAll() }
//        val commandUnselectAll = Command { unselectAll() }

        fun updateTotalTime() {
            totalTime.value = videoList.fold(0L) { acc, item ->
                if(item.filter>0) acc+item.duration else acc
            }
        }

//        init {
//            updateSelection()
//        }

//        fun updateSelection() {
//            isSelected.mutable.value = videoList.firstOrNull { it.filter>0 }!=null
//        }

//        private fun unselectAll() {
//            val itr = videoList.listIterator()
//            while(itr.hasNext()) {
//                val e = itr.next()
//                if(e.filter>0) {
//                    e.filter = 0
//                    itr.set(e)          // Changedイベントを発行させる
//                }
//            }
//            updateSelection()
//        }
//
//        private fun selectAll() {
//            val itr = videoList.listIterator()
//            while(itr.hasNext()) {
//                val e = itr.next()
//                if(e.filter<=0) {
//                    e.filter = 1
//                    itr.set(e)          // Changedイベントを発行させる
//                }
//            }
//            updateSelection()
//        }

        val busy = AtomicBoolean(false)

        init {
            updateTotalTime()
        }

        suspend fun complete():Boolean {
            return if(busy.compareAndSet(false, true)) {
                try {
                    OfflineManager.instance.updateFilter(videoList)
                    if (!AppViewModel.instance.offlineFilter && !enableFilter.value && isSelected) {
                        // 選択したがフィルターを有効化していない？
                        UtImmortalTask.awaitTaskResult("confirmEnableFilter") {
                            val context = BooApplication.instance.applicationContext
                            fun s(@StringRes id: Int): String = context.getString(id)
                            if (showYesNoMessageBox(s(R.string.app_name), "Enable Filter")) {
                                enableFilter.value = true
                            }
                            true
                        }
                    }
                    AppViewModel.instance.updateOfflineMode(true, enableFilter.value, updateList = true)
                } finally { busy.set(false) }
                true
            } else false
        }

//        companion object {
//            /**
//             * ViewModel の生成
//             */
//            fun createBy(task: IUtImmortalTask): VideoSelectDialogViewModel
//                    = UtImmortalViewModelHelper.createBy(VideoSelectDialogViewModel::class.java, task)
//
//            /**
//             * ダイアログから取得する用
//             */
//            fun instanceFor(dialog: IUtDialog): VideoSelectDialogViewModel
//                    = UtImmortalViewModelHelper.instanceFor(VideoSelectDialogViewModel::class.java, dialog)
//
//        }
    }

    val viewModel by lazy { getViewModel<VideoSelectDialogViewModel>() }

    override fun preCreateBodyView() {
        noHeader = true
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        cancellable = false
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
    }

    private lateinit var controls: DialogVideoSelectBinding

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        controls = DialogVideoSelectBinding.inflate(inflater.layoutInflater)
        binder
            .checkBinding(controls.enableFilterCheckbox, viewModel.enableFilter)
            .textBinding(controls.targetTotalTime, viewModel.totalTime.map { formatTime(it*1000,it*1000) })
            .recyclerViewBindingEx(controls.videoList) {
                options(
                    list = viewModel.videoList,
                    inflater = ListItemVideoCheckBinding::inflate,
                    bindView = { itemControls, itemBinder, view, item ->
                        itemControls.videoItemText.text = item.name
                        val check = MutableStateFlow<Boolean>(item.filter>0)
                        itemBinder
                            .owner(owner)
                            .checkBinding(itemControls.videoItemCheckbox, check)
                            .observe(check) {
                                item.filter = if(it == true) 1 else 0
                                viewModel.updateTotalTime()
                            }
                            .bindCommand(LiteUnitCommand(), itemControls.videoItemText) {
                                check.value = !check.value
                            }
                    }
                )
            }
        return controls.root
    }
    override fun onPositive() {
        lifecycleScope.launch {
            if(viewModel.complete()) {
                super.onPositive()
            }
        }
    }

    companion object {
        fun setupOfflineVideoFilter() {
            UtImmortalTask.launchTask("setupOfflineVideoFilter") {
                createViewModel<VideoSelectDialogViewModel>()
                showDialog(taskName) { VideoSelectDialog() }
                true
            }
        }
    }
}