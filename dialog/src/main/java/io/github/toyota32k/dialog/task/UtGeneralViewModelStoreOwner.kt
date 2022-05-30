package io.github.toyota32k.dialog.task

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * ライフサイクルをマニュアルで制御するViewModelStoreのオーナー
 * （ViewModelProviderの引数に渡すやつ）
 */
class UtGeneralViewModelStoreOwner : ViewModelStoreOwner {
    private val mViewModelStore = ViewModelStore()

    override fun getViewModelStore(): ViewModelStore {
        return mViewModelStore
    }

    // 不要になったら呼び出す。
    // このストアに属するすべてのViewModelが破棄される
    fun release() {
        mViewModelStore.clear()
    }
}