package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.logger.UtLog
import okhttp3.*
import okhttp3.coroutines.executeAsync
import org.json.JSONObject
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

object NetClient {
    val logger = UtLog("NET", BooApplication.logger)

    /**
     * BooDroid の全 HTTPS/HTTP 通信で共有する単一の OkHttpClient。
     *
     * TrustManager は [CompositeTrustManager] で「システム CA → fingerprint pin」のフォールバック構成。
     * これにより同一クライアントで:
     *   - 通常の HTTPS (Google 等) はそのまま
     *   - BooTube 自己署名は登録済 fingerprint と一致なら許可
     *
     * ExoPlayer 用の OkHttpDataSource からも本クライアントを再利用するため、
     * fingerprint 設定はこの一箇所で完結する (グローバル HttpsURLConnection 設定は不要)。
     */
    val client: OkHttpClient by lazy {
        val tm = CompositeTrustManager { CompositeTrustManager.fingerprintsFromSettings() }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(tm), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, tm)
            .hostnameVerifier(CompositeTrustManager.makeHostnameVerifier())
            .build()
    }

    suspend fun executeAsync(req: Request): Response {
        logger.debug("NetClient: ${req.url}")
        return client.newCall(req).executeAsync()
    }

    suspend fun executeAndGetJsonAsync(req: Request): JSONObject {
        return executeAsync(req).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            val body = res.body.use { it.string() }
            JSONObject(body)
        }
    }

    suspend fun executeAndGetStringAsync(req: Request): String {
        return executeAsync(req).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            res.body.use { it.string() }
        }
    }
}
