package io.github.toyota32k.boodroid.data

import java.lang.StringBuilder

class QueryBuilder {
    private val sb = StringBuilder()

    fun add(name:String, value:String) {
        if(sb.isNotEmpty()) {
            sb.append("&")
        }
        sb.append("${name}=${value}")
    }
    fun add(name:String, value:Int) {
        if(sb.isNotEmpty()) {
            sb.append("&")
        }
        sb.append("${name}=${value}")
    }
    val queryString:String
        get() = sb.toString()
}