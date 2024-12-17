package io.github.toyota32k.boodroid.offline

import androidx.core.net.toUri
import io.github.toyota32k.boodroid.data.ISizedItem
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class CachedVideoItem(
    override val id: String,
    override val name: String,
    override val trimming: Range,
    override val type: String,
    val file: File,
    var filter: Int,
    override val size: Long,
    override val duration: Long,
) : IMediaSourceWithChapter, ISizedItem {
    constructor(offlineData: OfflineData, file:File): this(offlineData.videoUrl, offlineData.name?:"", Range(offlineData.trimmingStart, offlineData.trimmingEnd), offlineData.type?:"mp4", file, offlineData.filter, offlineData.size, offlineData.duration)
    companion object {
        val idRegex:Regex by lazy { Regex("ytplayer/video\\?=(.*)") }
    }
    override val uri: String
        get() = file.toUri().toString()
    override var startPosition =  AtomicLong(0L)

//        get() {
//            val g = idRegex.find(uri)?.groups
//            return if(g!=null && g.size>1) {
//                g[1]?.value ?: throw IllegalStateException("regex error?")
//            } else throw InvalidParameterException("no id in url")
//        }

    override val chapterList = ChapterList(OfflineManager.instance.database.chapters().getForOwner(id).map { Chapter(it.position, it.label ?: "", it.skip) }.toMutableList())
}