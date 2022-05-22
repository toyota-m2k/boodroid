package io.github.toyota32k.boodroid.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.safeGetLong(key:String, defValue:Long) : Long {
    return try {
        this.getLong(key)
    }  catch (e:Throwable) {
        defValue
    }
}

fun JSONObject.safeGetInt(key:String, defValue:Int) : Int {
    return try {
        this.getInt(key)
    }  catch (e:Throwable) {
        defValue
    }
}

fun JSONObject.safeGetNullableString(key:String) : String? {
    return try {
        this.getString(key)
    }  catch (e:Throwable) {
        return null
    }
}
fun JSONObject.safeGetString(key:String) : String {
    return try {
        this.getString(key) ?: ""
    }  catch (e:Throwable) {
        return ""
    }
}

fun JSONArray.toIterable():Iterable<Any> {
    return Iterable {
        iterator {
            for (i in 0 until length()) {
                yield(get(i)!!)
            }
        }
    }
}

fun JSONArray.toFlow(): Flow<Any> {
//    val list = toIterable();
//    val itr = list.iterator()
//    val fn:()->Any = itr::next
//    return fn.asFlow()
//    list.asFlow()

    return flow {
        for (i in 0 until length()) {
            emit(get(i)!!)
        }
    }
}