package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.boodroid.viewmodel.MainViewModel
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class Authentication {
    var authToken:String? = null
    suspend fun authenticate() : Boolean {
        if(!AppViewModel.instance.needAuth) return true
        withContext(Dispatchers.IO) {
            val url = AppViewModel.instance.settings.authUrl()
            val req = Request.Builder()
                .url(url)
                .get()
                .build()
            try {
                val j = NetClient.executeAndGetJsonAsync(req)
                j.optString("challenge")
            } catch (e: Throwable) {
                UtLogger.stackTrace(e)
                false
            }
        }

    }
}