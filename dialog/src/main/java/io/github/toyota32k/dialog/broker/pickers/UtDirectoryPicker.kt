package io.github.toyota32k.dialog.broker.pickers

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.broker.IUtActivityLauncher
import io.github.toyota32k.dialog.broker.UtActivityBroker

open class UtDirectoryPicker : UtActivityBroker<Uri?, Uri?>() {
    companion object {
        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<Uri?>) : IUtActivityLauncher<Uri?> {
            return UtDirectoryPicker().apply {
                register(owner, callback)
            }
        }
    }
    protected open fun prepareChooserIntent(intent:Intent):Intent {
        return Intent.createChooser(intent, "Choose a folder")
    }
    protected inner class Contract: ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            val intent = super.createIntent(context, input)
            return prepareChooserIntent(intent)
        }
    }
    override val contract: ActivityResultContract<Uri?, Uri?>
        get() = Contract()

    suspend fun selectDirectory(initialPath:Uri?=null):Uri? {
        return invoke(initialPath)
    }
}