package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.utils.UtLogger
import io.github.toyota32k.video.model.IChapter
import io.github.toyota32k.video.model.IChapterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class Chapter(override val position:Long, override val label:String, override val skip:Boolean): IChapter {
    constructor(j:JSONObject):this(j.getLong("position"), j.getString("label"), j.getBoolean("skip"))
}

class ChapterList(val ownerId:String) : SortedList<IChapter, Long>(10, false,
    keyOf = {e->e.position},
    comparator = { x,y->
        val d = y-x
        if(d<0) -1 else if(d>0) 1 else 0 }), IChapterList {

    private val position = Position()
    override val chapters: List<IChapter> get() = this

    override fun prev(current:Long) : IChapter? {
        find(current, position)
        return if(0<=position.prev&&position.prev<size) this[position.prev] else null
    }

    override fun next(current:Long) : IChapter? {
        find(current, position)
        return if(0<=position.next&&position.next<size) this[position.next] else null
    }

    private fun disabledRangesRaw() = sequence<Range> {
        var skip = false
        var skipStart = 0L

        for (c in this@ChapterList) {
            if (c.skip) {
                if (!skip) {
                    skip = true
                    skipStart = c.position
                }
            } else {
                if (skip) {
                    skip = false
                    yield(Range(skipStart, c.position))
                }
            }
        }
        if(skip) {
            yield (Range(skipStart, 0))
        }

    }

    override fun disabledRanges(trimming:Range) = sequence<Range> {
        var trimStart = trimming.start
        var trimEnd = trimming.end
        for (r in disabledRangesRaw()) {
            if (r.end < trimming.start) {
                // ignore
                continue
            } else if (trimStart > 0) {
                if (r.start < trimStart) {
                    yield(Range(0, r.end))
                    continue
                } else {
                    yield(Range(0, trimStart))
                }
                trimStart = 0
            }

            if (trimEnd > 0) {
                if (trimEnd < r.start) {
                    break
                } else if (trimEnd < r.end) {
                    trimEnd = 0
                    yield(Range(r.start, 0))
                    break
                }
            }
            yield(r)
        }
        if (trimStart > 0) {
            yield(Range(0, trimStart))
        }
        if (trimEnd > 0) {
            yield(Range(trimEnd, 0))
        }
    }

    companion object {
        suspend fun get(ownerId:String): ChapterList? {
            return try {
                val vm = AppViewModel.instance
                val url = vm.settings.urlChapters(ownerId)
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                val json = NetClient.executeAndGetJsonAsync(req)
                // kotlin の reduce は、accumulatorとelementの型が同じ場合（sumみたいなやつ）しか扱えない。というより、accの初期値が、iterator.next()になっているし。
                // 代わりに、fold を使うとうまくいくというハック情報。
                json.getJSONArray("chapters").toIterable().fold<Any, ChapterList>(ChapterList(ownerId)) { acc, c-> acc.apply{ add(Chapter(c as JSONObject)) } }
            } catch(e:Throwable) {
                UtLogger.stackTrace(e)
                null
            }
        }
    }

}

