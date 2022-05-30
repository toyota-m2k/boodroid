package io.github.toyota32k.dialog.task

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

interface IUtImmortalTaskContextSource {
    val immortalTaskContext:IUtImmortalTaskContext
    val immortalCoroutineScope get() = immortalTaskContext.coroutineScope
}
interface IUtImmortalTaskMutableContextSource : IUtImmortalTaskContextSource {
    override var immortalTaskContext:IUtImmortalTaskContext
}

interface IUtImmortalTaskContext: ViewModelStoreOwner {
    val taskName:String
    val coroutineScope:CoroutineScope
    var clientData:Any?     // タスク実行中のみ利用可能な任意のデータ領域
    val task : IUtImmortalTask? get() = UtImmortalTaskManager.taskOf(taskName)?.task
}

/**
 * タスク実行中にタスクに関する情報を保持し、ダイアログなどから利用できるようにする情報クラス
 * @param taskName  タスク名
 * @param parentContext 親タスクのコンテキスト（サブタスクの場合のみ）
 *        親のコンテキストを渡すことで、親のViewModelStoreが使用され、親タスクのライフサイクル内で動作する。
 *        つまり、親タスクが生きている間は、（子タスクが終了しても）ViewModelは温存される。
 */
class UtImmortalTaskContext(override val taskName:String, val parentContext:IUtImmortalTaskContext?) : IUtImmortalTaskContext {
    private var mScope: CoroutineScope? = null
    override val coroutineScope:CoroutineScope
        get() = mScope ?: (parentContext?.coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)).apply { mScope=this }
    override var clientData: Any? = null

    private var mViewModelStore: ViewModelStore? = parentContext?.viewModelStore

    init {
        if(parentContext == this) {
            throw IllegalArgumentException("recursive task chain.")
        }
    }

    /**
     * ViewModelStoreOwner i/f
     */
    @Suppress("RecursivePropertyAccessor")
    override fun getViewModelStore(): ViewModelStore {
        return mViewModelStore ?: (parentContext?.viewModelStore ?: ViewModelStore()).apply { mViewModelStore = this }
    }

    fun close() {
        if(parentContext==null) {
            mScope?.cancel()
            mViewModelStore?.clear()
        }
        mScope = null
        mViewModelStore = null
        clientData = null
    }
}