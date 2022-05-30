@file:Suppress("unused")

package io.github.toyota32k.dialog.connector

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import io.github.toyota32k.dialog.UtDialogOwner

/**
 * 読み書き用にファイルを選択する
 */
@Deprecated("use broker.pickers.UtOpenFilePicker instead.")
class UtFileOpenPicker(owner: UtDialogOwner, mimeTypes: Array<String>, callback: ActivityResultCallback<Uri>)
    : UtActivityConnector<Array<String>, Uri>(owner.registerForActivityResult(Contract(), callback), mimeTypes) {

    private class Contract : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<out String>): Intent {
            val intent = super.createIntent(context, input)
            return Intent.createChooser(intent, "Choose File")
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:Array<String>)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<Array<String>, Uri>(UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<Array<String>, Uri> {
            return UtFileOpenPicker(owner, defArg, ImmortalResultCallback(key.immortalTaskName))
        }
    }
}

@Deprecated("use broker.pickers.UtOpenFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchFileOpenPicker(connectorName:String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtFileOpenPicker ?: throw IllegalStateException("connector is not an instance of UtFileOpenPicker")
        connector.launch()
    } as? Uri
}
@Deprecated("use broker.pickers.UtOpenFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchFileOpenPicker(connectorName:String, mimeTypes:Array<String>):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtFileOpenPicker ?: throw IllegalStateException("connector is not an instance of UtFileOpenPicker")
        connector.launch(mimeTypes)
    } as? Uri
}

/**
 * 複数ファイル選択用
 * ・・・１つしか選択できないみたい。標準ファイラーのバグ？
 * と思ったけど、UIの操作方法がまずかっただけだった。
 * 複数選択を許可しても、単にアイテムをタップしただけだと、タップされたアイテムだけが返ってくる。
 * アイテムを長押し選択すると、チェックボックスが現れ、右上に「選択」または、「開く」 などのボタンが表示される。
 * この状態で、アイテムを選択すると、チェックボックスのon/off がトグルして、複数選択が可能になる。
 */
@Deprecated("use broker.pickers.UtOpenMultiFilePicker instead.")
class UtMultiFileOpenPicker(owner: UtDialogOwner, mimeTypes: Array<String>, callback: ActivityResultCallback<List<Uri>>)
    : UtActivityConnector<Array<String>, List<Uri>>(owner.registerForActivityResult(Contract(), callback), mimeTypes) {

    private class Contract : ActivityResultContracts.OpenMultipleDocuments() {
        override fun createIntent(context: Context, input: Array<out String>): Intent {
            val intent = super.createIntent(context, input)
            return Intent.createChooser(intent, "Choose File")
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:Array<String>)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<Array<String>, List<Uri>>(
        UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<Array<String>, List<Uri>> {
            return createForImmortalTask(key.immortalTaskName, owner, defArg)
        }
    }

    companion object {
        @JvmStatic
        fun createForImmortalTask(immortalTaskName:String, owner: UtDialogOwner, mimeTypes:Array<String>) : UtMultiFileOpenPicker =
            UtMultiFileOpenPicker(owner,mimeTypes, ImmortalResultCallback(immortalTaskName))
    }
}

@Deprecated("use broker.pickers.UtOpenMultiFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchMultiFileOpenPicker(connectorName:String):List<Uri>? {
    @Suppress("UNCHECKED_CAST")
    return launchActivityConnector(connectorName) {
        val connector = it as? UtMultiFileOpenPicker ?: throw IllegalStateException("connector is not an instance of UtMultiFileOpenPicker")
        connector.launch()
    } as? List<Uri>
}

@Deprecated("use broker.pickers.UtOpenMultiFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchMultiFileOpenPicker(connectorName:String, mimeTypes:Array<String>):List<Uri>? {
    @Suppress("UNCHECKED_CAST")
    return launchActivityConnector(connectorName) {
        val connector = it as? UtMultiFileOpenPicker ?: throw IllegalStateException("connector is not an instance of UtMultiFileOpenPicker")
        connector.launch(mimeTypes)
    } as? List<Uri>
}

/**
 * 読み取り専用にファイル選択
 * https://developer.android.com/guide/topics/providers/document-provider.html?hl=ja
 * によると、ACTION_OPEN_DOCUMENT は、ACTION_GET_CONTENT の代わりとなることを意図したものではなく、
 * 利用目的によって使い分ける必要があるのだそうだ。
 *
 * - データの読み取りとインポートのみを行う場合は、ACTION_GET_CONTENT を使用
 * - データの編集を行うなど、長期間の永続的なアクセスが必要な場合は、ACTION_OPEN_DOCUMENT を使用
 *
 * おそらく、GET_CONTENT で取得した URI は、そのコールバック内でのみ有効（読み取り可能）なのだろうと思う。
 */
@Deprecated("use broker.pickers.UtOpenReadOnlyFilePicker instead.")
class UtContentPicker(owner: UtDialogOwner, mimeType: String, callback: ActivityResultCallback<Uri>)
    : UtActivityConnector<String, Uri>(owner.registerForActivityResult(Contract(), callback), mimeType) {

    private class Contract : ActivityResultContracts.GetContent() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            return Intent.createChooser(intent,null)
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:String)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<String, Uri>(
        UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<String, Uri> {
            return createForImmortalTask(key.immortalTaskName, owner, defArg)
        }
    }

    companion object {
        @JvmStatic
        fun createForImmortalTask(immortalTaskName:String, owner: UtDialogOwner, mimeType:String) : UtContentPicker =
            UtContentPicker(owner, mimeType, ImmortalResultCallback(immortalTaskName))
    }
}

@Deprecated("use broker.pickers.UtOpenReadOnlyFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchContentPicker(connectorName:String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtContentPicker ?: throw IllegalStateException("connector is not an instance of UtContentPicker")
        connector.launch()
    } as? Uri
}

@Deprecated("use broker.pickers.UtOpenReadOnlyFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchContentPicker(connectorName:String, mimeType:String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtContentPicker ?: throw IllegalStateException("connector is not an instance of UtContentPicker")
        connector.launch(mimeType)
    } as? Uri
}


/**
 * 複数ファイル選択
 * ACTION_OPEN_DOCUMENT の代わりに、ACTION_GET_CONTENT を使うようにしてみたが、やはり１ファイルしか選択できない。
 */
@Deprecated("use broker.pickers.UtOpenReadOnlyMultiFilePicker instead.")
class UtMultiContentPicker(owner: UtDialogOwner, mimeType: String, callback: ActivityResultCallback<List<Uri>>)
    : UtActivityConnector<String, List<Uri>>(owner.registerForActivityResult(Contract(), callback), mimeType) {

    private class Contract : ActivityResultContracts.GetMultipleContents() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            return Intent.createChooser(intent,null)
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:String)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<String, List<Uri>>(
        UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<String, List<Uri>> {
            return createForImmortalTask(key.immortalTaskName, owner, defArg)
        }
    }

    companion object {
        @JvmStatic
        fun createForImmortalTask(immortalTaskName:String, owner: UtDialogOwner, mimeType:String) : UtMultiContentPicker =
            UtMultiContentPicker(owner, mimeType, ImmortalResultCallback(immortalTaskName))
    }
}

@Deprecated("use broker.pickers.UtOpenReadOnlyMultiFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchMultiContentPicker(connectorName:String):List<Uri>? {
    @Suppress("UNCHECKED_CAST")
    return launchActivityConnector(connectorName) {
        val connector = it as? UtMultiContentPicker ?: throw IllegalStateException("connector is not an instance of UtMultiContentPicker")
        connector.launch()
    } as? List<Uri>
}

@Deprecated("use broker.pickers.UtOpenReadOnlyMultiFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchMultiContentPicker(connectorName:String, mimeType: String):List<Uri>? {
    @Suppress("UNCHECKED_CAST")
    return launchActivityConnector(connectorName) {
        val connector = it as? UtMultiContentPicker ?: throw IllegalStateException("connector is not an instance of UtMultiContentPicker")
        connector.launch(mimeType)
    } as? List<Uri>
}

/**
 * 作成用にファイルを選択
 */
@Deprecated("use broker.pickers.UtCreateFilePicker instead.")
class UtFileCreatePicker(owner: UtDialogOwner, initialName: String, mimeType:String?, callback: ActivityResultCallback<Uri>)
    : UtActivityConnector<String, Uri>(owner.registerForActivityResult(Contract(mimeType), callback), initialName) {

    private class Contract(val mimeType:String?): ActivityResultContracts.CreateDocument() {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            if(!mimeType.isNullOrEmpty()) {
                intent.setTypeAndNormalize(mimeType)
            }
            return Intent.createChooser(intent,null)
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:String, val mimeType: String?)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<String, Uri>(
        UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<String, Uri> {
            return createForImmortalTask(key.immortalTaskName, owner, defArg, mimeType)
        }
    }

    companion object {
        @JvmStatic
        fun createForImmortalTask(immortalTaskName:String, owner: UtDialogOwner, initialName:String, mimeType: String?) : UtFileCreatePicker =
            UtFileCreatePicker(owner, initialName, mimeType, ImmortalResultCallback(immortalTaskName))

    }
}

@Deprecated("use broker.pickers.UtCreateFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchFileCreatePicker(connectorName:String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtFileCreatePicker ?: throw IllegalStateException("connector is not an instance of UtFileCreatePicker")
        connector.launch()
    } as? Uri
}

@Deprecated("use broker.pickers.UtCreateFilePicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchFileCreatePicker(connectorName:String, initialName: String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtFileCreatePicker ?: throw IllegalStateException("connector is not an instance of UtFileCreatePicker")
        connector.launch(initialName)
    } as? Uri
}

/**
 * ディレクトリを選択
 */
@Deprecated("use broker.pickers.UtDirectoryPicker instead.")
class UtDirectoryPicker(owner: UtDialogOwner, initialPath: Uri?, callback: ActivityResultCallback<Uri>)
    : UtActivityConnector<Uri?, Uri>(owner.registerForActivityResult(Contract(), callback), initialPath) {

    private class Contract: ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            val intent = super.createIntent(context, input)
            return Intent.createChooser(intent,null)
        }
    }

    class Factory(immortalTaskName: String, connectorName:String, defArg:Uri?)
        : UtActivityConnectorFactoryBank.ActivityConnectorFactory<Uri?, Uri>(UtActivityConnectorKey(immortalTaskName,connectorName), defArg) {
        override fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<Uri?, Uri> {
            return createForImmortalTask(key.immortalTaskName, owner, defArg)
        }
    }

    companion object {
        @JvmStatic
        fun createForImmortalTask(immortalTaskName:String, owner: UtDialogOwner, initialPath:Uri?) : UtDirectoryPicker =
            UtDirectoryPicker(owner, initialPath, ImmortalResultCallback(immortalTaskName))
    }
}

@Deprecated("use broker.pickers.UtDirectoryPicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchDirectoryPicker(connectorName:String):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtDirectoryPicker ?: throw IllegalStateException("connector is not an instance of UtDirectoryPicker")
        connector.launch()
    } as? Uri
}

@Deprecated("use broker.pickers.UtDirectoryPicker instead.")
suspend fun UtActivityConnectorImmortalTaskBase.launchDirectoryPicker(connectorName:String, initialPath: Uri?):Uri? {
    return launchActivityConnector(connectorName) {
        val connector = it as? UtDirectoryPicker ?: throw IllegalStateException("connector is not an instance of UtDirectoryPicker")
        connector.launch(initialPath)
    } as? Uri
}
