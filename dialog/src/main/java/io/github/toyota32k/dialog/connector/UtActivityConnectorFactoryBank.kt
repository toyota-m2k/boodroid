package io.github.toyota32k.dialog.connector

import io.github.toyota32k.dialog.UtDialogOwner

/**
 * ImmortalTask 用 ActivityConnector の生成情報をストックしておくクラス。
 * 通常、Activity|Fragment の companion object 内でインスタンス化しておく。
 * @param factoryList   ActivityConnectorFactoryの配列。ImmortalTask で使用するすべての ActivityConnector のファクトリの配列を渡す。
 */
class UtActivityConnectorFactoryBank(private val factoryList:Array<ActivityConnectorFactory<*, *>>) {

    /**
     * ファクトリクラスの定義
     * UtActivityConnector 継承クラス毎に実装する。
     */
    abstract class ActivityConnectorFactory<I,O>(
        val key: UtActivityConnectorKey,
        val defArg:I
    ) {
        /**
         * ActivityConnectorを生成
         * @param owner     Activity|Fragment
         */
        abstract fun createActivityConnector(owner: UtDialogOwner): UtActivityConnector<I, O>
    }

    /**
     * ActivityConnectorマップを生成
     */
    private fun createConnectors(owner: UtDialogOwner) : Map<UtActivityConnectorKey, UtActivityConnector<*, *>> {
        return mutableMapOf<UtActivityConnectorKey, UtActivityConnector<*, *>>().also { map ->
            for (g in factoryList) {
                map[g.key] = g.createActivityConnector(owner)
            }
        }
    }

    /**
     * UtActivityConnectorStore を作成
     * @param owner Activity|Fragment
     */
    fun createConnectorStore(owner:UtDialogOwner): UtActivityConnectorStore {
        return UtActivityConnectorStore(createConnectors(owner))
    }
}