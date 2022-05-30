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
 * 読み取り専用にファイルを選択
 * ACTION_GET_CONTENT
 * データの読み取りとインポートのみを行う場合
 */
open class UtOpenReadOnlyFilePicker : UtActivityBroker<String, Uri?>()  {
    companion object {
        const val defaultMimeType: String = "*/*"
        @JvmStatic
        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<Uri?>) : IUtActivityLauncher<String> {
            return UtOpenReadOnlyFilePicker().apply {
                register(owner, callback)
            }
        }
    }

    protected open fun prepareChooserIntent(intent:Intent):Intent {
        return Intent.createChooser(intent,"Choose a file")
    }

    protected inner class Contract : ActivityResultContracts.GetContent() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            return prepareChooserIntent(intent)
        }
    }

    override val contract: ActivityResultContract<String, Uri?>
        get() = Contract()

    suspend fun selectFile(mimeType:String = defaultMimeType): Uri? {
        return invoke(mimeType)
    }
}