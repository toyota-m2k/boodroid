package io.github.toyota32k.dialog.broker.pickers

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.toyota32k.dialog.broker.IUtActivityBroker
import io.github.toyota32k.dialog.broker.UtActivityBrokerStore

class UtFilePickerStore private constructor() : UtActivityBrokerStore() {
    constructor(activity: FragmentActivity):this() {
        register(activity)
    }
    constructor(fragment: Fragment):this() {
        register(fragment)
    }

    /**
     * 読み書き用にファイルを選択
     */
    val openFilePicker = UtOpenFilePicker()
    /**
     * 読み書き用に複数ファイルを選択
     */
    val openMultiFilePicker = UtOpenMultiFilePicker()

    /**
     * 読み取り専用にファイルを選択
     */
    val openReadOnlyFilePicker = UtOpenReadOnlyFilePicker()
    /**
     * 読み取り専用に複数ファイルを選択
     */
    val openReadOnlyMultiFilePicker = UtOpenReadOnlyMultiFilePicker()

    /**
     * 名前を付けてファイルを作成
     */
    val createFilePicker = UtCreateFilePicker()

    /**
     * ディレクトリを選択
     */
    val directoryPicker = UtDirectoryPicker()

    override val brokerList: List<IUtActivityBroker>
        = listOf(openFilePicker, openMultiFilePicker, openReadOnlyFilePicker, openReadOnlyMultiFilePicker, createFilePicker, directoryPicker)
}