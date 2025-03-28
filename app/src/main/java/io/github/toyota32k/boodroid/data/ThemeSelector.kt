package io.github.toyota32k.boodroid.data

import android.app.Application
import android.provider.Settings
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.boodroid.BooApplication
import kotlin.collections.firstOrNull

data class ThemeInfo(
    val label: String,
    @StyleRes val id: Int,
    @StyleRes val overlayMedium: Int?,
    @StyleRes val overlayHigh: Int?,
)

interface IThemeList {
    val themes: List<ThemeInfo>
    val defaultTheme get() = themes[0]
    fun themeOf(name: String): ThemeInfo {
        return themes.firstOrNull { it.label == name } ?: defaultTheme
    }
}

class ThemeSelector(val application: Application) {
    enum class NightMode(val mode:Int) {
        System(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        Light(AppCompatDelegate.MODE_NIGHT_NO),
        Dark(AppCompatDelegate.MODE_NIGHT_YES),
        ;
        companion object {
            fun ofMode(mode:Int): NightMode? {
                return entries.firstOrNull { it.mode == mode }
            }
        }
    }
    enum class ContrastLevel {
        System,
        Normal,
        Medium,
        High,
        ;
        companion object {
            fun parse(name: String): ContrastLevel? {
                return entries.firstOrNull { it.name == name }
            }
        }
    }

    var currentThemeId:Int = -1
        private set

    val currentNightMode:NightMode get() = NightMode.ofMode(AppCompatDelegate.getDefaultNightMode()) ?: NightMode.System

    private fun getSystemContrastLevel(): ContrastLevel {
        return try {
            val isDaltonizerEnabled = Settings.Secure.getInt(
                application.contentResolver,
                "accessibility_display_daltonizer_enabled", 0
            ) == 1

            val daltonizerMode = Settings.Secure.getInt(
                application.contentResolver,
                "accessibility_display_daltonizer", -1
            )

            when {
                !isDaltonizerEnabled -> ContrastLevel.Normal
                daltonizerMode == 0 -> ContrastLevel.Medium
                daltonizerMode > 0 -> ContrastLevel.High
                else -> ContrastLevel.Normal
            }
        } catch (e: Exception) {
            ContrastLevel.Normal
        }
    }

    private fun resolveContrastLevel(contrastLevel: ContrastLevel): ContrastLevel {
        return if(contrastLevel== ContrastLevel.System) {
            getSystemContrastLevel()
        } else contrastLevel
    }

    private fun resolveThemeId(theme: ThemeInfo, contrastLevel: ContrastLevel): Int {
        return when (resolveContrastLevel(contrastLevel)) {
            ContrastLevel.High -> theme.overlayHigh
            ContrastLevel.Medium -> theme.overlayMedium
            else -> null
        } ?: theme.id
    }

    fun isThemeChanged(theme: ThemeInfo, contrastLevel: ContrastLevel): Boolean {
        return currentThemeId != resolveThemeId(theme, contrastLevel)
    }

    fun applyTheme(theme: ThemeInfo, contrastLevel: ContrastLevel, activity: FragmentActivity) {
        val themeId = resolveThemeId(theme, contrastLevel)
        activity.setTheme(themeId)
        currentThemeId = themeId
    }

    fun applyNightMode(nightMode: NightMode) {
        if(AppCompatDelegate.getDefaultNightMode()!=nightMode.mode) {
            AppCompatDelegate.setDefaultNightMode(nightMode.mode)
        }
    }

    companion object {
        val defaultInstance: ThemeSelector by lazy { ThemeSelector(BooApplication.instance) }
    }
}