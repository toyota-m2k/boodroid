package io.github.toyota32k.video.common

import io.github.toyota32k.video.model.IChapterList
import io.github.toyota32k.player.model.Range

interface IAmvSource {
    val id:String
    val name:String
    val uri:String
    val trimming: Range
    val type: String    // 拡張子(.なし）
    suspend fun getChapterList():IChapterList?

    fun disabledRanges(chapterList:IChapterList?):List<Range>? {
        return chapterList?.disabledRanges(trimming)?.toList()
    }

//    val chapterList: IChapterList?
//    val disabledRanges:List<Range> get() = chapterList?.disabledRanges(trimming)?.toList() ?: emptyList()
//    val hasChapter:Boolean get() = (chapterList?.chapters?.size ?: 0)>0
}

