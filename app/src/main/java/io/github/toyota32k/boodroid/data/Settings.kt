package io.github.toyota32k.boodroid.data

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.utils.UtLogger
import java.lang.IllegalStateException

class Settings(
    val activeHost: String?,
    val hostList: List<String>,
    val sourceType: SourceType,
    val rating:Rating,
    val marks:List<Mark>,
    val category:String?) {

    val isValid get() = !activeHost.isNullOrBlank()

    private val hostPort:String?
        get() = activeHost?.let { host ->
            return if(host.contains(":")) {
                host
            } else {
                "${host}:3500"
            }
        }
    @Suppress("SpellCheckingInspection")
    val baseUrl : String get() = "http://${hostPort}/ytplayer/"


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



    fun save(context: Context) {
        UtLogger.assert(isValid, "invalid settings")
        val pref = PreferenceManager.getDefaultSharedPreferences(context) ?: throw IllegalStateException("no preference manager.")
        pref.edit {
            if(activeHost!=null) putString(KEY_ACTIVE_HOST, activeHost) else remove(KEY_ACTIVE_HOST)
            if(hostList.isNotEmpty()) putStringSet(KEY_HOST_LIST, hostList.toSet()) else remove(KEY_HOST_LIST)
            putInt(KEY_SOURCE_TYPE, sourceType.v)
            putInt(KEY_RATING, rating.v)
            putStringSet(KEY_MARKS, marks.map {it.toString()}.toSet())
            if(!category.isNullOrBlank()) putString(KEY_CATEGORY, category) else remove(KEY_CATEGORY)
        }
        AppViewModel.instance.settings = this
    }

    companion object {
        const val KEY_ACTIVE_HOST = "activeHost"
        const val KEY_HOST_LIST = "hostList"
        const val KEY_SOURCE_TYPE = "sourceType"
        const val KEY_RATING = "rating"
        const val KEY_MARKS = "marks"
        const val KEY_CATEGORY = "category"

        fun load(context: Context): Settings {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            return Settings(
                activeHost = pref.getString(KEY_ACTIVE_HOST, null),
                hostList = pref.getStringSet(KEY_HOST_LIST, null)?.toList() ?: listOf(),
                sourceType = SourceType.valueOf(pref.getInt(KEY_SOURCE_TYPE, -1)),
                rating = Rating.valueOf(pref.getInt(KEY_RATING, -1)),
                marks = pref.getStringSet(KEY_MARKS, null)?.map { Mark.valueOf(it) } ?: listOf(),
                category = pref.getString(KEY_CATEGORY, null))
        }

        val empty:Settings = Settings(null, listOf(), SourceType.DB, Rating.NORMAL, listOf(), null)
    }
}