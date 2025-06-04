package io.github.toyota32k.boodroid.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.command.Command
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.ServerCapability
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.dialog.OfflineDialog
import io.github.toyota32k.boodroid.dialog.VideoSelectDialog
import io.github.toyota32k.boodroid.offline.OfflineManager
import io.github.toyota32k.dialog.UtSingleSelectionBox
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.PlayerControllerModel
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    lateinit var controlPanelModel: PlayerControllerModel
    val playerModel get() = controlPanelModel.playerModel
    private val appViewModel: AppViewModel by lazy { AppViewModel.instance }

    private val syncToServerCommand = Command()

    private val syncFromServerCommand = Command ()

    val syncWithServerCommand = LiteUnitCommand()

//    val menuCommand = Command()

    val setupOfflineModeCommand = LiteUnitCommand()

    val selectOfflineVideoCommand = LiteUnitCommand()

    val syncCommandAvailable = appViewModel.capability.map { it.hasView }       // ヘッドレスサーバーに対しては、このコマンドは無意味
    val offlineModeAvailable = appViewModel.capability.map { !it.needAuth }     // 認証を要求するサーバーではオフラインモードは使えない


    private var prepared = false
    private val disposer = Binder()

    private fun prepare(@Suppress("UNUSED_PARAMETER") owner:MainActivity):MainViewModel {
        if(!prepared) {
            prepared = true
            controlPanelModel = appViewModel.controlPanelModelSource.fetch()
            disposer.register(
                appViewModel.refreshCommand.bindForever { refreshVideoList(it) },
                syncFromServerCommand.bindForever { syncFromServer() },
                syncToServerCommand.bindForever { syncToServer() },
//                menuCommand.bindForever { showMenu() },
                syncWithServerCommand.bindForever { syncWithServer() },
                setupOfflineModeCommand.bindForever { setupOfflineMode() },
                selectOfflineVideoCommand.bindForever { setupOfflineFilter() }
            )
            refreshVideoList(false)
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

    fun savePlayPositionInfo() {
        val current = playerModel.currentSource.value
        if (current != null) {
            val pos = playerModel.playerSeekPosition.value
            LastPlayInfo.set(BooApplication.instance.applicationContext, current.id, pos, true)
        }
    }

    private fun getPlayPositionInfo(list:List<IMediaSource>):PlayPositionInfo {
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

    var serverAvailable: Boolean = false
        private set
    private fun refreshVideoListFromServer() {
        AppViewModel.logger.debug()
        viewModelScope.launch {
            if(loading.value) {
                AppViewModel.logger.error("busy.")
                return@launch
            }
            val src = try {
                loading.value = true
                withContext(Dispatchers.IO) {
                    val cap = ServerCapability.get(appViewModel.settings.hostAddress)
                    serverAvailable = cap!=null
                    appViewModel.setCapability(cap ?: ServerCapability.empty)
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
                src.setCurrentSource(pos.index, pos.position)
//                if(updateTimerTask==null) {
//                    updateTimerTask = Timer().run {
//                        schedule(60000,60000) {
//                            updateVideoList()
//                        }
//                    }
//                }
                AppViewModel.instance.videoListSource = src
            } else {
                AppViewModel.logger.debug("list empty")
                lastUpdate = 0L
                AppViewModel.instance.videoListSource = null
            }
        }
    }

    private fun refreshVideoListFromLocal() {
        AppViewModel.logger.debug()
        val appViewModel = AppViewModel.instance
//        val listSource = appViewModel.videoListSource ?: return
        val om = OfflineManager.instance
        if (om.busy.flagged) return
        val list = om.getOfflineVideos().run {
            if(appViewModel.offlineFilter) {
                filter { it.filter>0 }
            } else this
        }
        if(list.isEmpty()) {
            UtImmortalTask.launchTask("emptyOfflineMode") {
                val context = BooApplication.instance
                showConfirmMessageBox(context.getString(R.string.offline_mode), context.getString(R.string.offline_empty_list))
                true
            }
        }

        val pos = getPlayPositionInfo(list)
        appViewModel.videoListSource = VideoListSource(list, lastUpdate).apply {
            setCurrentSource(pos.index, pos.position)
        }
    }

    fun refreshVideoList(settingIfNotServerAvailable:Boolean) {
        savePlayPositionInfo()
        if(AppViewModel.instance.offlineMode) {
            refreshVideoListFromLocal()
        } else {
            if(!serverAvailable && settingIfNotServerAvailable) {
                AppViewModel.instance.settingCommand.invoke()
            } else {
                refreshVideoListFromServer()
            }
        }
    }

    fun tryPlayAt(id: String) {
        val listSource = AppViewModel.instance.videoListSource ?: return
        val index = listSource.list.indexOfFirst { it.id == id }
        if(index>=0) {
            listSource.setCurrentSource(index, 0)
        }
    }


    /**
     * BooRemote で再生中の動画をBooTubeの動画リスト上で選択する
     */
    fun syncToServer() {
        val current = controlPanelModel.playerModel.currentSource.value ?: return
        val url = AppViewModel.url.current ?: return
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
        val url = AppViewModel.url.current ?: return
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
        val url = AppViewModel.url.register(urlParam) ?: return
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
        UtImmortalTask.launchTask ("SyncWithServer") {
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
            AppViewModel.instance.videoListSource?.list?.mapNotNull { it as? VideoItem }
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