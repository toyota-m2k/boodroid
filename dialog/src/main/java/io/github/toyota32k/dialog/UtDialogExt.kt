package io.github.toyota32k.dialog

import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import kotlinx.coroutines.Job

/**
 * ダイアログを表示して、結果をコールバック（onResult)で受け取る。
 * onResultはImmortalTaskコンテキストで呼び出されるので、その内部ではImmortalTaskのメソッドが使える。
 * ダイアログの結果はonResultの引数、dlg.status などを確認。
 */
fun UtDialog.showOnTask(tag:String, onResult:(dlg:UtDialog)->Unit): Job {
    val dlg = this
    return UtImmortalSimpleTask.run(tag) {
        showDialog(tag) { dlg }
        onResult(dlg)
        dlg.status.positive
    }
}

/**
 * ダイアログを表示して、結果をコールバック（onResult)で受け取り、結果を返す。
 * onResultはImmortalTaskコンテキストで呼び出されるので、その内部ではImmortalTaskのメソッドが使える。
 * ダイアログの結果はonResultの引数、dlg.status などを確認。
 * onResultの戻り値を返すので、連続した処理を書くときに使えるんじゃなかろうか。
 */
suspend fun <T> UtDialog.showAndGetResult(tag:String, onResult:(dlg:UtDialog)->T):T? {
    val dlg = this
    var result:T? = null
    UtImmortalSimpleTask.runAsync(tag) {
        showDialog(tag) { dlg }
        result = onResult(dlg)
        dlg.status.positive
    }
    return result
}

