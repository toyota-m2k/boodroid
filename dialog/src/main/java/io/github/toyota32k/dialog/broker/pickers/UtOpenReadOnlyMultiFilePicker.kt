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
 * 読み取り専用に複数のファイルを選択
 * ACTION_GET_CONTENT
 */
open class UtOpenReadOnlyMultiFilePicker : UtActivityBroker<String, List<Uri>?>() {
    companion object {
        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<List<Uri>?>) : IUtActivityLauncher<String> {
            return UtOpenReadOnlyMultiFilePicker().apply {
                register(owner, callback)
            }
        }
    }

    protected open fun prepareChooserIntent(intent: Intent): Intent {
        return Intent.createChooser(intent,"Choose files")
    }

    protected  inner class Contract : ActivityResultContracts.GetMultipleContents() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            return prepareChooserIntent(intent)
        }
    }

    override val contract: ActivityResultContract<String, List<Uri>?>
        get() = Contract()

    suspend fun selectFiles(mimeType:String = UtOpenReadOnlyFilePicker.defaultMimeType): List<Uri>? {
        return invoke(mimeType)
    }
}