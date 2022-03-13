package io.github.toyota32k.video.common

import io.github.toyota32k.video.model.IChapterList
import io.github.toyota32k.player.model.Range

interface IAmvSource {
    val id:String
    val uri:String
    val trimming: Range
    val chapterList: IChapterList?
    val disabledRanges:List<Range> get() = chapterList?.disabledRanges(trimming)?.toList() ?: emptyList()
    val hasChapter:Boolean get() = (chapterList?.chapters?.size ?: 0)>0
}

