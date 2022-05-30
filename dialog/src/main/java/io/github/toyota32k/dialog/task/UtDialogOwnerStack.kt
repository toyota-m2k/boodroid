package io.github.toyota32k.dialog.task

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import io.github.toyota32k.dialog.UtDialogOwner
import io.github.toyota32k.dialog.UtDialogWeakOwner
import io.github.toyota32k.utils.Chronos
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class UtDialogOwnerStack: IUiMortalInstanceSource {
    private val list = mutableListOf<UtOwner>()
    private val ownerFlow = MutableStateFlow<UtOwner?>(null)
    inner class UtOwner(lifecycleOwner: LifecycleOwner): UtDialogWeakOwner(lifecycleOwner) {
        override fun dispose() {
            super.dispose()
            list.remove(this)
            if (ownerFlow.value == this) {
                ownerFlow.value = latest()
            }
        }
    }
    @MainThread
    fun push(owner: UtDialogOwner) {
        UtOwner(owner.lifecycleOwner).also { uo->
            if(list.find { it.lifecycleOwner === owner.lifecycleOwner }==null) {
                list.add(uo)
            }
            ownerFlow.value = uo
        }
    }
    @MainThread
    fun remove(owner: UtDialogOwner) {
        list.find { it.lifecycleOwner === owner.lifecycleOwner }?.dispose()
    }

    @MainThread
    private fun latest(): UtOwner? {
        while(list.size>0) {
            val v = list.last()
            if(v.asDialogOwner!=null) {
                return v
            }
            v.dispose()
        }
        return null
    }

    override suspend fun getOwner(): UtDialogOwner {
        return ownerFlow.mapNotNull { it?.asDialogOwner }.first()
    }

    override suspend fun getOwnerOf(clazz:Class<*>): UtDialogOwner {
        return ownerFlow.mapNotNull {
            val owner = it?.asDialogOwner
            if (owner != null && owner.lifecycleOwner::class.java == clazz) owner else null
        }.first()
    }
}
