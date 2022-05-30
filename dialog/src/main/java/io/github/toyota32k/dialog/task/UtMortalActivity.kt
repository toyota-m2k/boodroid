package io.github.toyota32k.dialog.task

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import io.github.toyota32k.dialog.*

/**
 * ImmortalTask と協調動作するActivityの基本実装
 */
abstract class UtMortalActivity private constructor(@Suppress("MemberVisibilityCanBePrivate") val dialogHostManager: UtDialogHostManager) : AppCompatActivity(), IUtDialogHost by dialogHostManager {
    constructor() : this(UtDialogHostManager())

    /**
     * タスク名のテーブル
     */
    protected open val immortalTaskNameList:Array<String> = emptyArray()

    /**
     * タスクの結果を受け取るハンドラ
     * Activityがタスクの結果を知る必要がある場合はオーバーライドする。
     * 放置でよければ、オーバーライド不要。
     */
    protected open fun notifyImmortalTaskResult(taskInfo: UtImmortalTaskManager.ITaskInfo) {}

    /**
     * Activity終了時にタスクをdisposeするかどうかを返す。
     * ActivityをまたいでTaskを残したいとき以外はtrueを返せばよいと思う。
     */
    protected open fun queryDisposeTaskOnFinishActivity(name:String):Boolean {
        return true
    }

    /**
     * Activity が前面に上がる時点で、reserveTask()を呼び出して、タスクテーブルに登録しておく。
     */
    override fun onResume() {
        super.onResume()

        // ImmortalTask に接続する
        UtImmortalTaskManager.registerOwner(toDialogOwner())
        for(name in immortalTaskNameList) {
            observeImmortalTask(name, UtImmortalTaskManager.reserveTask(name).state)
        }
    }

    /**
     * Activity が　finish()するときに disposeTask()する。
     */
    override fun onPause() {
        super.onPause()
        for(name in immortalTaskNameList) {
//            UtImmortalTaskManager.onOwnerPaused(name, toDialogOwner())
            if(isFinishing&&queryDisposeTaskOnFinishActivity(name)) {
                UtImmortalTaskManager.disposeTask(name,toDialogOwner())
            }
        }
    }

    /**
     * UtMortalActivityを継承するActivityは、onKeyDownを直接オーバーライドしないで、必要なら、handleKeyEventをオーバーライドする。
     *
     */
    open fun handleKeyEvent(keyCode: Int, event: KeyEvent?):Boolean {
        return false
    }

    /**
     * KeyDownイベントハンドラ（オーバーライド禁止）
     * - ダイアログ表示中なら、ダイアログにイベントを渡す。
     * - ダイアログ表示中でなければ、handleKeyEvent()を呼び出す。
     * - handleKeyEvent()がfalseを返したら、親クラス(FragmentActivity）の onKeyDownを呼ぶ。
     */
    final override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentDialog: UtDialog? = UtDialogHelper.currentDialog(this)
        if (currentDialog != null) {
            logger.debug { "key event consumed by dialog: $keyCode (${event}) : ${currentDialog.javaClass.simpleName}" }
            if(currentDialog.onKeyDown(keyCode, event)) {
                // ダイアログがイベントを処理した
                return true
            }
            if(!currentDialog.isDialog) {
                // フラグメントモードの場合は、ダイアログでイベントを処理しなくても、消費したことにする（ダイアログの後ろで、Activityが操作されてしまうのを防止）
                return true
            }
        }
        // サブクラスの処理を呼ぶ
        if(handleKeyEvent(keyCode, event)) {
            return true
        }
        // イベントを消費しなかったなら、親クラスへ
        return super.onKeyDown(keyCode, event)
    }

//    override fun onBackPressed() {
//        if(UtDialogHelper.cancelCurrentDialog(this)) {
//            return
//        }
//        super.onBackPressed()
//    }

    /**
     * ImmortalTask の状態変化を受け取るハンドラ
     * - 終了ステータス以外は無視。
     * - 終了ステータスの場合は、notifyImmortalTaskResult()を呼ぶ
     */
    private fun onImmortalTaskStateChanged(taskName:String, state:UtImmortalTaskState) {
        if(state.finished) {
            val task = UtImmortalTaskManager.taskOf(taskName) ?: return
            notifyImmortalTaskResult(task)
        }
    }

    /**
     * タスクの状態監視オブザーバー登録メソッド
     */
    private fun observeImmortalTask(taskName:String, liveData: LiveData<UtImmortalTaskState>) {
        logger.debug("")
        liveData.observe(this) {
            if(it!=null) {
                onImmortalTaskStateChanged(taskName, it)
            }
        }
    }

    open val logger = UtImmortalTaskManager.logger
}