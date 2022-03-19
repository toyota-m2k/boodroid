package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.video.common.IAmvSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class VideoListSource(val list:List<IAmvSource>, val modifiedDate:Long) {
    suspend fun checkUpdate(date:Long) : Boolean {
        val url = AppViewModel.instance.settings.checkUrl(date)
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            val json = NetClient.executeAsync(req).use { res ->
                if (res.code == 200) {
                    val body = withContext(Dispatchers.IO) {
                        res.body?.string()
                    } ?: throw IllegalStateException("Server Response No Data.")
                    JSONObject(body)
                } else {
                    throw IllegalStateException("Server Response Error (${res.code})")
                }
            }
            json.getString("update") == "1"
        } catch(e:Throwable) {
            UtLogger.stackTrace(e)
            return false
        }
    }

    companion object {
        suspend fun retrieve(date:Long=0L): VideoListSource? {
            if(!AppViewModel.instance.settings.isValid) return null
            val url = AppViewModel.instance.settings.listUrl(date)
            val req = Request.Builder()
                .url(url)
                .get()
                .build()

            return try {
                val json = NetClient.executeAsync(req).use { res ->
                    if (res.code == 200) {
                        val body = withContext(Dispatchers.IO) {
                            res.body?.string()
                        } ?: throw IllegalStateException("Server Response No Data.")
                        JSONObject(body)
                    } else {
                        throw IllegalStateException("Server Response Error (${res.code})")
                    }
                }
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