package io.github.toyota32k.boodroid.dialog

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.UtImmortalTaskContextSource
import io.github.toyota32k.boodroid.common.getAttrColor
import io.github.toyota32k.boodroid.common.getAttrColorAsDrawable
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.offline.CachedVideoItem
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.utils.UtObservableFlag
import io.github.toyota32k.video.common.IAmvSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineDialog : UtDialog(isDialog = true) {
    data class Selectable<T>(val value:T, var selected:Boolean=false)

    class OfflineDialogViewModel : ViewModel(), IUtImmortalTaskMutableContextSource by UtImmortalTaskContextSource() {
//        val offlineMode = MutableStateFlow(AppViewModel.instance.offlineMode)
        val sourceList = ObservableList<Selectable<VideoItem>>()
        val targetList = ObservableList<Selectable<IAmvSource>>()

        val isSourceSelected:Flow<Boolean> = MutableStateFlow(false)
        val isTargetSelected:Flow<Boolean> = MutableStateFlow(false)
        val hasTarget = MutableStateFlow(targetList.isNotEmpty())

        val selectedSources:List<VideoItem>
            get() = sourceList.mapNotNull { if(it.selected) it.value else null }
        val selectedTargets:List<IAmvSource>
            get() = targetList.mapNotNull { if(it.selected) it.value else null }

        private var prepared:Boolean = false
        val loadingSources = UtObservableFlag()
//        val busy:Flow<Boolean> = combine(loadingSources,OfflineManager.instance.busy) { s,o->s||o }

        val downloadProgress = OfflineManager.DownloadProgress()

        fun prepare(sources:List<VideoItem>?) {
            if(!prepared) {
                prepared = true
                targetList.clear()
                sourceList.clear()
                targetList.addAll(BooApplication.instance.offlineManager.getOfflineVideos().map { Selectable(it) })
                if(sources!=null) {
                    sourceList.addAll(sources.map { Selectable(it) })
                } else {
                    if(loadingSources.set()) {
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
            }
        }

        override fun onCleared() {
            logger.debug()
        }

        fun sourceSelectionChanged() {
            (isSourceSelected as MutableStateFlow).value = sourceList.firstOrNull { it.selected } != null
        }

        fun targetSelectionChanged() {
            (isTargetSelected as MutableStateFlow).value = targetList.firstOrNull { it.selected } != null
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
            (isTargetSelected as MutableStateFlow).value = false
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
            (isTargetSelected as MutableStateFlow).value = false
        }

        val commandAdd = Command { addToTargets() }
        val commandDelete = Command { deleteFromTargets() }
        val commandClearSelection = Command { clearSelection() }
        val commandDeleteAll = Command { clearTarget() }

        // val completed:Flow<Boolean?> = MutableStateFlow(null)

        suspend fun complete():Boolean {
            if(loadingSources.value) return false

            val newList = OfflineManager.instance.setOfflineVideos(targetList.map { it.value }, downloadProgress) ?: return false
//            AppViewModel.instance.settings = Settings(AppViewModel.instance.settings, offlineMode = offlineMode.value)
            val oldMode = AppViewModel.instance.offlineMode
            val newMode = if(!oldMode && newList.isNotEmpty()) {
                UtImmortalSimpleTask.runAsync("enterOfflineMode") {
                    val context = BooApplication.instance.applicationContext
                    fun s(@StringRes id:Int) : String = context.getString(id)
                    showYesNoMessageBox(s(R.string.app_name), s(R.string.query_enter_offline_mode))
                }
            } else oldMode
            AppViewModel.instance.updateOfflineMode(newMode, updateList = true)
            return true
        }

        companion object {
            /**
             * ViewModel の生成
             */
            fun createBy(task: IUtImmortalTask, sources:List<VideoItem>?):OfflineDialogViewModel
                = UtImmortalViewModelHelper.createBy(OfflineDialogViewModel::class.java, task) { it.prepare(sources) }

            /**
             * ダイアログから取得する用
             */
            fun instanceFor(dialog: IUtDialog): OfflineDialogViewModel
                = UtImmortalViewModelHelper.instanceFor(OfflineDialogViewModel::class.java, dialog)
        }
    }
    
    val binder = Binder()
    val viewModel:OfflineDialogViewModel by lazy { OfflineDialogViewModel.instanceFor(this) }

    @ColorInt
    private var normalTextColor: Int = Color.WHITE
    @ColorInt
    private var selectedTextColor:Int = Color.BLACK
    private lateinit var normalColor: Drawable
    private lateinit var selectedColor: Drawable

    override fun preCreateBodyView() {
        context.theme!!.apply {
            normalColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSurface, Color.WHITE)
            normalTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            selectedColor = getAttrColorAsDrawable(com.google.android.material.R.attr.colorSecondary, Color.BLUE)
            selectedTextColor = getAttrColor(com.google.android.material.R.attr.colorOnSecondary, Color.WHITE)
        }
        bodyGuardColor = Color.argb(0xDD,0xFF, 0xFF, 0xFF) // GuardColor.SEE_THROUGH.color
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        cancellable = false
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        inflater.layoutInflater.inflate(R.layout.panel_download_progress, bodyGuardView).also { panel->
            // KB単位で整形
            fun stringInKb(size: Long): String {
                return String.format("%,d KB", size / 1000L)
            }
            val bytesProgress = panel.findViewById<ProgressBar>(R.id.bytes_progress)
            val countProgress = panel.findViewById<ProgressBar>(R.id.count_progress)
            val bytesTextView = panel.findViewById<TextView>(R.id.bytes_progress_message)
            val countTextView = panel.findViewById<TextView>(R.id.count_progress_message)
            val messageTextView = panel.findViewById<TextView>(R.id.message)
            val progressBarPanel = panel.findViewById<View>(R.id.progress_bars)
            binder.register(
                ProgressBarBinding.create(this, bytesProgress, viewModel.downloadProgress.percentInBytes.asLiveData()),
                ProgressBarBinding.create(this, countProgress, viewModel.downloadProgress.percentInCount.asLiveData()),
                TextBinding.create(this, bytesTextView, combine(viewModel.downloadProgress.length, viewModel.downloadProgress.received) { length, receivied ->
                    "${stringInKb(receivied)} KB / ${stringInKb(length)} KB"
                }.asLiveData()),
                TextBinding.create(this, countTextView, combine(viewModel.downloadProgress.count, viewModel.downloadProgress.index) {count, index ->
                    "${index} / ${count}"
                }.asLiveData()),
                TextBinding.create(this, messageTextView, viewModel.downloadProgress.message.asLiveData()),
                VisibilityBinding.create(this, progressBarPanel, viewModel.downloadProgress.showProgressBar.asLiveData()),
            )
        }
//        bodyGuardView.background = ColorDrawable(Color.argb(0xDD,0xFF, 0xFF, 0xFF))
//        bodyGuardView.visibility = View.VISIBLE


        return inflater.inflate(R.layout.dialog_offline_mode).also { dlg->
            val addButton = dlg.findViewById<Button>(R.id.add_button)
            val delButton = dlg.findViewById<Button>(R.id.del_button)
            val resetSelButton = dlg.findViewById<Button>(R.id.reset_selection_button)
            val clearAllButton = dlg.findViewById<Button>(R.id.clear_target_button)
            val sourceView = dlg.findViewById<RecyclerView>(R.id.source_list)
            val targetView = dlg.findViewById<RecyclerView>(R.id.target_list)
            fun setSelectionColor(textView: TextView, selected: Boolean) {
                if(selected) {
                    textView.background = selectedColor
                    textView.setTextColor(selectedTextColor)
                } else {
                    textView.background = normalColor
                    textView.setTextColor(normalTextColor)
                }
            }

            fun <T:IAmvSource> bindItemView(itemBinder:Binder, view:View, item:Selectable<T>) {
                    val textView = view.findViewById<TextView>(R.id.video_item_text)
                    textView.text = item.value.name
                    setSelectionColor(textView, item.selected)
                    itemBinder.register(
                        Command().connectAndBind(this, textView) {
                            item.selected = !item.selected
                            setSelectionColor(it as TextView, item.selected)
                            viewModel.sourceSelectionChanged()
                            viewModel.targetSelectionChanged()
                        },
                    )
            }
            binder.register(
//                CheckBinding.create(this, dlg.findViewById(R.id.source_list_label), viewModel.offlineMode.asMutableLiveData(this)),

                EnableBinding.create(this, addButton, viewModel.isSourceSelected.asLiveData()),
                EnableBinding.create(this, delButton, viewModel.isTargetSelected.asLiveData()),
                EnableBinding.create(this, resetSelButton, combine(viewModel.isSourceSelected, viewModel.isTargetSelected) {s,t->s||t}.asLiveData() ),
                EnableBinding.create(this, clearAllButton, viewModel.hasTarget.asLiveData()),
                viewModel.targetList.addListener(this) { viewModel.hasTarget.value = it.list.isNotEmpty() },

                viewModel.commandAdd.connectViewEx(addButton),
                viewModel.commandDelete.connectViewEx(delButton),
                viewModel.commandClearSelection.connectViewEx(resetSelButton),
                viewModel.commandDeleteAll.connectViewEx(clearAllButton),
                RecyclerViewBinding.create(this, sourceView, viewModel.sourceList, R.layout.list_item_video, bindView = ::bindItemView),
                RecyclerViewBinding.create(this, targetView, viewModel.targetList, R.layout.list_item_video, bindView = ::bindItemView).apply { enableDragAndDrop(true) },

                VisibilityBinding.create(this, bodyGuardView, OfflineManager.instance.busy.asLiveData()),
                EnableBinding.create(this, rightButton, OfflineManager.instance.busy.asLiveData(), boolConvert = BoolConvert.Inverse),

//                RecyclerViewBinding.create(this, sourceView, viewModel.sourceList, R.layout.list_item_video) { itemBinder, view, item ->
//                    val textView = view.findViewById<TextView>(R.id.video_item_text)
//                    textView.text = item.value.name
//                    setSelectionColor(textView, item.selected)
//                    itemBinder.register(
//                        Command().connectAndBind(this, textView) {
//                            item.selected = !item.selected
//                            setSelectionColor(it as TextView, item.selected)
//                            viewModel.sourceSelectionChanged()
//                        },
//                    )
//                },

//                RecyclerViewBinding.create(this, targetView, viewModel.targetList, R.layout.list_item_video) { itemBinder, view, item ->
//                    val textView = view.findViewById<TextView>(R.id.video_item_text)
//                    textView.text = item.value.name
//                    setSelectionColor(textView, item.selected)
//                    itemBinder.register(
//                        Command().connectAndBind(this, textView) {
//                            item.selected = !item.selected
//                            setSelectionColor(it as TextView, item.selected)
//                            viewModel.sourceSelectionChanged()
//                        },
//                    )
//                }.apply { enableDragAndDrop(true) }
            )
        }
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
            UtImmortalSimpleTask.run(OfflineDialog::class.java.name) {
                OfflineDialogViewModel.createBy(this, videoList)
                showDialog(taskName) { OfflineDialog() }
                true
            }
        }
    }
}