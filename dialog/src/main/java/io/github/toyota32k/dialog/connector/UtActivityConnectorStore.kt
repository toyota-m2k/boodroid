package io.github.toyota32k.dialog.connector

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.UtDialogOwner

/**
 * Activity/FragmentからImmortalTaskに ActivityConnector インスタンスを供給するためのi/f定義
 * ImmortalTask と協調動作する ActivityまたはFragmentは、このi/fを継承する。
 */
interface IUtActivityConnectorStore {
    /**
     * ActivityConnector を取得する
     * @param immortalTaskName    immortalTask のタスク名
     * @param connectorName       connector を識別する名前
     */
    fun getActivityConnector(immortalTaskName:String, connectorName:String): UtActivityConnector<*, *>?
}

/**
 * IUtActivityConnectorStore の実装クラス
 * 通常は、同Activity(Fragment)内に、UtActivityConnectorStoreインスタンスを持たせ、
 * Activity.getActivityConnector()から、UtActivityConnectorStore.getActivityConnector() を呼び出すように実装する。
 */
class UtActivityConnectorStore(private val map: Map<UtActivityConnectorKey, UtActivityConnector<*, *>>) :
    IUtActivityConnectorStore {
    /**
     * ActivityConnector を取得する
     * @param immortalTaskName    immortalTask のタスク名
     * @param connectorName       connector を識別する名前
     */
    override fun getActivityConnector(immortalTaskName:String, connectorName:String): UtActivityConnector<*, *>? {
        return map[UtActivityConnectorKey(immortalTaskName,connectorName)]
    }
}

/**
 * UtDialogOwnerからIUtActivityConnectorStoreを取得するための拡張関数
 * もし、UtDialogOwnerが IUtActivityConnectorStore を実装していなければ null を返す。
 */
fun UtDialogOwner.asActivityConnectorStore() : IUtActivityConnectorStore? =
    lifecycleOwner as? IUtActivityConnectorStore

/**
 * UtDialogOwnerに対して、registerForActivityResult() できるようにするための拡張関数
 * Fragment用とActivity用に実装を用意するのが面倒なので、connector関連は、UtDialogOwner で一本化する。
 */
fun <I, O> UtDialogOwner.registerForActivityResult(contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>): ActivityResultLauncher<I> {
    return when(lifecycleOwner) {
        is Fragment -> lifecycleOwner.registerForActivityResult(contract, callback)
        is FragmentActivity -> lifecycleOwner.registerForActivityResult(contract, callback)
        else -> throw java.lang.IllegalStateException("invalid lifecycle owner")
    }
}
