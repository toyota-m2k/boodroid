package io.github.toyota32k.boodroid.data

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.toyota32k.bindit.IIDValueResolver
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R
import io.github.toyota32k.boodroid.common.safeGetString
import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtLogger
import org.json.JSONArray
import org.json.JSONObject
import java.lang.IllegalStateException

enum class ThemeSetting(val v:Int, @IdRes val id:Int, val mode:Int) {
    SYSTEM(0, R.id.chk_theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(1,R.id.chk_theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(2,R.id.chk_theme_dark, AppCompatDelegate.MODE_NIGHT_YES),
    ;

    private class IDResolver : IIDValueResolver<ThemeSetting> {
        override fun id2value(@IdRes id: Int):ThemeSetting  = ThemeSetting.id2value(id)
        override fun value2id(v: ThemeSetting): Int = v.id
    }
    companion object {
        fun id2value(@IdRes id: Int, def: ThemeSetting = ThemeSetting.SYSTEM): ThemeSetting {
            return ThemeSetting.values().find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: ThemeSetting = ThemeSetting.SYSTEM): ThemeSetting {
            return ThemeSetting.values().find { it.v == v } ?: def
        }
        val idResolver: IIDValueResolver<ThemeSetting> by lazy { IDResolver() }
    }
}

enum class ColorVariation(val v:Int, @IdRes val id:Int, @StyleRes val themeId:Int) {
    PINK(0, R.id.chk_color_pink, R.style.Theme_Boodroid_Main),
    GREEN(1,R.id.chk_color_green, R.style.Theme_Boodroid_Alt01),
    BLUE(2,R.id.chk_color_blue, R.style.Theme_Boodroid_alt02),
    PURPLE(3,R.id.chk_color_purple, R.style.Theme_Boodroid_alt03),
    ;

    private class IDResolver : IIDValueResolver<ColorVariation> {
        override fun id2value(@IdRes id: Int):ColorVariation  = Companion.id2value(id)
        override fun value2id(v: ColorVariation): Int = v.id
    }
    companion object {
        fun id2value(@IdRes id: Int, def: ColorVariation = ColorVariation.PINK): ColorVariation {
            return ColorVariation.values().find { it.id == id } ?: def
        }
        fun valueOf(v: Int, def: ColorVariation = ColorVariation.PINK): ColorVariation {
            return values().find { it.v == v } ?: def
        }
        val idResolver: IIDValueResolver<ColorVariation> by lazy { IDResolver() }
    }
}

data class HostAddressEntity(val name:String, val address:String)

class Settings(
//    val activeHost: HostAddressEntity?,
    val activeHostIndex:Int,
    val hostList: List<HostAddressEntity>,
    val sourceType: SourceType,
    val rating:Rating,
    val theme:ThemeSetting,
    val colorVariation: ColorVariation,
    val marks:List<Mark>,
    val category:String?) {

    val activeHost:HostAddressEntity?
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
    @Suppress("SpellCheckingInspection")
    val baseUrl : String get() = "http://${hostAddress}/ytplayer/"


    fun listUrl(date:Long):String {
        return VideoItemFilter(this).urlWithQueryString(date)
    }

    fun checkUrl(date:Long):String {
        return baseUrl + "check?date=${date}"
    }

    fun videoUrl(id:String):String {
        return baseUrl + "video?id=${id}"
    }

    fun urlToRegister(url:String):String {
        return baseUrl + "register?url=${url}"
    }

    fun urlToListCategories(): String {
        return baseUrl + "category"
    }
    fun urlCurrentItem():String {
        return baseUrl + "current"
    }
    fun urlChapters(id:String):String {
        return baseUrl + "chapter?id=$id"
    }

    fun urlReputation():String {
        return baseUrl + "reputation"
    }

    fun save(context: Context) {
        UtLogger.assert(isValid, "invalid settings")
//        logger.debug("Settings:saving $this")
        val pref = PreferenceManager.getDefaultSharedPreferences(context) ?: throw IllegalStateException("no preference manager.")
        pref.edit {
//            if(activeHost!=null) putString(KEY_ACTIVE_HOST, activeHost) else remove(KEY_ACTIVE_HOST)
            putInt(KEY_ACTIVE_HOST_INDEX, activeHostIndex)
            putString(KEY_HOST_ENTITY_LIST, serializeHosts(hostList))
            putInt(KEY_SOURCE_TYPE, sourceType.v)
            putInt(KEY_RATING, rating.v)
            putInt(KEY_THEME, theme.v)
            putInt(KEY_COLOR_VARIATION, colorVariation.v)
            putStringSet(KEY_MARKS, marks.map {it.toString()}.toSet())
            if(!category.isNullOrBlank()) putString(KEY_CATEGORY, category) else remove(KEY_CATEGORY)
        }
        AppViewModel.instance.settings = this
    }

    companion object {
        val logger = UtLog("Settings", BooApplication.logger)

        const val KEY_ACTIVE_HOST_INDEX = "activeHostIndex"
        const val KEY_HOST_ENTITY_LIST = "hostEntityList"
        const val KEY_SOURCE_TYPE = "sourceType"
        const val KEY_RATING = "rating"
        const val KEY_THEME = "theme"
        const val KEY_COLOR_VARIATION = "colorVariation"
        const val KEY_MARKS = "marks"
        const val KEY_CATEGORY = "category"

        fun load(context: Context): Settings {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            return Settings(
                activeHostIndex = pref.getInt(KEY_ACTIVE_HOST_INDEX, -1),
                hostList =  deserializeHosts(pref.getString(KEY_HOST_ENTITY_LIST, null)),
                sourceType = SourceType.valueOf(pref.getInt(KEY_SOURCE_TYPE, -1)),
                rating = Rating.valueOf(pref.getInt(KEY_RATING, -1)),
                theme = ThemeSetting.valueOf(pref.getInt(KEY_THEME, -1)),
                colorVariation = ColorVariation.valueOf(pref.getInt(KEY_COLOR_VARIATION,-1)),
                marks = pref.getStringSet(KEY_MARKS, null)?.map { Mark.valueOf(it) } ?: listOf(),
                category = pref.getString(KEY_CATEGORY, null))
//                .apply {logger.debug("Settings:Loaded $this")}
        }

        fun serializeHosts(list:List<HostAddressEntity>):String {
            return list.fold(JSONArray()) {json, v->
                json.put(JSONObject().apply  {
                    put("n", v.name)
                    put("a", v.address)
                })
                json
            }.toString().apply { logger.debug(this) }
        }

        fun deserializeHosts(jsonString:String?):List<HostAddressEntity> {
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


        val empty:Settings = Settings(-1, listOf(), SourceType.DB, Rating.NORMAL, ThemeSetting.SYSTEM, ColorVariation.PINK, listOf(), null)
    }
}