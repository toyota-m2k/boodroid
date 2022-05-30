package io.github.toyota32k.dialog.broker

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.utils.UtLog
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * UtActivityConnectorは必要以上に複雑化してしまったので、
 * ImmortalTaskから呼び出すことに限定して、超絶簡略化したバージョンを用意してみる。
 * - ImmortalTask から呼び出すための仕掛け
 *      FilePicker類は、UtFilePickerStore(<--IUtActivityBrokerStore) 経由で使用する。
 *      他のActivity呼び出しを実装する場合は、UtFilePickerStoreの実装を参考に、registerを呼び出す実装を入れる。
 * - Activityのメンバーに登録して呼び出すための仕掛け
 *      registerForActivityResult()の単純なラッパー
 *      Activityのフィールドに、適当なメンバー変数を用意して、処理内容とともに、次のように定義する。
 *      val launcher: IUtActivityLauncher<String> = UtOpenReadOnlyFilePicker.create(this) { uri?-> ... }
 */

abstract class UtActivityBroker<I,O>
    : ActivityResultCallback<O>, IUtActivityBroker, IUtActivityLauncher<I> {
    companion object {
        val logger = UtLog("UtActivityBroker")
        var continuation:Continuation<*>? = null
    }
    // region   ImmortalTask から呼び出すための仕掛け

    abstract val contract: ActivityResultContract<I,O>
    private lateinit var launcher: ActivityResultLauncher<I>
    private var taskContext: IUtImmortalTaskContext? = null

    override fun register(owner: Fragment) {
        logger.debug()
        launcher = owner.registerForActivityResult(contract, this)
    }
    override fun register(owner: FragmentActivity) {
        logger.debug()
        launcher = owner.registerForActivityResult(contract, this)
    }

    override fun onActivityResult(result: O) {
        @Suppress("UNCHECKED_CAST")
        (continuation as? Continuation<O>)?.resume(result)
        continuation = null
    }

    protected suspend fun invoke(input:I): O {
        if(continuation!=null) {
            throw IllegalStateException("broker is busy.")
        }
        return suspendCoroutine {
            continuation = it
            launcher.launch(input)
        }
    }

    // endregion

    // region Activityのメンバーに登録して呼び出すための仕掛け

    fun register(owner: Fragment, callback:ActivityResultCallback<O>) {
        logger.debug()
        launcher = owner.registerForActivityResult(contract, callback)
    }
    fun register(owner: FragmentActivity, callback:ActivityResultCallback<O>) {
        logger.debug()
        launcher = owner.registerForActivityResult(contract, callback)
    }

    override fun launch(input: I) {
        launcher.launch(input)
    }
}