/**
 * エラー情報を保持するクラス
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package io.github.toyota32k.video.common

class AmvError() {
    private var mMessage: String? = null
    private var mException: Throwable? = null

    constructor(message:String) : this() {
        setError(message)
    }
    constructor(error:Throwable) : this() {
        setError(error)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val hasError: Boolean
        get() = (mMessage != null || mException != null)

    val message : String
        get() = mMessage ?: mException?.message ?: ""


    @Suppress("unused")
    fun reset() {
        mException = null
        mMessage = null
    }

    fun setError(e:Throwable) {
        if (null == mException) {
            mException = e
        }
    }

    fun setError(message:String) {
        if (null == mMessage) {
            mMessage = message
        }
    }

    fun copyFrom(e: AmvError) {
        if (!hasError) {
            mException = e.mException
            mMessage = e.mMessage
        }
    }

    @Suppress("unused")
    fun clone() : AmvError {
        return AmvError().apply {
            copyFrom(this)
        }
    }

    override fun toString(): String {
        return when {
            null!=mException -> mException.toString()
            null!=mMessage -> message
            else -> "No error."
        }
    }
}