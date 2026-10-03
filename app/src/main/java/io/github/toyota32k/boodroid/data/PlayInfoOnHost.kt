package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.BooApplication

data class PlayInfoOnHost(val address:String/*key*/, val id:String, val position:Long, val isPlaying:Boolean) {
    companion object {
        fun set(address:String, id:String, position:Long, isPlaying:Boolean) {
            Data.logger.debug("save: $address, $id, $position, $isPlaying")
            val pref = BooApplication.instance.applicationContext.getSharedPreferences("playInfoOnHost", 0)
            pref.edit().apply {
                putString("${address}_id", id)
                putLong("${address}_position", position)
                putBoolean("${address}_playing", isPlaying)
                apply()
            }
        }
        fun get(address:String): PlayInfoOnHost? {
            val pref = BooApplication.instance.applicationContext.getSharedPreferences("playInfoOnHost", 0)
            val id = pref.getString("${address}_id", null) ?: return null
            val position = pref.getLong("${address}_position", 0L)
            val isPlaying = pref.getBoolean("${address}_playing", true)
            return PlayInfoOnHost(address, id, position, isPlaying)
        }

        fun remove(address:String) {
            val pref = BooApplication.instance.applicationContext.getSharedPreferences("playInfoOnHost", 0)
            pref.edit().apply {
                remove("${address}_id")
                remove("${address}_position")
                apply()
            }
        }
    }
}
