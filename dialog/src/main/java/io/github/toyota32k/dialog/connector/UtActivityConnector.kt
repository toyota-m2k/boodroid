package io.github.toyota32k.dialog.connector

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.UtDialogOwner
import io.github.toyota32k.dialog.task.UtImmortalTaskManager

/**
 * registerForActivityResult() / ActivityResultLauncher.launch の動作をカプセル化するクラス
 * @param launcher  サブクラスでregisterした ActivityResultLauncher インスタンス
 * @param defArg    ActivityResultLauncher.launch()の引数省略時に使用するデフォルトパラメータ
 */
abstract  class UtActivityConnector<I,O>(private val launcher: ActivityResultLauncher<I>, val defArg:I) {
    /**
     * Activity を起動
     * @param arg   ActivityResultLauncher.launch に渡す引数（省略時は、コンストラクタで設定された defArg を使用）
     */
    @JvmOverloads
    fun launch(arg:I = defArg) {
        launcher.launch(arg)
    }

    /**
     * ImmortalTaskからの呼び出し時に使用される ActivityResultCallback の実装
     * Activity/Fragmentからの呼び出しでは使わない（使ってはいけない）。
     */
    class ImmortalResultCallback<O>(private val immortalTaskName:String): ActivityResultCallback<O> {
        override fun onActivityResult(result: O) {
            val entry = UtImmortalTaskManager.taskOf(immortalTaskName) ?: throw IllegalStateException("no task named '$immortalTaskName'")
            val task = entry.task ?: throw IllegalStateException("no task attached to '$immortalTaskName'")
            task.resumeTask(result)
        }
    }
}
