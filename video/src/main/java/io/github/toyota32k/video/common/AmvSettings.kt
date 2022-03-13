package io.github.toyota32k.video.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.toyota32k.player.cache.AmvCacheManager
import io.github.toyota32k.utils.UtLog
import okhttp3.OkHttpClient
import java.io.File

interface IAmvHttpClientSource {
    fun getHttpClient():OkHttpClient
}

object AmvSettings {
    val logger = UtLog("a-m-v", null, "com.michael.video.")

    private var initialized: Boolean = false
//    private var allowPictureInPictureByCaller = false
    lateinit var workDirectory: File
    var maxBitRate = 705 // k bps
        private set
    private var allowPictureInPicture = false
    private var httpClientSource: IAmvHttpClientSource? = null

    /**
     * 初期化. Application.onCreate()から呼び出す。
     *
     * @param context   システムがPinPをサポートしているかをチェックするためのcontext (ApplicationContextで可）
     * @param cacheRootPath ダウンロードした動画のキャッシュ用ディレクトリ
     * @param bitrate  貼り付ける動画の最大ビットレート
     * @param allowPinP PinPを許可するか(VfEditionDef の定義）。。。実際にPinPが使えるかどうかは、OSのバージョン＋デバイスの対応状況によって変わる。
     * @param　httpClientSource　videoライブラリと本体とで、httpClient（の設定やセッションなど）を共用するためのi/f
     */
    @JvmStatic
    fun initialize(context: Context, cacheRootPath: File, bitrate:Int, allowPinP:Boolean, httpClientSource: IAmvHttpClientSource?) {
        if (initialized) {
            return
        }
        initialized = true
        maxBitRate = bitrate
        allowPictureInPicture = allowPinP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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

    fun isPinPAvailable(context: Context) : Boolean {
        return allowPictureInPicture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
}