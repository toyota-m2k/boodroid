/**
 * Cache object i/f
 *
 * @author M.TOYOTA 2018.07.27 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package io.github.toyota32k.player.cache

import android.net.Uri
import io.github.toyota32k.video.common.AmvError
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * キャッシュマネージャが管理するキャッシュクラスのi/f定義
 */
interface IAmvCache
{
    /**
     * キャッシュファイルを取得する。
     * @param callback  結果を返すコールバック fn(sender, file)    エラー発生時はfile==null / sender.error でエラー情報を取得
     */
    // for Kotlin
    suspend fun getFileAsync() : File?

    /**
     * エラー情報
     */
    val error: AmvError

    /**
     * 参照カウンタを下げる・・・キャッシュを解放（CacheManagerによる削除）可能な状態にする
     */
    fun release() : Int

    /**
     * 参照カウンタを上げる
     */
    fun addRef()

    /**
     * 参照カウンタの値を取得
     */
    val refCount: Int

    /**
     * キャッシュを無効化する
     */
    val invalidated:Boolean
    fun invalidate()

    /**
     * ダウンロードをキャンセル
     * ダウンロード中でなければ何もしない。副作用なし。
     */
    fun cancel()

    // for percent
    // combine(receivedBytes,totalBytes) {r,t-> r*100/t }
    var receivedBytes: MutableStateFlow<Long>?
    var totalBytes: MutableStateFlow<Long>?

    /**
     * 呼び出し時点で取得しているキャッシュファイルを取得
     * ダウンロード中、または、Invalidateされているときは、nullを返す。
     */
    val cacheFile : File?

    /**
     * ターゲットの URI を取得
     */
    val uri : Uri?

    /**
     * 参照キー
     */
    val key : String
}
