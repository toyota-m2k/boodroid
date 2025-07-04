package io.github.toyota32k.boodroid.data

import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.safeGetString
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.logger.UtLog
import org.json.JSONArray
import org.json.JSONObject

//enum class ThemeSetting(val v:Int, @IdRes val id:Int, val mode:Int) {
//    SYSTEM(0, R.id.chk_theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
//    LIGHT(1,R.id.chk_theme_light, AppCompatDelegate.MODE_NIGHT_NO),
//    DARK(2,R.id.chk_theme_dark, AppCompatDelegate.MODE_NIGHT_YES),
//    ;
//
//    private class IDResolver : IIDValueResolver<ThemeSetting> {
//        override fun id2value(@IdRes id: Int):ThemeSetting  = ThemeSetting.id2value(id)
//        override fun value2id(v: ThemeSetting): Int = v.id
//    }
//    companion object {
//        fun id2value(@IdRes id: Int, def: ThemeSetting = ThemeSetting.SYSTEM): ThemeSetting {
//            return ThemeSetting.values().find { it.id == id } ?: def
//        }
//        fun valueOf(v: Int, def: ThemeSetting = ThemeSetting.SYSTEM): ThemeSetting {
//            return ThemeSetting.values().find { it.v == v } ?: def
//        }
//        val idResolver: IIDValueResolver<ThemeSetting> by lazy { IDResolver() }
//    }
//}
//
//enum class ColorVariation(val v:Int, @IdRes val id:Int, @StyleRes val themeId:Int) {
//    PINK(0, R.id.chk_color_pink, R.style.Theme_Boodroid_Main),
//    GREEN(1,R.id.chk_color_green, R.style.Theme_Boodroid_Alt01),
//    BLUE(2,R.id.chk_color_blue, R.style.Theme_Boodroid_alt02),
//    PURPLE(3,R.id.chk_color_purple, R.style.Theme_Boodroid_alt03),
//    ;
//
//    private class IDResolver : IIDValueResolver<ColorVariation> {
//        override fun id2value(@IdRes id: Int):ColorVariation  = Companion.id2value(id)
//        override fun value2id(v: ColorVariation): Int = v.id
//    }
//    companion object {
//        fun id2value(@IdRes id: Int, def: ColorVariation = ColorVariation.PINK): ColorVariation {
//            return entries.find { it.id == id } ?: def
//        }
//        fun valueOf(v: Int, def: ColorVariation = ColorVariation.PINK): ColorVariation {
//            return entries.find { it.v == v } ?: def
//        }
//        val idResolver: IIDValueResolver<ColorVariation> by lazy { IDResolver() }
//    }
//}

data class HostAddressEntity(val name:String, val address:String)

data class SettingsOnServer(val minRating:Int, val marks:List<Int>, val category:String) {
    companion object {
        val clean:SettingsOnServer = SettingsOnServer(0, emptyList(), "All")
    }
}

class Settings(
//    val activeHost: HostAddressEntity?,
    val activeHostIndex:Int,
    val hostList: List<HostAddressEntity>,
    val sourceType: SourceType,
//    val theme:ThemeSetting,
//    val useDynamicColor: Boolean,
//    val colorVariation: ColorVariation,

    val offlineMode:Boolean,
    val offlineFilter:Boolean,

    val showTitleOnScreen:Boolean,
    val slideInterval:Int = 5,  // sec

    val settingsOnServer: Map<String,SettingsOnServer>,
    var themeInfo:ThemeInfo,
    var contrastLevel: ThemeSelector.ContrastLevel,
    var nightMode: ThemeSelector.NightMode,
    ) {
    // コピーコンストラクタ
    constructor(
        src:Settings,
        activeHostIndex:Int = src.activeHostIndex,
        hostList: List<HostAddressEntity> = src.hostList,
        sourceType: SourceType = src.sourceType,
//        theme:ThemeSetting = src.theme,
//        useDynamicColor: Boolean = src.useDynamicColor,
//        colorVariation: ColorVariation = src.colorVariation,
        offlineMode:Boolean = src.offlineMode,
        offlineFilter:Boolean = src.offlineFilter,
        showTitleOnScreen: Boolean = src.showTitleOnScreen,
        slideInterval: Int = src.slideInterval,
        settingsOnServer: Map<String,SettingsOnServer> = src.settingsOnServer,
        themeInfo:ThemeInfo = src.themeInfo,
        contrastLevel: ThemeSelector.ContrastLevel = src.contrastLevel,
        nightMode: ThemeSelector.NightMode = src.nightMode,
    ) : this(activeHostIndex, hostList, sourceType, offlineMode, offlineFilter, showTitleOnScreen, slideInterval, settingsOnServer, themeInfo, contrastLevel, nightMode)

    private val activeHost:HostAddressEntity?
        get() = if(0<=activeHostIndex&&activeHostIndex<hostList.size) hostList.get(activeHostIndex) else null
    val isValid get() = !activeHost?.address.isNullOrBlank()
    val hostAddress:String?
        get() = activeHost?.address?.let { host ->
            return if(host.contains(":")) {
                host
            } else {
                "${host}:3500"
            }
        }
    val settingsOnActiveHost : SettingsOnServer
        get() = settingsOnServer[hostAddress ?: ""] ?: SettingsOnServer.clean

    private val restCommandBase:String get() = AppViewModel.instance.capability.value.root
    val baseUrl : String get() = "http://${hostAddress}${restCommandBase}"

//    val themeId: Int get() = if(useDynamicColor) R.style.Theme_Boodroid_Dynamic else colorVariation.themeId

    fun save(context: Context) {
//        UtLogger.assert(isValid, "invalid settings")
//        logger.debug("Settings:saving $this")
        val pref = PreferenceManager.getDefaultSharedPreferences(context) ?: throw IllegalStateException("no preference manager.")
        pref.edit {
//            if(activeHost!=null) putString(KEY_ACTIVE_HOST, activeHost) else remove(KEY_ACTIVE_HOST)
            putInt(KEY_ACTIVE_HOST_INDEX, activeHostIndex)
            putString(KEY_HOST_ENTITY_LIST, serializeHosts(hostList))
            putInt(KEY_SOURCE_TYPE, sourceType.v)
            putBoolean(KEY_OFFLINE, offlineMode)
            putBoolean(KEY_OFFLINE_FILTER, offlineFilter)
            putBoolean(KEY_SHOW_TITLE_ON_SCREEN, showTitleOnScreen)
            putInt(KEY_SLIDE_INTERVAL, slideInterval)
            putStringSet(KEY_SETTINGS_ON_SERVER, serializeSettingsOnServer(settingsOnServer))
            putString(KEY_THEME_NAME, themeInfo.label)
            putString(KEY_CONTRAST_LEVEL, contrastLevel.name)
            putString(KEY_NIGHT_MODE, nightMode.name)
        }
        AppViewModel.instance.settings = this
    }

    fun applyTheme(activity: FragmentActivity) {
        ThemeSelector.defaultInstance.applyNightMode(nightMode)
        ThemeSelector.defaultInstance.applyTheme(themeInfo, contrastLevel, activity)
    }

    object ThemeList: IThemeList {
        override val themes: List<ThemeInfo> = listOf(
            ThemeInfo("Default", R.style.DefaultTheme, null, null),
            ThemeInfo("Cherry", R.style.CherryTheme, R.style.CherryTheme_MediumContrast, R.style.CherryTheme_HighContrast),
            ThemeInfo("Grape", R.style.GrapeTheme, R.style.GrapeTheme_MediumContrast, R.style.GrapeTheme_HighContrast),
            ThemeInfo("Blueberry", R.style.BlueberryTheme, R.style.BlueberryTheme_MediumContrast, R.style.BlueberryTheme_HighContrast),
            ThemeInfo("Melon", R.style.MelonTheme, R.style.MelonTheme_MediumContrast, R.style.MelonTheme_HighContrast),
            ThemeInfo("Orange", R.style.OrangeTheme, R.style.OrangeTheme_MediumContrast, R.style.OrangeTheme_HighContrast),
            ThemeInfo("Soda", R.style.SodaTheme, R.style.SodaTheme_MediumContrast, R.style.SodaTheme_HighContrast),
        )
    }

    companion object {
        val logger = UtLog("Settings", BooApplication.logger)

        const val KEY_ACTIVE_HOST_INDEX = "activeHostIndex"
        const val KEY_HOST_ENTITY_LIST = "hostEntityList"
        const val KEY_SOURCE_TYPE = "sourceType"
        const val KEY_OFFLINE = "offline"
        const val KEY_OFFLINE_FILTER = "offlineFilter"
        const val KEY_SHOW_TITLE_ON_SCREEN = "showTitleOnScreen"
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
                showTitleOnScreen = pref.getBoolean(KEY_SHOW_TITLE_ON_SCREEN, false),
                slideInterval = pref.getInt(KEY_SLIDE_INTERVAL, 5),
                settingsOnServer = deserializeSettingsOnServer(pref.getStringSet(KEY_SETTINGS_ON_SERVER, null)),
                themeInfo = ThemeList.themeOf(pref.getString(KEY_THEME_NAME,null) ?: "Default"),
                contrastLevel = ThemeSelector.ContrastLevel.parse(pref.getString(KEY_CONTRAST_LEVEL,null)?:"System") ?: ThemeSelector.ContrastLevel.System,
                nightMode = ThemeSelector.NightMode.valueOf(pref.getString(KEY_NIGHT_MODE,null) ?: "System")
            )
//                .apply {logger.debug("Settings:Loaded $this")}
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
                            safeGetString("n"),
                            safeGetString("a")
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
//            theme = ThemeSetting.SYSTEM,
//            useDynamicColor = false,
//            colorVariation =  ColorVariation.PINK,
            offlineMode = false,
            offlineFilter = false,
            showTitleOnScreen = false,
            slideInterval = 5,
            settingsOnServer = emptyMap(),
            themeInfo = ThemeList.themes[0],
            contrastLevel = ThemeSelector.ContrastLevel.System,
            nightMode = ThemeSelector.NightMode.System,
            )
    }
}