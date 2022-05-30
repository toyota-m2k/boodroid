package io.github.toyota32k.dialog

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.IllegalStateException
import java.lang.ref.WeakReference

/**
 * ダイアログの親となる、FragmentとActivity をAPI的に区別しないで扱えるようにするためのクラス
 * これは、Fragment/Activityを強参照で保持するので、引数の引き渡し用としてのみ使用し、
 * メンバーとして保持する場合は UtDialogWeakOwner を使用すること。
 */
data class UtDialogOwner(val lifecycleOwner: LifecycleOwner) {
    init {
        if(!(lifecycleOwner is FragmentActivity || lifecycleOwner is Fragment)) {
            throw IllegalArgumentException("DialogOwner must be FragmentActivity or Fragment")
        }
    }
    fun asContext() : Context {
        return when(lifecycleOwner) {
            is FragmentActivity-> lifecycleOwner
            is Fragment->lifecycleOwner.requireContext()
            is Context->lifecycleOwner
            else -> {
                throw IllegalStateException("invalid lifecycleOwner")
            }
        }

    }
    @Suppress("unused")
    fun asActivity():FragmentActivity? {
        return when(lifecycleOwner) {
            is FragmentActivity->lifecycleOwner
            is Fragment->lifecycleOwner.requireActivity()
            else -> null
        }
    }

    @Suppress("unused")
    fun asFragment():FragmentActivity? {
        return when(lifecycleOwner) {
            is FragmentActivity->lifecycleOwner
            else -> null
        }
    }
}

/**
 * Activity --> UtDialogOwner
 */
fun FragmentActivity.toDialogOwner() = UtDialogOwner(this)

/**
 * Fragment --> UtDialogOwner
 */
@Suppress("unused")
fun Fragment.toDialogOwner() = UtDialogOwner(this)

/**
 * UtDialogOwnerを親にしてダイアログを開くための拡張関数
 */
fun IUtDialog.show(owner: UtDialogOwner, tag:String) {
    when(owner.lifecycleOwner) {
        is FragmentActivity -> show(owner.lifecycleOwner, tag)
        is Fragment         -> show(owner.lifecycleOwner.requireActivity(), tag)
    }
}

/**
 * UtDialogOwnerを親にしてUtDialogHostManager.NamedReceptor.showDialogを呼ぶための拡張関数
 */
//fun UtDialogHostManager.ReceptorImpl.showDialog(owner:UtDialogOwner, creator:(UtDialogHostManager.ReceptorImpl)->IUtDialog) {
//    when(owner.lifecycleOwner) {
//        is FragmentActivity -> showDialog(owner.lifecycleOwner, creator)
//        is Fragment         -> showDialog(owner.lifecycleOwner, creator)
//    }
//}
@Suppress("unused")
fun <D> UtDialogHostManager.NamedReceptor<D>.showDialog(owner: UtDialogOwner, clientData:Any?=null, creator:(UtDialogHostManager.NamedReceptor<D>)->D) where D: IUtDialog {
    when(owner.lifecycleOwner) {
        is FragmentActivity -> showDialog(owner.lifecycleOwner, clientData, creator)
        is Fragment         -> showDialog(owner.lifecycleOwner, clientData, creator)
    }
}

open class UtDialogWeakOwner(owner: LifecycleOwner) : LifecycleEventObserver {
    private var weakOwner :WeakReference<LifecycleOwner>? = null

    var lifecycleOwner:LifecycleOwner?
        get() = weakOwner?.get()
        private set(owner) {
            if(!(owner is FragmentActivity || owner is Fragment)) {
                throw IllegalArgumentException("DialogOwner must be FragmentActivity or Fragment")
            }
            weakOwner = WeakReference(owner)
            owner.lifecycle.addObserver(this)
        }

    init {
        this.lifecycleOwner = owner
    }


    val asDialogOwner: UtDialogOwner?
        get() = lifecycleOwner?.let { UtDialogOwner(it) }

    final override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (!source.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            dispose()
        }
    }

    open fun dispose() {
        lifecycleOwner?.lifecycle?.removeObserver(this)
        weakOwner = null
    }

    @Suppress("unused")
    val hasOwner:Boolean
        get() = lifecycleOwner!=null
}