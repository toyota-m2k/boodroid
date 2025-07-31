package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.IMediaSourceWithChapter
import io.github.toyota32k.lib.player.model.IMutableChapterList
import io.github.toyota32k.lib.player.model.Range
import io.github.toyota32k.lib.player.model.chapter.MutableChapterList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

interface ISizedItem : IMediaSource {
    val size:Long
    val duration:Long
}

class VideoItem private constructor (j: JSONObject, val chapterRetriever: (suspend (VideoItem) -> List<IChapter>)?, private val mutableChapterList: MutableChapterList)
    : IMediaSourceWithChapter, ISizedItem {
    constructor(j: JSONObject, chapters: List<IChapter>) : this(j, null, MutableChapterList(chapters))
    constructor(j: JSONObject, chapterRetriever: suspend (VideoItem) -> List<IChapter>) : this(j, chapterRetriever, MutableChapterList())

    override val id:String
    override val name:String
    override val trimming: Range
    override val type:String             // 拡張子
    override val size:Long
    override val duration: Long
    override var startPosition:AtomicLong = AtomicLong(0L)

    override suspend fun getChapterList(): IChapterList {
        if (chapterRetriever == null) {
            return IChapterList.Empty
        }
        return withContext(Dispatchers.IO) {
            mutableChapterList.apply {
                initChapters(chapterRetriever(this@VideoItem))
            }
        }
    }

    init {
        id = j.getString("id")
        name = j.optString("name", "")
        trimming = Range(j.optLong("start", 0), j.optLong("end", 0))
        type = j.optString("type", "mp4")
        size = j.optLong("size", 0L)
        duration = j.optLong("duration", 0L)
    }

    override val uri:String
        get() = AppViewModel.url.item(id)
}

