package io.github.toyota32k.boodroid.data

import android.net.Uri

/**
 * `bootube://<host>:<port>?fp=...&name=...&svc=...&https=1` 形式の
 * ペアリング URI を [HostAddressEntity] に変換するためのユーティリティ。
 *
 * BooTube 側の PairingQrDialog が同じ形式の URI を QR にエンコードしている。
 */
object PairingUri {
    const val SCHEME = "bootube"

    data class Pairing(
        val host: String,
        val port: Int,
        val fingerprint: String?,
        val name: String,
        val serviceName: String?,
        val httpsOnly: Boolean,
    ) {
        fun toEntity(): HostAddressEntity = HostAddressEntity(
            name = "$serviceName@$name",
            address = "$host:$port",
            serviceName = serviceName,
            fingerprint = fingerprint,
            isHttps = httpsOnly,
        )
    }

    /** URI を解析。スキーム不一致やホスト欠落なら null。 */
    fun parse(uri: Uri): Pairing? {
        if (uri.scheme != SCHEME) return null
        val host = uri.host ?: return null
        // Uri.getPort() は未指定なら -1 を返す。HTTPS 既定 3501 にフォールバック。
        val port = uri.port.takeIf { it > 0 } ?: 3501
        val httpsOnly = uri.getQueryParameter("https") == "1"
        return Pairing(
            host = host,
            port = port,
            fingerprint = uri.getQueryParameter("fp")?.takeIf { it.isNotEmpty() },
            name = uri.getQueryParameter("name")?.takeIf { it.isNotEmpty() } ?: host,
            serviceName = uri.getQueryParameter("svc")?.takeIf { it.isNotEmpty() },
            httpsOnly = httpsOnly,
        )
    }
}
