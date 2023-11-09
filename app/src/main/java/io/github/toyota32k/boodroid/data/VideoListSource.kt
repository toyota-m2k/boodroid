package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.video.common.IAmvSource
import okhttp3.Request
import org.json.JSONObject

data class VideoListSource(val list:List<IAmvSource>, val modifiedDate:Long) {
    suspend fun checkUpdate(date:Long) : Boolean {
        val url = AppViewModel.url.check(date)
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            val json = NetClient.executeAndGetJsonAsync(req)
            json.getString("update") == "1"
        } catch(e:Throwable) {
            UtLogger.stackTrace(e)
            return false
        }
    }

    companion object {
        suspend fun retrieve(date:Long=0L): VideoListSource? {
            if(!AppViewModel.instance.settings.isValid) return null
            if(!AppViewModel.instance.authentication.authentication()) return null
            val url = AppViewModel.url.list(date)
            val req = Request.Builder()
                .url(url)
                .get()
                .build()

            return try {
                val json = NetClient.executeAndGetJsonAsync(req)
                val lastUpdate = json.getString("date").toLong()
                val jsonList = json.getJSONArray("list")
                    ?: throw IllegalStateException("Server Response Null List.")
                VideoListSource( jsonList.toIterable().map { j -> VideoItem(j as JSONObject) }, lastUpdate )      // todo: 一発のクエリで完結させたい
            } catch (e: Throwable) {
                UtLogger.stackTrace(e)
                return null
            }
        }
    }
}