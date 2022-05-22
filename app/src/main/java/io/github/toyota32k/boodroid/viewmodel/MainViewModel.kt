package io.github.toyota32k.boodroid.viewmodel

import androidx.lifecycle.*
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.MainActivity
import io.github.toyota32k.boodroid.data.LastPlayInfo
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.VideoListSource
import io.github.toyota32k.utils.UtLogger
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

    var prepared = false
    val binder = Binder()

    private fun prepare(@Suppress("UNUSED_PARAMETER") owner:MainActivity):MainViewModel {
        if(!prepared) {
            prepared = true
            controlPanelModel = appViewModel.controlPanelModelSource.fetch()
            binder.register(
                appViewModel.refreshCommand.bindForever { refreshVideoList() },
                appViewModel.syncFromServerCommand.bindForever { syncFromServer() },
                appViewModel.syncToServerCommand.bindForever { syncToServer() },
            )
            refreshVideoList()
        }
        return this
    }

    override fun onCleared() {
        super.onCleared()
        if(prepared) {
            prepared = false
            binder.reset()
            AppViewModel.instance.controlPanelModelSource.release(controlPanelModel)
        }
    }

    val loading:MutableStateFlow<Boolean> get() = appViewModel.loading
    var lastUpdate
        get() = appViewModel.lastUpdate
        set(v) { appViewModel.lastUpdate = v }


    private fun refreshVideoList() {
        AppViewModel.logger.debug()
        viewModelScope.launch {
            if(loading.value == true) {
                AppViewModel.logger.error("busy.")
                return@launch
            }
            val src = try {
                loading.value = true
                withContext(Dispatchers.Default) {
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
                AppViewModel.logger.debug("list empty")
                lastUpdate = 0L
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

}