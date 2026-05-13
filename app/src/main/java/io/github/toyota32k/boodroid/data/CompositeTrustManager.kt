package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

interface IFingerprintSource {
    val fingerprints: Set<String>
    val pinnedHosts: Set<String>    // fingerprintを持っているHost一覧（ポート番号なし）
}

class FingerprintSourceImpl(private val getHostList:()->Collection<HostAddressEntity>) : IFingerprintSource {
    private val pinnedEntities get() = getHostList().filter { !it.fingerprint.isNullOrBlank() }
    override val fingerprints: Set<String>
        get() = pinnedEntities.mapNotNull { it.fingerprint }.toSet()
    override val pinnedHosts: Set<String>
        get() = pinnedEntities.map { it.address.substringBefore(":") }.toSet()
}

/**
 * 「システム CA で検証 → ダメなら登録済 fingerprint との一致を確認」の合成 TrustManager。
 *
 * - 通常のサイト (Google などシステム CA で検証通る相手) は素通り。
 * - 自己署名サーバ (BooTube) は登録済の fingerprint との完全一致のみ許可。
 * - それ以外の自己署名サーバはすべて拒否される。
 *
 * ペアリング情報 (fingerprint 一覧) は [AppViewModel.instance.settings.hostList] から動的に
 * 取得するので、設定変更後 (新ホスト追加・削除) も即時反映される。
 */
class CompositeTrustManager : X509TrustManager {
    private val systemTm: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // クライアント認証は使わない
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain == null || chain.isEmpty()) {
            throw CertificateException("Empty certificate chain")
        }
        // 1) システム CA で検証
        try {
            systemTm.checkServerTrusted(chain, authType)
            return
        } catch (_: CertificateException) {
            // 2) fingerprint pin にフォールバック
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
        val actualHex = sha.joinToString("") { "%02X".format(it) }
        val pinned = fingerprintSource.fingerprints
            .map { it.replace(":", "").replace("-", "").uppercase() }
            .toSet()
        if (actualHex in pinned) return
        throw CertificateException(
            "Untrusted self-signed cert (fp=$actualHex, ${pinned.size} pinned)"
        )
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTm.acceptedIssuers

    companion object {
        /** Settings から現在 pin されている fingerprint の集合を取り出す。 */
//        fun fingerprintsFromSettings(): Set<String> = runCatching {
//            AppViewModel.instance.fingerprintSource.fingerprints
//        }.getOrDefault(emptySet())
//
//        /** Settings から現在 pin されているホスト名 (ポートなし) の集合。 */
//        fun pinnedHostsFromSettings(): Set<String> = runCatching {
//            AppViewModel.instance.settings.hostList
//                .filter { !it.fingerprint.isNullOrEmpty() }
//                .map { it.address.substringBefore(":") }
//                .toSet()
//        }.getOrDefault(emptySet())

        private val fingerprintSource get() = AppViewModel.instance.fingerprintSource

        /**
         * pin 済みホストはホスト名検証を省く HostnameVerifier。
         * fingerprint で証明書全体が pin されているのでホスト名照合は冗長で、
         * 動的な IP/.local 名と SAN がずれた場合を救う目的。
         */
        fun makeHostnameVerifier(): HostnameVerifier {
            val default = HttpsURLConnection.getDefaultHostnameVerifier()
            return HostnameVerifier { hostname: String, session: SSLSession ->
                if (hostname in fingerprintSource.pinnedHosts) true
                else default.verify(hostname, session)
            }
        }
    }
}
