package io.github.toyota32k.boodroid.data

import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.common.safeGetString
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.lib.themes.BuiltInThemeList
import io.github.toyota32k.lib.themes.ContrastLevel
import io.github.toyota32k.lib.themes.NightMode
import io.github.toyota32k.lib.themes.ThemeData
import io.github.toyota32k.lib.themes.ThemeDelegate
import io.github.toyota32k.logger.UtLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * BooDroid 側で記憶している BooTube サーバ情報。
 *
 * - [name]/[address] は従来どおり、UI の表示名と "host:port" 文字列。
 * - [serviceName] は mDNS-SD で発見されたエントリの Service Instance 名。非 null ならアクセス時に
 *   [BooTubeDiscovery.resolveOnce] で IP/port を再解決して DHCP 変動に追従する。
 * - [fingerprint] は HTTPS 証明書 SHA-256 ("AB:CD:..." 形式)。非 null なら OkHttp の
 *   CertificatePinner で照合する (mDNS 発見時の TXT レコード fp= から取り込む)。
 * - [isHttps] true なら接続時のスキームを https にする。
 *
 * 旧 JSON との互換のために 3 つの新フィールドはすべてデフォルト値を持つ。
 */
data class HostAddressEntity(
    val name: String,
    val address: String,
    val serviceName: String? = null,
    val fingerprint: String? = null,
    val isHttps: Boolean = false,
    /** mDNS TXT hostname= から取得 (例 "TOYOTA-PC.local")。表示用。接続には [address] を使う。 */
    val hostname: String? = null,
) {
    constructor(
        src: HostAddressEntity,
        name:String?=null,
        address: String?=null,
        serviceName: String? = null,
        fingerprint: String? = null,
        httpsOnly: Boolean? = null,
        /** mDNS TXT hostname= から取得 (例 "TOYOTA-PC.local")。表示用。接続には [address] を使う。 */
        hostname: String? = null,
        ) : this(name?:src.name, address?:src.address, serviceName?:src.serviceName, fingerprint?:src.fingerprint,httpsOnly?:src.isHttps, hostname?:src.hostname)
}

data class SettingsOnServer(val minRating:Int, val marks:List<Int>, val category:String) {
    companion object {
        val clean:SettingsOnServer = SettingsOnServer(0, emptyList(), "All")
    }
}

class Settings(
    val activeHostIndex:Int,
    val hostList: List<HostAddressEntity>,
    val sourceType: SourceType,

    val offlineMode:Boolean,
    val offlineFilter:Boolean,
    val preferAudioOnOfflineMode:Boolean,

    val showTitleOnScreen:Boolean,
    val slideInterval:Int = 5,  // sec
    val loopPlayback: Boolean,

    val settingsOnServer: Map<String,SettingsOnServer>,
    var themeData: ThemeData,
    var contrastLevel: ContrastLevel,
    var nightMode: NightMode,
    ) {
    // コピーコンストラクタ
    constructor(
        src:Settings,
        activeHostIndex:Int = src.activeHostIndex,
        hostList: List<HostAddressEntity> = src.hostList,
        sourceType: SourceType = src.sourceType,
        offlineMode:Boolean = src.offlineMode,
        offlineFilter:Boolean = src.offlineFilter,
        preferAudioOnOfflineMode:Boolean = src.preferAudioOnOfflineMode,
        showTitleOnScreen: Boolean = src.showTitleOnScreen,
        loopPlayback: Boolean = src.loopPlayback,
        slideInterval: Int = src.slideInterval,
        settingsOnServer: Map<String,SettingsOnServer> = src.settingsOnServer,
        themeData:ThemeData = src.themeData,
        contrastLevel: ContrastLevel = src.contrastLevel,
        nightMode: NightMode = src.nightMode,
    ) : this(activeHostIndex, hostList, sourceType, offlineMode, offlineFilter, preferAudioOnOfflineMode, showTitleOnScreen, slideInterval, loopPlayback, settingsOnServer, themeData, contrastLevel, nightMode)

    val activeHost:HostAddressEntity?
        get() = if(0<=activeHostIndex&&activeHostIndex<hostList.size) hostList.get(activeHostIndex) else null
    val isValid get() = !activeHost?.address.isNullOrBlank()
    val hostAddress:String?
        get() = activeHost?.let { host ->
            val addr = host.address
            return if (addr.contains(":")) addr
            else "${addr}:${if (host.isHttps) 3501 else 3500}"
        }
    val settingsOnActiveHost : SettingsOnServer
        get() = settingsOnServer[hostAddress ?: ""] ?: SettingsOnServer.clean

    private val restCommandBase:String get() = AppViewModel.instance.capability.value.root
    val baseUrl : String get() {
        val scheme = if (activeHost?.isHttps == true) "https" else "http"
        return "${scheme}://${hostAddress}${restCommandBase}"
    }

    fun save(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context) ?: throw IllegalStateException("no preference manager.")
        pref.edit {
            putInt(KEY_ACTIVE_HOST_INDEX, activeHostIndex)
            putString(KEY_HOST_ENTITY_LIST, serializeHosts(hostList))
            putInt(KEY_SOURCE_TYPE, sourceType.v)
            putBoolean(KEY_OFFLINE, offlineMode)
            putBoolean(KEY_OFFLINE_FILTER, offlineFilter)
            putBoolean(KEY_PREFER_AUDIO_ON_OFFLINE_MODE, preferAudioOnOfflineMode)
            putBoolean(KEY_SHOW_TITLE_ON_SCREEN, showTitleOnScreen)
            putBoolean(KEY_LOOP_PLAYBACK, loopPlayback)
            putInt(KEY_SLIDE_INTERVAL, slideInterval)
            putStringSet(KEY_SETTINGS_ON_SERVER, serializeSettingsOnServer(settingsOnServer))
            putString(KEY_THEME_NAME, themeData.label)
            putString(KEY_CONTRAST_LEVEL, contrastLevel.name)
            putString(KEY_NIGHT_MODE, nightMode.name)
        }
        AppViewModel.instance.settings = this
    }

    fun applyTheme(activity: FragmentActivity) {
        ThemeDelegate.defaultDelegate.applyTheme(activity, themeData, contrastLevel, nightMode, ThemeDelegate.ApplyMode.IMMEDIATE)
    }

    companion object {
        val logger = UtLog("Settings", BooApplication.logger)

        const val KEY_ACTIVE_HOST_INDEX = "activeHostIndex"
        const val KEY_HOST_ENTITY_LIST = "hostEntityList"
        const val KEY_SOURCE_TYPE = "sourceType"
        const val KEY_OFFLINE = "offline"
        const val KEY_OFFLINE_FILTER = "offlineFilter"
        const val KEY_PREFER_AUDIO_ON_OFFLINE_MODE = "preferAudioOnOfflineMode"
        const val KEY_SHOW_TITLE_ON_SCREEN = "showTitleOnScreen"
        const val KEY_LOOP_PLAYBACK = "loopPlayback"
        const val KEY_SLIDE_INTERVAL = "slideInterval"
        const val KEY_SETTINGS_ON_SERVER = "settingsOnServer"
        const val KEY_THEME_NAME = "themeName"
        const val KEY_CONTRAST_LEVEL = "contrastLevel"
        const val KEY_NIGHT_MODE = "nightMode"

        fun load(context: Context): Settings {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            return Settings(
                activeHostIndex = pref.getInt(KEY_ACTIVE_HOST_INDEX, -1),
                hostList =  deserializeHosts(pref.getString(KEY_HOST_ENTITY_LIST, null)),
                sourceType = SourceType.valueOf(pref.getInt(KEY_SOURCE_TYPE, -1)),
                offlineMode = pref.getBoolean(KEY_OFFLINE, false),
                offlineFilter = pref.getBoolean(KEY_OFFLINE_FILTER,false),
                preferAudioOnOfflineMode = pref.getBoolean(KEY_PREFER_AUDIO_ON_OFFLINE_MODE, false),
                showTitleOnScreen = pref.getBoolean(KEY_SHOW_TITLE_ON_SCREEN, false),
                loopPlayback = pref.getBoolean(KEY_LOOP_PLAYBACK, true),
                slideInterval = pref.getInt(KEY_SLIDE_INTERVAL, 5),
                settingsOnServer = deserializeSettingsOnServer(pref.getStringSet(KEY_SETTINGS_ON_SERVER, null)),
                themeData = BuiltInThemeList.themeOf(pref.getString(KEY_THEME_NAME,null) ?: "Default"),
                contrastLevel = ContrastLevel.parse(pref.getString(KEY_CONTRAST_LEVEL,null)?:"System") ?: ContrastLevel.System,
                nightMode = NightMode.valueOf(pref.getString(KEY_NIGHT_MODE,null) ?: "System")
            )
        }

        private fun serializeSettingsOnServer(settings:Map<String,SettingsOnServer>):Set<String> {
            return settings.map {
                JSONObject().apply {
                    put("k",it.key)
                    put("r",it.value.minRating)
                    put("m",it.value.marks.fold(JSONArray()) {ja, m->ja.put(m)})
                    put("c", it.value.category)
                }.toString()
            }.toSet()
        }
        private fun deserializeSettingsOnServer(jsonStrings: Set<String>?):Map<String,SettingsOnServer> {
            return try {
                if(null == jsonStrings) return emptyMap()
                return jsonStrings.fold(mutableMapOf<String,SettingsOnServer>()) { map, j ->
                    val json = JSONObject(j)
                    val k = json.getString("k")
                    val r = json.getInt("r")
                    val c = json.getString("c")
                    val m = json.getJSONArray("m").toIterable().map {it as Int }
                    map.apply { put(k, SettingsOnServer(r,m,c)) }
                }
            } catch(e:Throwable) {
                logger.stackTrace(e)
                emptyMap()
            }

        }

        private fun serializeHosts(list:List<HostAddressEntity>):String {
            return list.fold(JSONArray()) {json, v->
                json.put(JSONObject().apply  {
                    put("n", v.name)
                    put("a", v.address)
                    // 新フィールド (旧バージョンとの互換のため null/false は省略)
                    if (!v.serviceName.isNullOrEmpty()) put("svc", v.serviceName)
                    if (!v.fingerprint.isNullOrEmpty()) put("fp", v.fingerprint)
                    if (v.isHttps) put("https", true)
                    if (!v.hostname.isNullOrEmpty()) put("hn", v.hostname)
                })
                json
            }.toString().apply { logger.debug(this) }
        }

        private fun deserializeHosts(jsonString:String?):List<HostAddressEntity> {
            return try {
                val json = JSONArray(jsonString ?: return emptyList())
                json.toIterable().mapNotNull {
                    (it as? JSONObject)?.run {
                        HostAddressEntity(
                            name        = safeGetString("n"),
                            address     = safeGetString("a"),
                            serviceName = optString("svc", "").ifEmpty { null },
                            fingerprint = optString("fp", "").ifEmpty { null },
                            isHttps   = optBoolean("https", false),
                            hostname    = optString("hn", "").ifEmpty { null },
                        )
                    }
                }
            } catch(e:Throwable) {
                logger.stackTrace(e)
                emptyList()
            }
        }

        val empty:Settings = Settings(
            activeHostIndex = -1,
            hostList = listOf(),
            sourceType = SourceType.DB,
            offlineMode = false,
            offlineFilter = false,
            preferAudioOnOfflineMode = false,
            showTitleOnScreen = false,
            slideInterval = 5,
            settingsOnServer = emptyMap(),
            loopPlayback = true,
            themeData = BuiltInThemeList.themes[0],
            contrastLevel = ContrastLevel.System,
            nightMode = NightMode.System,
            )
    }
}