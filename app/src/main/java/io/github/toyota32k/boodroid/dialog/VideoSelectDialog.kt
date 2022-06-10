package io.github.toyota32k.boodroid.dialog

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.toyota32k.bindit.*
import io.github.toyota32k.bindit.list.ObservableList
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.UtImmortalTaskContextSource
import io.github.toyota32k.boodroid.offline.CachedVideoItem
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.utils.DisposableObserver
import io.github.toyota32k.utils.asMutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class VideoSelectDialog : UtDialog() {
    class VideoSelectDialogViewModel: ViewModel(), IUtImmortalTaskMutableContextSource by UtImmortalTaskContextSource() {
        val videoList = ObservableList<CachedVideoItem>().apply { addAll(OfflineManager.instance.getOfflineVideos()) }
        val enableFilter = MutableStateFlow(AppViewModel.instance.offlineFilter)

        val isSelected:Boolean
            get() = videoList.firstOrNull { it.filter>0 }!=null
//        val isSelected: StateFlow<Boolean> = MutableStateFlow(false)
//        val commandSelectAll = Command { selectAll() }
//        val commandUnselectAll = Command { unselectAll() }

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

        suspend fun complete():Boolean {
            return if(busy.compareAndSet(false, true)) {
                try {
                    OfflineManager.instance.updateFilter(videoList)
                    if (!AppViewModel.instance.offlineFilter && !enableFilter.value && isSelected) {
                        // 選択したがフィルターを有効化していない？
                        UtImmortalSimpleTask.runAsync("confirmEnableFilter") {
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

        private val <T> StateFlow<T>.mutable: MutableStateFlow<T>
            get() = this as MutableStateFlow<T>

        companion object {
            /**
             * ViewModel の生成
             */
            fun createBy(task: IUtImmortalTask): VideoSelectDialogViewModel
                    = UtImmortalViewModelHelper.createBy(VideoSelectDialogViewModel::class.java, task)

            /**
             * ダイアログから取得する用
             */
            fun instanceFor(dialog: IUtDialog): VideoSelectDialogViewModel
                    = UtImmortalViewModelHelper.instanceFor(VideoSelectDialogViewModel::class.java, dialog)

        }
    }

    val viewModel by lazy { VideoSelectDialogViewModel.instanceFor(this) }
    val binder = Binder()

    override fun preCreateBodyView() {
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
        cancellable = false
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return inflater.inflate(R.layout.dialog_video_select).also { dlg ->
//            val selectAllButton = dlg.findViewById<Button>(R.id.select_all)
//            val unselectAllButton = dlg.findViewById<Button>(R.id.unselect_all)
            binder.register(
                CheckBinding.create(this, dlg.findViewById(R.id.enable_filter_checkbox), viewModel.enableFilter.asMutableLiveData(this)),
//                EnableBinding.create(this, unselectAllButton, viewModel.isSelected.asLiveData()),
//                viewModel.commandSelectAll.connectViewEx(selectAllButton),
//                viewModel.commandUnselectAll.connectViewEx(unselectAllButton),
                RecyclerViewBinding.create(this,dlg.findViewById(R.id.video_list), viewModel.videoList, R.layout.list_item_video_check) {itemBinder, view, item ->
                    val textView = view.findViewById<TextView>(R.id.video_item_text)
                    val checkbox = view.findViewById<CheckBox>(R.id.video_item_checkbox)
                    val check = MutableLiveData<Boolean>(item.filter>0)
                    textView.text = item.name
                    itemBinder.register(
                        CheckBinding.create(this, checkbox, check),
                        DisposableObserver(check, this) {
                            item.filter = if(it == true) 1 else 0
                        },
                        Command {
                            check.value = check.value == false
                        }.connectView(textView),
                    )
                }
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
        fun setupOfflineVideoFilter() {
            UtImmortalSimpleTask.run("setupOfflineVideoFilter") {
                VideoSelectDialogViewModel.createBy(this)
                showDialog(taskName) { VideoSelectDialog() }
                true
            }
        }
    }
}