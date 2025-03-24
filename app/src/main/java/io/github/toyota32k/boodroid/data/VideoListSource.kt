package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.toIterable
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.lib.player.model.IChapter
import io.github.toyota32k.lib.player.model.IChapterList
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.lib.player.model.chapter.Chapter
import io.github.toyota32k.lib.player.model.chapter.ChapterList
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class VideoListSource(val list:List<VideoItem>, val modifiedDate:Long) {
    suspend fun checkUpdate(date:Long) : Boolean {
        val url = AppViewModel.url.check(date) ?: return false
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            val json = NetClient.executeAndGetJsonAsync(req)
            json.getString("update") == "1"
        } catch(e:Throwable) {
            UtLogger.stackTrace(e)
            return false
        }
    }

    val currentSourceIndex = MutableStateFlow<Int>(-1)
    val hasNext: Flow<Boolean> = currentSourceIndex.map { it<list.size-1 }
    val hasPrevious : Flow<Boolean> = currentSourceIndex.map { it>0 }
    val currentSource : Flow<IMediaSource?> = currentSourceIndex.map { if(it<0||it>=list.size) null else list[it] }

    private val authing = AtomicBoolean(false)
    private fun auth() {
        if(authing.getAndSet(true)) return
        CoroutineScope(Dispatchers.IO).launch {
            AppViewModel.instance.authentication.authentication(false)
            authing.set(false)
        }
    }

    fun setCurrentSource(index:Int, pos:Long=0L) {
        if(list.isEmpty()) {
            currentSourceIndex.value = -1
            return
        }
        auth()
        if (0<=index && index<list.size) {
            list[index].startPosition.set(pos)
            currentSourceIndex.value = index
        } else {
            currentSourceIndex.value = 0
        }
    }
    fun setCurrentSource(item:VideoItem, pos:Long=0L) {
        setCurrentSource(list.indexOf(item), pos)
    }

    fun next() {
        if(currentSourceIndex.value<list.size-1) {
            auth()
            currentSourceIndex.value++
        }
    }

    fun previous() {
        if(currentSourceIndex.value>0) {
            auth()
            currentSourceIndex.value--
        }
    }

    companion object {
        val empty:VideoListSource = VideoListSource(emptyList(), 0)

        suspend fun getChapters(item:VideoItem): List<IChapter> {
            return try {
                val url = AppViewModel.url.chapter(item.id) ?: return emptyList()
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                val json = NetClient.executeAndGetJsonAsync(req)
                json.getJSONArray("chapters").toIterable().map { Chapter(it as JSONObject) }
            } catch(_:Throwable) {
                emptyList()
            }
        }

        suspend fun retrieve(date:Long=0L): VideoListSource? {
            if(!AppViewModel.instance.settings.isValid) return null
            if(!AppViewModel.instance.authentication.authentication()) return null
            val url = AppViewModel.url.list(date)
            val req = Request.Builder()
                .url(url)
                .get()
                .build()

            return try {
                val json = NetClient.executeAndGetJsonAsync(req)
                val lastUpdate = json.getString("date").toLong()
                val jsonList = json.getJSONArray("list")
                    ?: throw IllegalStateException("Server Response Null List.")
                VideoListSource( jsonList.toIterable().filter { (it as JSONObject).optString("media", "")!="p" }.map { j -> VideoItem(j as JSONObject, ::getChapters) }, lastUpdate )
            } catch (e: Throwable) {
                UtLogger.stackTrace(e)
                return null
            }
        }


    }
}