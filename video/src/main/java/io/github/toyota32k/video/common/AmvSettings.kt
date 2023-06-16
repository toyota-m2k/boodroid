package io.github.toyota32k.video.common

import io.github.toyota32k.video.cache.AmvCacheManager
import io.github.toyota32k.utils.UtLog
import okhttp3.OkHttpClient
import java.io.File

interface IAmvHttpClientSource {
    fun getHttpClient():OkHttpClient
}

object AmvSettings {
    var logger:UtLog = UtLog("AMV", null, "io.github.toyota32k.")
        set(v) { field = UtLog("AMV", v) }

    private var initialized: Boolean = false
    lateinit var workDirectory: File
    private var httpClientSource: IAmvHttpClientSource? = null

    /**
     * 初期化. Application.onCreate()から呼び出す。
     *
     * @param cacheRootPath ダウンロードした動画のキャッシュ用ディレクトリ
     * @param　httpClientSource　videoライブラリと本体とで、httpClient（の設定やセッションなど）を共用するためのi/f
     */
    @JvmStatic
    fun initialize(cacheRootPath: File, httpClientSource: IAmvHttpClientSource?) {
        if (initialized) {
            return
        }
        initialized = true
        AmvSettings.httpClientSource = httpClientSource
        val videoCache = File(cacheRootPath, ".video-cache")
        AmvCacheManager.initialize(videoCache)

        workDirectory = File(cacheRootPath, ".video-tmp")
        if (!workDirectory.exists()) {
            workDirectory.mkdir()
        } else {
            val files = workDirectory.listFiles() ?: emptyArray()
            for (file in files)
                if (null!=file && !file.isDirectory) {
                    file.delete()
                }
        }
    }

    val httpClient: OkHttpClient
        get() = httpClientSource?.getHttpClient() ?: OkHttpClient()
}