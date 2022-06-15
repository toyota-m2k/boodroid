package io.github.toyota32k.boodroid.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 外向きには、StatusFlow としてプロパティを公開しつつ、内部的には、MutableStateFlowとして扱うことを実現する、世紀の大発明ｗ
 * - この仕組みを使いたいクラス（ViewModel派生クラスなど）を、IUtPropertyHost派生にする。
 * - プロパティは、val prop:StateFlow<T> = MutableStateFlow<T>() のように実装する。
 * - プロパティの値を変更するときは、そのクラス内から、prop.mutable.value に値をセットする。
 *
 * 使用例）
 * class HogeViewModel: ViewModel(), IFlowPropertyHost {
 *    val isBusy:StateFlow<Boolean> = MutableStateFlow(false)
 *
 *    fun setBusy(busy:Boolean) {
 *        isBusy.mutable.value = busy
 *    }
 * }
 *
 * class HogeActivity {
 *     lateinit var viewModel:HogeViewModel
 *
 *     fun doSomething() {
 *        if(viewModel.isBusy.value) return
 *        // viewModel.isBusy.value = true     // error (the property of "isBusy" is immutable.)
 *        viewModel.setBusy(true) {
 *            try {
 *                // do something
 *            } finally {
 *                viewModel.setBusy(false)
 *            }
 *        }
 *     }
 * }
 */
interface IUtPropertyHost {
    val <T> Flow<T>.mutable:MutableStateFlow<T>
        get() = this as MutableStateFlow<T>
}

