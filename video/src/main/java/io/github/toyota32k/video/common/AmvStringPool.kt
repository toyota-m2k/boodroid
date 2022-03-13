package io.github.toyota32k.video.common

import android.util.SparseArray

/**
 * videoライブラリのUIで、app側で定義した文字列(R.string.xxx)を使用するための小さな仕掛け
 */
object AmvStringPool {
    private val map = SparseArray<String>()

    @JvmStatic
    fun getString(id:Int) : String? {
        return map.get(id, null)
    }

    @JvmStatic
    fun setString(id:Int, str:String) {
        map.put(id, str)
    }

    operator fun get(id:Int):String? {
        return map.get(id, null)
    }
}

