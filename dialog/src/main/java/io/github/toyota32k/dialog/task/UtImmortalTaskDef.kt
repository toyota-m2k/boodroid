package io.github.toyota32k.dialog.task

import io.github.toyota32k.dialog.UtDialogOwner
import java.io.Closeable

/**
 * 不死身タスクの状態
 */
enum class UtImmortalTaskState(val finished:Boolean) {
    INITIAL(false),
    RUNNING(false),
    COMPLETED(true),
    ERROR(true),
}

/**
 * 不死身タスクのi/f
 */
interface IUtImmortalTask : Closeable, IUtImmortalTaskContextSource {
    val taskName: String
    val taskResult:Any?
    fun resumeTask(value:Any?)
}

/**
 * ライフサイクルオブジェクト（死んだり生き返ったりするオブジェクト:Activity/Fragment）を取得するための i/f
 */
interface IUiMortalInstanceSource {
    suspend fun getOwner() : UtDialogOwner
    suspend fun getOwnerOf(clazz:Class<*>) : UtDialogOwner
}

suspend inline fun <T> IUiMortalInstanceSource.withOwner(fn:(UtDialogOwner)->T):T {
    return fn(getOwner())
}

suspend inline fun <T> IUiMortalInstanceSource.withOwner(clazz:Class<*>, fn:(UtDialogOwner)->T):T {
    return fn(getOwnerOf(clazz))
}
