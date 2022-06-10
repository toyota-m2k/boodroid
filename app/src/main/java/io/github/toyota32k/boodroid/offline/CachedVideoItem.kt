package io.github.toyota32k.boodroid.offline

import androidx.core.net.toUri
import io.github.toyota32k.boodroid.data.Chapter
import io.github.toyota32k.boodroid.data.ChapterList
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.video.common.IAmvSource
import io.github.toyota32k.video.model.IChapterList
import java.io.File
import java.security.InvalidParameterException
import java.util.*

class CachedVideoItem(
    override val id: String,
    override val name: String,
    override val trimming: Range,
    override val type: String,
    val file: File,
    var filter: Int,
) : IAmvSource {
    constructor(offlineData: OfflineData, file:File): this(offlineData.videoUrl, offlineData.name?:"", Range(offlineData.trimmingStart, offlineData.trimmingEnd), offlineData.type?:"mp4", file, offlineData.filter)
    companion object {
        val idRegex:Regex by lazy { Regex("ytplayer/video\\?=(.*)") }
    }
    override val uri: String
        get() = file.toUri().toString()

//        get() {
//            val g = idRegex.find(uri)?.groups
//            return if(g!=null && g.size>1) {
//                g[1]?.value ?: throw IllegalStateException("regex error?")
//            } else throw InvalidParameterException("no id in url")
//        }

    override suspend fun getChapterList(): IChapterList {
        val database = OfflineManager.instance.database
        return database.chapters().getForOwner(id).fold(ChapterList(id)) { acc, item->
            acc.apply { add(Chapter(item.position, item.label ?: "", item.skip)) }
        }
    }
}