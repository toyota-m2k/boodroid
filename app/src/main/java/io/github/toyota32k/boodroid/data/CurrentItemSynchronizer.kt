package io.github.toyota32k.boodroid.data

import androidx.lifecycle.viewModelScope
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Client(BooRemote)/Server(BooTube)間で選択アイテムの同期を行う
 */
object CurrentItemSynchronizer {
    /**
     * BooRemote で再生中の動画をBooTubeの動画リスト上で選択する
     */
    fun syncTo() {
        val vm = AppViewModel.instance
        val current = vm.controlPanelModel.playerModel.currentSource.value ?: return
        val url = vm.settings.urlCurrentItem()
        val json = JSONObject()
            .put("id", current.id)
            .toString()
        val req = Request.Builder()
            .url(url)
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            NetClient.executeAsync(req).close()
        }
    }

    /**
     * BooTube上で選択（フォーカス）されている動画をBooRemote上で再生する。
     */
    fun syncFrom() {
        val vm = AppViewModel.instance
        val url = vm.settings.urlCurrentItem()
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = NetClient.executeAndGetJsonAsync(req)
                withContext(Dispatchers.Main) {
                    vm.tryPlayAt(json.getString("id"))
                }
            } catch(e:Throwable) {
                UtLogger.stackTrace(e)
            }
        }
    }

}