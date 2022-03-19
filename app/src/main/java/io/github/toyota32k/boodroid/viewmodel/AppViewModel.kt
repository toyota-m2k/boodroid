package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.data.Settings
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.boodroid.dialog.SettingsDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.video.model.ControlPanelModel
import io.github.toyota32k.video.model.PlayerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                var index = 0
                var position = 0L
                val lpi = LastPlayInfo.get(BooApplication.instance)
                if(lpi!=null) {
                    index = src.list.indexOfFirst { lpi.id == it.id }
                    position = if(index>=0) lpi.position else 0L
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
}