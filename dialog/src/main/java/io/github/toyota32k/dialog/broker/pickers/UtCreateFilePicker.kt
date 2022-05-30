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

/**
 * 作成用にファイルを選択
 */
open class UtCreateFilePicker : UtActivityBroker<String, Uri?>() {
    companion object {
        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<Uri?>) : IUtActivityLauncher<String> {
            return UtCreateFilePicker().apply {
                register(owner, callback)
            }
        }
    }

    var mimeType:String? = null
    protected open fun prepareChooserIntent(intent:Intent):Intent {
        return Intent.createChooser(intent, "Choose a file")
    }

    protected inner class Contract: ActivityResultContracts.CreateDocument() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            if(!mimeType.isNullOrEmpty()) {
                intent.setTypeAndNormalize(mimeType)
            }
            return prepareChooserIntent(intent)
        }
    }

    override val contract: ActivityResultContract<String, Uri?>
        get() = Contract()

    suspend fun selectFile(initialFileName:String, mimeType:String? = null):Uri? {
        this.mimeType = mimeType
        return invoke(initialFileName)
    }
}