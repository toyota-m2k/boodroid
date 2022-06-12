package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.ytremote.data.CategoryInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class ServerCapability(
    val serverName:String,
    val version:Int,                // protocol version
    val hasCategory:Boolean,
    val hasRating:Boolean,
    val hasMark:Boolean,
    val acceptRequest:Boolean,
    val hasView:Boolean,
) {
    constructor(j:JSONObject) : this(
        j.optString("serverName", "unknown"),
        j.optInt("version", 0),
        j.optBoolean("category", false),
        j.optBoolean("rating", false),
        j.optBoolean("mask", false),
        j.optBoolean("acceptRequest", false),
        j.optBoolean("hasView", false),
    )

    companion object {
        val empty = ServerCapability("unknown", 0, false, false, false, false, false)
        suspend fun get():ServerCapability {
            if (!AppViewModel.instance.settings.isValid) return empty
            return withContext(Dispatchers.IO) {
                val url = AppViewModel.instance.settings.urlCapability()
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                try {
                    ServerCapability(NetClient.executeAndGetJsonAsync(req))
                } catch (e: Throwable) {
                    UtLogger.stackTrace(e)
                    empty
                }
            }
        }
    }
}