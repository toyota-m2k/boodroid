package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.logger.UtLog
import okhttp3.*
import okhttp3.coroutines.executeAsync
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        val tm = CompositeTrustManager()
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
        return try {
            client.newCall(req).executeAsync()
        } catch (e: IOException) {
            // 接続段階のネットワークエラーで、active host が mDNS 由来ならば、現在 IP を再解決して
            // 1 度だけリトライする。これで「BooTube 起動中に PC の IP が変わった」シナリオを救う。
            if (!isReResolvableFailure(e)) throw e
            val rebuilt = tryRebuildWithFreshAddress(req)
            if (rebuilt == null) {
                throw e
            }
            logger.warn("Connection failed (${e.javaClass.simpleName}); retrying with refreshed address ${rebuilt.url}")
            client.newCall(rebuilt).executeAsync()
        }
    }

    /** ネットワーク層レベルの失敗 (≒ IP/port が変わって繋がらない可能性) のみリトライ対象とする。 */
    private fun isReResolvableFailure(e: IOException): Boolean = when (e) {
        is UnknownHostException, is ConnectException,
        is SocketTimeoutException, is NoRouteToHostException -> true
        else -> false
    }

    /**
     * active host が mDNS で発見されたエントリ (`serviceName != null`) の場合、現在の IP を再解決し、
     * 変わっていれば設定を更新したうえでリクエスト URL を新 IP に書き換えて返す。
     * 変わっていない、もしくは解決失敗の場合は null。
     */
    private suspend fun tryRebuildWithFreshAddress(req: Request): Request? {
        val ctx = BooApplication.instance.applicationContext
        val cur = try { AppViewModel.instance.settings } catch (_: Throwable) { return null }
        val active = cur.activeHost ?: return null
        val svc = active.serviceName ?: return null
        val resolved = BooTubeDiscovery.resolveOnce(ctx, svc) ?: return null
        val newAddr = "${resolved.host}:${resolved.port}"
        if (newAddr == active.address) return null

        val updated = active.copy(
            address = newAddr,
            fingerprint = resolved.fingerprint ?: active.fingerprint,
            isHttps = resolved.isHttps || active.isHttps,
            hostname = resolved.hostname ?: active.hostname,
        )
        val newList = cur.hostList.map { if (it.address == active.address) updated else it }
        val idx = newList.indexOfFirst { it.address == updated.address }
        val newSettings = Settings(cur, hostList = newList, activeHostIndex = idx)
        newSettings.save(ctx)

        val newUrl = req.url.newBuilder()
            .host(resolved.host)
            .port(resolved.port)
            .build()
        return req.newBuilder().url(newUrl).build()
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
