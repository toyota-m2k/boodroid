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
 * 読み書き用にファイルを選択する
 * ACTION_OPEN_DOCUMENT
 * データの編集を行うなど、長期間の永続的なアクセスが必要な場合に使用。
 */
open class UtOpenFilePicker : UtActivityBroker<Array<String>, Uri?>() {
    companion object {
        val defaultMimeTypes: Array<String> = arrayOf("*/*")

        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<Uri?>) : IUtActivityLauncher<Array<String>> {
            return UtOpenFilePicker().apply {
                register(owner, callback)
            }
        }
    }

    protected open fun prepareChooserIntent(intent:Intent):Intent {
        return Intent.createChooser(intent, "Choose a file")
    }

    protected inner class Contract : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<out String>): Intent {
            val intent = super.createIntent(context, input)
            return prepareChooserIntent(intent)
        }
    }

    override val contract: ActivityResultContract<Array<String>, Uri?>
        get() = Contract()

    suspend fun selectFile(mimeTypes:Array<String> = defaultMimeTypes):Uri? {
        return invoke(mimeTypes)
    }
}

