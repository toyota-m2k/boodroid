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
 * 読み書き用に複数のファイルを選択する
 * ACTION_OPEN_DOCUMENT
 * アイテムを長押し選択すると、チェックボックスが現れ、右上に「選択」または、「開く」 などのボタンが表示される。
 * この状態で、アイテムを選択すると、チェックボックスのon/off がトグルして、複数選択が可能になる。
 */
open class UtOpenMultiFilePicker : UtActivityBroker<Array<String>, List<Uri>?>() {
    companion object {
        fun launcher(owner: FragmentActivity, callback: ActivityResultCallback<List<Uri>?>) : IUtActivityLauncher<Array<String>> {
            return UtOpenMultiFilePicker().apply {
                register(owner, callback)
            }
        }
    }

    protected open fun prepareChooserIntent(intent:Intent):Intent {
        return Intent.createChooser(intent, "Choose files")
    }

    protected inner class Contract : ActivityResultContracts.OpenMultipleDocuments() {
        override fun createIntent(context: Context, input: Array<out String>): Intent {
            val intent = super.createIntent(context, input)
            return prepareChooserIntent(intent)
        }
    }

    override val contract: ActivityResultContract<Array<String>, List<Uri>?>
        get() = Contract()

    suspend fun selectFiles(mimeTypes:Array<String> = UtOpenFilePicker.defaultMimeTypes): List<Uri>? {
        return invoke(mimeTypes)
    }
}