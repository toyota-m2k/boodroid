package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.data.*
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.model.PlayerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class AppViewModel: ViewModel() {
    companion object {
        val logger = BooApplication.logger
        val instance: AppViewModel
            get() = ViewModelProvider(BooApplication.instance, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java].prepare()
    }

    lateinit var controlPanelModel:ControlPanelModel
    val playerModel:PlayerModel get() = controlPanelModel.playerModel

    var settings: Settings = Settings.empty
        set(v) {
            if(v!=field) {
                field = v
                refreshVideoList()
            }
        }

    // 通信中フラグ
    private val loading = MutableStateFlow<Boolean>(false)
    private var lastUpdate : Long = 0L

    private fun prepare():AppViewModel {
        if(!this::controlPanelModel.isInitialized) {
            controlPanelModel = ControlPanelModel.create(BooApplication.instance.applicationContext)
            settings = Settings.load(BooApplication.instance)
        }
        return this
    }

    private fun refreshVideoList() {
        logger.debug()
        viewModelScope.launch {
            if(loading.value == true) {
                logger.error("busy.")
                return@launch
            }
            val src = try {
                loading.value = true
                withContext(Dispatchers.Default) {
                    VideoListSource.retrieve()
                }
            } finally {
                loading.value = false
            }

            lastUpdate = src?.modifiedDate ?: 0L
            if(src!=null) {
                logger.debug("list.count=${src.list.size}")
                var index = -1
                var position = 0L
                // 再生中なら、同じ場所から再開
                val current = playerModel.currentSource.value
                if(current!=null) {
                    index = src.list.indexOfFirst { it.id == current.id }
                    position = playerModel.playerSeekPosition.value
                }
                // 再生中でなければ、前回の再生位置から復元
                if(index<0) {
                    val lpi = LastPlayInfo.get(BooApplication.instance)
                    if (lpi != null) {
                        index = src.list.indexOfFirst { lpi.id == it.id }
                        position = if (index >= 0) lpi.position else 0L
                    }
                }
                playerModel.setSources(src.list, index, position)



//                if(updateTimerTask==null) {
//                    updateTimerTask = Timer().run {
//                        schedule(60000,60000) {
//                            updateVideoList()
//                        }
//                    }
//                }
            } else {
                logger.debug("list empty")
                playerModel.setSources(emptyList())
//                updateTimerTask?.cancel()
//                updateTimerTask = null
            }
        }
    }

    fun registerUrl(rawUrl:String) {
        val urlParam = rawUrl.split("\r", "\n", " ", "\t").firstOrNull { it.isNotBlank() } ?: return
        val url = settings.urlToRegister(urlParam)
        CoroutineScope(Dispatchers.IO).launch {
            val req = Request.Builder()
                .url(url)
                .get()
                .build()
            NetClient.executeAsync(req)
        }
    }

    fun tryPlayAt(id: String) {
        val index = playerModel.videoSources.indexOfFirst { it.id == id }
        playerModel.playAt(index)
    }

    override fun onCleared() {
        super.onCleared()
        controlPanelModel.close()
    }

    val settingCommand = Command {
        UtImmortalSimpleTask.run("settings") {
            this.showDialog(taskName) { SettingsDialog() }
            true
        }
    }
    val refreshCommand = Command {
        refreshVideoList()
    }

    val syncToServerCommand = Command {
        CurrentItemSynchronizer.syncTo()
    }

    val syncFromServerCommand = Command {
        CurrentItemSynchronizer.syncFrom()
    }
}