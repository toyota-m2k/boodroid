package io.github.toyota32k.boodroid.dialog

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.checkBinding
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.enableBinding
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.binder.progressBarBinding
import io.github.toyota32k.binder.recyclerViewBinding
import io.github.toyota32k.binder.textBinding
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.IUtPropertyHost
import io.github.toyota32k.boodroid.data.ISizedItem
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.databinding.DialogOfflineModeBinding
import io.github.toyota32k.boodroid.databinding.PanelDownloadProgressBinding
import io.github.toyota32k.boodroid.offline.CachedVideoItem
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.UtDialogEx
import io.github.toyota32k.dialog.task.UtDialogViewModel
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getStringOrNull
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.showYesNoMessageBox
import io.github.toyota32k.lib.player.common.formatSize
import io.github.toyota32k.lib.player.common.formatTime
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.utils.UtObservableFlag
import io.github.toyota32k.utils.android.getAttrColor
import io.github.toyota32k.utils.android.getAttrColorAsDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class OfflineDialog : UtDialogEx() {
    data class Selectable<T>(val value:T, var selected:Boolean=false)

    class OfflineDialogViewModel : UtDialogViewModel(), IUtPropertyHost {
        val offlineMode = MutableStateFlow(AppViewModel.instance.offlineMode)
        val sourceList = ObservableList<Selectable<VideoItem>>()
        val targetList = ObservableList<Selectable<ISizedItem>>()

        val isSourceSelected:Flow<Boolean> = MutableStateFlow(false)
        val isTargetSelected:Flow<Boolean> = MutableStateFlow(false)
        val hasTarget = MutableStateFlow(targetList.isNotEmpty())

        val sourceTotalSize:Flow<Long> = MutableStateFlow(0L)
        val sourceTotalTime:Flow<Long> = MutableStateFlow(0L)
        val targetTotalSize:Flow<Long> = MutableStateFlow(0L)
        val targetTotalTime:Flow<Long> = MutableStateFlow(0L)

        val selectedSources:List<VideoItem>
            get() = sourceList.mapNotNull { if(it.selected) it.value else null }
        val selectedTargets:List<IMediaSource>
            get() = targetList.mapNotNull { if(it.selected) it.value else null }

        private var prepared:Boolean = false
        val loadingSources = UtObservableFlag()
//        val busy:Flow<Boolean> = combine(loadingSources,OfflineManager.instance.busy) { s,o->s||o }

        val downloadProgress = OfflineManager.DownloadProgress()
        var disposables = Binder()
        fun prepare(sources:List<VideoItem>?) {
            if(!prepared) {
                prepared = true
                targetList.clear()
                sourceList.clear()
                targetList.addAll(BooApplication.instance.offlineManager.getOfflineVideos().map { Selectable(it) })
                if(sources!=null) {
                    sourceList.addAll(sources.map { Selectable(it) })
                } else {
                    if(loadingSources.trySetIfNot()) {
                        viewModelScope.launch {
                            try {
                                val list = withContext(Dispatchers.IO) {
                                    VideoListSource.retrieve(0)?.list
                                }
                                if (list != null) {
                                    sourceList.addAll(list.map { Selectable(it as VideoItem) })
                                }
                            } finally {
                                loadingSources.reset()
                            }
                        }
                    }
                }

                disposables.register(
                    targetList.addListenerForever {
                        data class TotalInfo(var size:Long=0L, var time:Long=0L)
                        val total = it.list.fold(TotalInfo()) { acc, selectable ->
                            acc.size += selectable.value.size
                            acc.time += selectable.value.duration
                            acc
                        }
                        targetTotalSize.mutable.value = total.size
                        targetTotalTime.mutable.value = total.time
                        hasTarget.value = it.list.isNotEmpty()
                    },
                )
            }
        }

        override fun onCleared() {
            logger.debug()
            disposables.dispose()
        }

        fun sourceSelectionChanged() {
            (isSourceSelected as MutableStateFlow).value = sourceList.firstOrNull { it.selected } != null
            data class TotalInfo(var size:Long=0L, var time:Long=0L)
            val total = sourceList.fold(TotalInfo()) { acc, selectable ->
                if(selectable.selected) {
                    acc.size += selectable.value.size
                    acc.time += selectable.value.duration
                }
                acc
            }
            sourceTotalSize.mutable.value = total.size
            sourceTotalTime.mutable.value = total.time
        }

        fun targetSelectionChanged() {
            isTargetSelected.mutable.value = targetList.firstOrNull { it.selected } != null
        }

        private fun clearTarget() {
            targetList.clear()
        }

        private fun <T> clearSelection(list:ObservableList<Selectable<T>>) {
            val itr = list.listIterator()
            while(itr.hasNext()) {
                val e = itr.next()
                if(e.selected) {
                    e.selected = false
                    itr.set(e)          // Changedイベントを発行させる
                }
            }
        }

        private fun clearSourceSelection() {
            clearSelection(sourceList)
            (isSourceSelected as MutableStateFlow).value = false
        }
        private fun clearTargetSelection() {
            clearSelection(targetList)
            isTargetSelected.mutable.value = false
        }

        private fun clearSelection() {
            clearSourceSelection()
            clearTargetSelection()
        }

        private fun containsInTarget(src:VideoItem):Boolean {
            return targetList.firstOrNull {
                when(it.value) {
                    is VideoItem -> it.value.uri == src.uri
                    is CachedVideoItem -> it.value.id == src.uri
                    else -> false
                }
            } == null
        }

        fun addToTargets() {
            clearTargetSelection()
            targetList.addAll(selectedSources.filter(this::containsInTarget).map { Selectable(it, true) })
            clearSourceSelection()
            targetSelectionChanged()
        }

        fun deleteFromTargets() {
            targetList.removeAll { it.selected }
            isTargetSelected.mutable.value = false
        }

        val commandAdd = LiteUnitCommand { addToTargets() }
        val commandDelete = LiteUnitCommand { deleteFromTargets() }
        val commandClearSelection = LiteUnitCommand { clearSelection() }
        val commandDeleteAll = LiteUnitCommand { clearTarget() }

        // val completed:Flow<Boolean?> = MutableStateFlow(null)

        suspend fun complete():Boolean {
            if(loadingSources.flagged) return false

            val newList = OfflineManager.instance.setOfflineVideos(targetList.map { it.value }, downloadProgress) ?: return false
//            AppViewModel.instance.settings = Settings(AppViewModel.instance.settings, offlineMode = offlineMode.value)
            val oldMode = AppViewModel.instance.offlineMode
            val newMode = if(!oldMode && !offlineMode.value && newList.isNotEmpty()) {
                UtImmortalTask.awaitTaskResult("enterOfflineMode") {
                    showYesNoMessageBox(getStringOrNull(R.string.app_name), getStringOrNull(R.string.query_enter_offline_mode))
                }
            } else offlineMode.value
            AppViewModel.instance.updateOfflineMode(newMode, filter = false, updateList = true)
            return true
        }

//        companion object {
//            /**
//             * ViewModel の生成
//             */
//            fun createBy(task: IUtImmortalTask, sources:List<VideoItem>?):OfflineDialogViewModel
//                = UtImmortalViewModelHelper.createBy(OfflineDialogViewModel::class.java, task) { it.prepare(sources) }
//
//            /**
//             * ダイアログから取得する用
//             */
//            fun instanceFor(dialog: IUtDialog): OfflineDialogViewModel
//                = UtImmortalViewModelHelper.instanceFor(OfflineDialogViewModel::class.java, dialog)
//        }
    }
    
    val viewModel:OfflineDialogViewModel by lazy { getViewModel<OfflineDialogViewModel>() }

    @ColorInt
    private var normalTextColor: Int = Color.WHITE
    @ColorInt
    private var selectedTextColor:Int = Color.BLACK
    private lateinit var normalColor: Drawable
    private lateinit var selectedColor: Drawable

    override fun preCreateBodyView() {
        isDialog = true
        context.theme!!.apply {
            normalColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSurface, Color.WHITE)
            normalTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            selectedColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSecondary, Color.BLUE)
            selectedTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSecondary, Color.WHITE)
        }
//        bodyGuardColor = context.getColor(R.color.guard_color)
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        cancellable = false
        leftButtonType = ButtonType.CANCEL
        rightButtonType = ButtonType.DONE
    }

    lateinit var controls: DialogOfflineModeBinding
    lateinit var progressControls: PanelDownloadProgressBinding

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        // KB単位で整形
        fun stringInKb(size: Long): String {
            return String.format(Locale.US, "%,d KB", size / 1000L)
        }

        progressControls = PanelDownloadProgressBinding.inflate(inflater.layoutInflater, bodyGuardView, true).apply {
            binder
                .progressBarBinding(bytesProgress, viewModel.downloadProgress.percentInBytes)
                .progressBarBinding(countProgress, viewModel.downloadProgress.percentInCount)
                .textBinding(bytesProgressMessage,combine(viewModel.downloadProgress.length, viewModel.downloadProgress.received) { length, received -> "${stringInKb(received)} KB / ${stringInKb(length)} KB"})
                .textBinding(countProgressMessage, combine(viewModel.downloadProgress.count, viewModel.downloadProgress.index) {count, index -> "$index / $count"})
                .textBinding(message, viewModel.downloadProgress.message)
                .visibilityBinding(progressBars, viewModel.downloadProgress.showProgressBar)
        }

        fun setSelectionColor(textView: TextView, selected: Boolean) {
            if(selected) {
                textView.background = selectedColor
                textView.setTextColor(selectedTextColor)
            } else {
                textView.background = normalColor
                textView.setTextColor(normalTextColor)
            }
        }
        fun <T:IMediaSource> bindSourceItemView(itemBinder:Binder, view:View, item:Selectable<T>) {
            val textView = view.findViewById<TextView>(R.id.video_item_text)
            textView.text = item.value.name
            setSelectionColor(textView, item.selected)
            itemBinder.register(
                Command().connectAndBind(this, textView) {
                    item.selected = !item.selected
                    setSelectionColor(it as TextView, item.selected)
                    viewModel.sourceSelectionChanged()
                },
            )
        }
        fun <T:IMediaSource> bindTargetItemView(itemBinder:Binder, view:View, item:Selectable<T>) {
            val textView = view.findViewById<TextView>(R.id.video_item_text)
            textView.text = item.value.name
            setSelectionColor(textView, item.selected)
            itemBinder.register(
                Command().connectAndBind(this, textView) {
                    item.selected = !item.selected
                    setSelectionColor(it as TextView, item.selected)
                    viewModel.targetSelectionChanged()
                },
            )
        }

        controls = DialogOfflineModeBinding.inflate(inflater.layoutInflater).apply {
            binder
                .checkBinding(enableOfflineMode, viewModel.offlineMode)
                .enableBinding(addButton, viewModel.isSourceSelected)
                .enableBinding(delButton, viewModel.isTargetSelected)
                .enableBinding(resetSelectionButton, combine(viewModel.isSourceSelected, viewModel.isTargetSelected){s,t->s||t})
                .enableBinding(clearTargetButton, viewModel.hasTarget)
                .textBinding(targetTotalSize, viewModel.targetTotalSize.map { formatSize(it) })
                .textBinding(targetTotalTime, viewModel.targetTotalTime.map { formatTime(it*1000,it*1000) })
                .textBinding(sourceTotalSize, viewModel.sourceTotalSize.map { formatSize(it) })
                .textBinding(sourceTotalTime, viewModel.sourceTotalTime.map { formatTime(it*1000,it*1000) })
                .bindCommand(viewModel.commandAdd, addButton)
                .bindCommand(viewModel.commandDelete, delButton)
                .bindCommand(viewModel.commandClearSelection, resetSelectionButton)
                .bindCommand(viewModel.commandDeleteAll, clearTargetButton)
                .recyclerViewBinding(sourceList) {
                    options(
                        list = viewModel.sourceList,
                        itemLayoutId = R.layout.list_item_video,
                        bindView = ::bindSourceItemView
                    )
                }
                .recyclerViewBinding(targetList) {
                    options(
                        list = viewModel.targetList,
                        itemLayoutId = R.layout.list_item_video,
                        bindView = ::bindTargetItemView,
                        dragAndDrop = true
                    )
                }
                .dialogBodyGuardViewVisibility(OfflineManager.instance.busy, showProgressRing = true)
                .dialogRightButtonEnable(OfflineManager.instance.busy, boolConvert = BoolConvert.Inverse)
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
        fun setupOfflineMode(videoList:List<VideoItem>?) {
            UtImmortalTask.launchTask(OfflineDialog::class.java.name) {
                createViewModel<OfflineDialogViewModel> { prepare(videoList) }
                showDialog(taskName) { OfflineDialog() }
            }
        }
    }
}