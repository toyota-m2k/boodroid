package io.github.toyota32k.boodroid.data

import io.github.toyota32k.utils.UtLogger
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object NetClient {
    private val motherClient : OkHttpClient = OkHttpClient.Builder().build()

//    fun newCall(req: Request):Call {
//        return motherClient.newCall(req)
//    }

    suspend fun executeAsync(req:Request):Response {
        UtLogger.debug("NetClient: ${req.url}")
        return motherClient.newCall(req).executeAsync()
    }

    suspend fun executeAndGetJsonAsync(req:Request):JSONObject {
        return executeAsync(req).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            val body = res.body?.use { it.string() } ?: throw IllegalStateException("Server Response No Data.")
            JSONObject(body)
        }
    }
    suspend fun executeAndGetStringAsync(req:Request):String? {
        return executeAsync(req).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            res.body?.use { it.string() }
        }
    }

    /**
     * Coroutineを利用し、スレッドをブロックしないで同期的な通信を可能にする拡張メソッド
     * OkHttpのnewCall().execute()を置き換えるだけで使える。
     */
    private suspend fun Call.executeAsync() : Response {
        return suspendCoroutine {cont ->
            try {
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        UtLogger.error("NetClient: error: ${e.localizedMessage}")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        UtLogger.debug("NetClient: completed (${response.code}): ${call.request().url}")
                        cont.resume(response)
                    }
                })
            } catch(e:Throwable) {
                UtLogger.error("NetClient: exception: ${e.localizedMessage}")
                cont.resumeWithException(e)
            }
        }
    }

}