package io.github.toyota32k.boodroid.common

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.R

object PackageUtil {
    fun getPackageInfo(context:Context):PackageInfo? {
        return try {
            val name = context.packageName
            val pm = context.packageManager
            return pm.getPackageInfo(name, PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            BooApplication.logger.stackTrace(e)
            null
        }

    }

    fun getVersion(context: Context):String? {
        return try {
            // バージョン番号の文字列を返す
            getPackageInfo(context)?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            BooApplication.logger.stackTrace(e)
            null
        }
    }

    fun appName(context: Context):String {
        return context.resources.getString(R.string.app_name)
    }
}