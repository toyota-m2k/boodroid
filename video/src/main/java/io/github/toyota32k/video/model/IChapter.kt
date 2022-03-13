package io.github.toyota32k.video.model

import io.github.toyota32k.player.model.Range

interface IChapter {
    val position:Long
    val label:String
    val skip:Boolean
}

interface IChapterList {
    val chapters:List<IChapter>
    fun prev(current:Long) : IChapter?
    fun next(current:Long) : IChapter?
    fun disabledRanges(trimming: Range) : Sequence<Range>
}