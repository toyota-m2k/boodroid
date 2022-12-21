package io.github.toyota32k.boodroid.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.IUtPropertyHost
import io.github.toyota32k.boodroid.data.*
import io.github.toyota32k.boodroid.dialog.OfflineDialog
import io.github.toyota32k.boodroid.dialog.VideoSelectDialog
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.dialog.UtSingleSelectionBox
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.getActivity
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.video.common.IAmvSource
import io.github.toyota32k.video.model.ControlPanelModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainViewModel : ViewModel() {
    companion object {
        fun instanceFor(owner: MainActivity): MainViewModel {
            return ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[MainViewModel::class.java].prepare(owner)
        }
    }

    lateinit var controlPanelModel:ControlPanelModel
    val playerModel get() = controlPanelModel.playerModel
    val appViewModel: AppViewModel by lazy { AppViewModel.instance }

    val syncToServerCommand = Command()

    val syncFromServerCommand = Command ()

    val syncWithServerCommand = Command()

//    val menuCommand = Command()

    val setupOfflineModeCommand = Command()

    val selectOfflineVideoCommand = Command()


    private var prepared = false
    private val disposer = Binder()

    private fun prepare(@Suppress("UNUSED_PARAMETER") owner:MainActivity):MainViewModel {
        if(!prepared) {
            prepared = true
            controlPanelModel = appViewModel.controlPanelModelSource.fetch()
            disposer.register(
                appViewModel.refreshCommand.bindForever { refreshVideoList() },
                syncFromServerCommand.bindForever { syncFromServer() },
                syncToServerCommand.bindForever { syncToServer() },
//                menuCommand.bindForever { showMenu() },
                syncWithServerCommand.bindForever { syncWithServer() },
                setupOfflineModeCommand.bindForever { setupOfflineMode() },
                selectOfflineVideoCommand.bindForever { setupOfflineFilter() }
            )
            refreshVideoList()
        }
        return this
    }

    override fun onCleared() {
        super.onCleared()
        if(prepared) {
            prepared = false
            disposer.reset()
            AppViewModel.instance.controlPanelModelSource.release(controlPanelModel)
        }
    }

    // 通信中フラグ
    private val loading = MutableStateFlow(false)
    private var lastUpdate : Long = 0L

    private data class PlayPositionInfo(val index:Int, val position:Long)

    private fun getPlayPositionInfo(list:List<IAmvSource>):PlayPositionInfo {
        var index = -1
        var position = 0L
        // 再生中なら、同じ場所から再開
        val current = playerModel.currentSource.value
        if(current!=null) {
            index = list.indexOfFirst { it.id == current.id }
            position = playerModel.playerSeekPosition.value
        }
        // 再生中でなければ、前回の再生位置から復元
        if(index<0) {
            val lpi = LastPlayInfo.get(BooApplication.instance)
            if (lpi != null) {
                index = list.indexOfFirst { lpi.id == it.id }
                position = if (index >= 0) lpi.position else 0L
            }
        }
        return PlayPositionInfo(index, position)
    }

    private fun refreshVideoListFromServer() {
        AppViewModel.logger.debug()
        viewModelScope.launch {
            if(loading.value == true) {
                AppViewModel.logger.error("busy.")
                return@launch
            }
            val src = try {
                loading.value = true
                withContext(Dispatchers.IO) {
                    appViewModel.setCapability(ServerCapability.get())
                    VideoListSource.retrieve()
                }
            } catch(e:Throwable) {
                AppViewModel.logger.stackTrace(e)
                null
            } finally {
                loading.value = false
            }

            if(src!=null) {
                lastUpdate = src.modifiedDate
                AppViewModel.logger.debug("list.count=${src.list.size}")
                val pos = getPlayPositionInfo(src.list)
                playerModel.setSources(src.list, pos.index, pos.position)
//                if(updateTimerTask==null) {
//                    updateTimerTask = Timer().run {
//                        schedule(60000,60000) {
//                            updateVideoList()
//                        }
//                    }
//                }
            } else {
                AppViewModel.logger.debug("list empty")
                lastUpdate = 0L
                playerModel.setSources(emptyList())
//                updateTimerTask?.cancel()
//                updateTimerTask = null
            }
        }
    }

    private fun refreshVideoListFromLocal() {
        AppViewModel.logger.debug()
        val appViewModel = AppViewModel.instance
        val om = OfflineManager.instance
        if (om.busy.flagged) return
        val list = om.getOfflineVideos().run {
            if(appViewModel.offlineFilter) {
                filter { it.filter>0 }
            } else this
        }
        if(list.isEmpty()) {
            UtImmortalSimpleTask.run("emptyOfflineMode") {
                val context = BooApplication.instance
                showConfirmMessageBox(context.getString(R.string.offline_mode), context.getString(R.string.offline_empty_list))
                true
            }
        }

        val pos = getPlayPositionInfo(list)
        playerModel.setSources(list, pos.index, pos.position)
    }

    fun refreshVideoList() {
        if(AppViewModel.instance.offlineMode) {
            refreshVideoListFromLocal()
        } else {
            refreshVideoListFromServer()
        }
    }

    fun tryPlayAt(id: String) {
        val index = playerModel.videoSources.indexOfFirst { it.id == id }
        playerModel.playAt(index)
    }

    /**
     * BooRemote で再生中の動画をBooTubeの動画リスト上で選択する
     */
    fun syncToServer() {
        val current = controlPanelModel.playerModel.currentSource.value ?: return
        val url = appViewModel.settings.urlCurrentItem()
        val json = JSONObject()
            .put("id", current.id)
            .toString()
        val req = Request.Builder()
            .url(url)
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NetClient.executeAsync(req).close()
            } catch(e:Throwable) {
                UtLogger.stackTrace(e)
            }
        }
    }

    /**
     * BooTube上で選択（フォーカス）されている動画をBooRemote上で再生する。
     */
    fun syncFromServer() {
        val url = appViewModel.settings.urlCurrentItem()
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = NetClient.executeAndGetJsonAsync(req)
                withContext(Dispatchers.Main) {
                    tryPlayAt(json.getString("id"))
                }
            } catch(e:Throwable) {
                UtLogger.stackTrace(e)
            }
        }
    }

    // YouTube url をサーバーに登録
    fun registerYouTubeUrl(rawUrl:String) {
        val urlParam = rawUrl.split("\r", "\n", " ", "\t").firstOrNull { it.isNotBlank() } ?: return
        val url = appViewModel.settings.urlToRegister(urlParam)
        CoroutineScope(Dispatchers.IO).launch {
            val req = Request.Builder()
                .url(url)
                .get()
                .build()
            try {
                NetClient.executeAsync(req)
            } catch (e:Throwable) {
                AppViewModel.logger.stackTrace(e)
            }
        }
    }

    private fun syncWithServer() {
        UtImmortalSimpleTask.run("OfflineMenu") {
            val context = BooApplication.instance.applicationContext
            fun s(@StringRes id:Int):String = context.getString(id)
            val menuItems = arrayOf(
                s(R.string.menu_sync_from_server),
                s(R.string.menu_sync_to_server),
            )
            val dlg = showDialog(taskName) { UtSingleSelectionBox.create(s(R.string.app_name), menuItems) }
            if(dlg.status.positive) {
                when(dlg.selectedIndex) {
                    0-> syncFromServer()
                    1-> syncToServer()
                }
            }
            true
        }

    }

//    private fun showMenu() {
//        val list = OfflineManager.instance.database.dataTable().getAll()
//        if(list.isEmpty()) {
//            setupOfflineMode()
//            return
//        }
//
//        UtImmortalSimpleTask.run("OfflineMenu") {
//            val context = BooApplication.instance.applicationContext
//            fun s(@StringRes id:Int):String = context.getString(id)
//            val menuItems = arrayOf(
//                s(if(AppViewModel.instance.offlineMode) R.string.menu_item_exit_offline_mode else R.string.menu_item_enter_offline_mode),
//                s(R.string.menu_item_setup_offline_mode),
//                s(R.string.menu_item_select_offline_videos),
//            )
//            val dlg = showDialog(taskName) { UtSingleSelectionBox.create(s(R.string.app_name), menuItems) }
//            if(dlg.status.positive) {
//                when(dlg.selectedIndex) {
//                    0-> AppViewModel.instance.updateOfflineMode(!AppViewModel.instance.offlineMode, filter = AppViewModel.instance.offlineFilter, updateList = false)
//                    1-> setupOfflineMode()
//                    2-> setupOfflineFilter()
//                }
//            }
//            true
//        }
//    }

    /**
     * オフラインモードの設定
     */
    private fun setupOfflineMode() {
        playerModel.pause()
        val list:List<VideoItem>? =if(!AppViewModel.instance.offlineMode) {
            @Suppress("UNCHECKED_CAST")
            playerModel.videoSources as List<VideoItem>
        } else null
        OfflineDialog.setupOfflineMode(list)
    }

    /**
     *
     */
    private fun setupOfflineFilter() {
        playerModel.pause()
        VideoSelectDialog.setupOfflineVideoFilter()
    }

}