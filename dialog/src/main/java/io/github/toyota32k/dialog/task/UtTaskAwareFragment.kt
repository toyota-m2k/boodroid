package io.github.toyota32k.dialog.task

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.toyota32k.dialog.UtBundleDelegate

/**
 * オーナーのタスク名を保持しておくことにより、同じオーナーに属するフラグメント間で、タスクのライフサイクルを持つビューモデルを共有できるようにしたフラグメントクラス。
 * 初期化のタイミングで、親タスクのタスク名を immortalTaskName にセットしておく。
 * 例）
 * class FooFragment()      // 引数なしのプライマリコンストラクタ（osの要求により必須）
 *  : UtTaskAwareFragment() {
 *  constructor(taskName:String) : this() { immortalTaskName = taskName }   // セカンダリコンストラクタ（インスタンス作成時は、こちらを使用）
 * }
 *
 * fun bar() {
 *    val fragment = FooFragment("SomeTaskName")
 * }
 */
abstract class UtTaskAwareFragment: Fragment() {
    val bundle = UtBundleDelegate { ensureArguments() }
    var immortalTaskName: String? by bundle.stringNullable
    private val ownerTask:IUtImmortalTask? get() = immortalTaskName?.let { UtImmortalTaskManager.taskOf(it)?.task }
    protected fun <T> getViewModel(clazz:Class<T>):T? where T:ViewModel {
        return ownerTask?.let { ViewModelProvider(it.immortalTaskContext, ViewModelProvider.NewInstanceFactory())[clazz] }
    }
}

fun Fragment.ensureArguments(): Bundle {
    return this.arguments ?: Bundle().apply { arguments = this }
}

