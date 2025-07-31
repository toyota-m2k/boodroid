package io.github.toyota32k.boodroid.offline

import android.content.Context
import androidx.room.Room
import io.github.toyota32k.boodroid.BooApplication
import io.github.toyota32k.boodroid.data.NetClient
import io.github.toyota32k.boodroid.data.VideoItem
import io.github.toyota32k.lib.player.model.IMediaSource
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.UtObservableFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min

class OfflineManager(context: Context) {
    companion object {
        val logger = UtLog("OLC", BooApplication.logger)
        val instance:OfflineManager get() = BooApplication.instance.offlineManager
        const val DIR_NAME = "offline"

        private fun IMediaSource.keyUrl() : String?
                = when(this) {
            is VideoItem -> uri
            is CachedVideoItem -> id
            else -> null
        }

        private fun File?.safeDelete() {
            try {
                if(this==null) return
                if (this.exists() && this.isFile) {
                    this.delete()
                }
            } catch(e:Throwable) {
                logger.stackTrace(e)
            }
        }
    }
    val database:OfflineDB = Room
            .databaseBuilder(context.applicationContext, OfflineDB::class.java, "offline_db")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration(false)
            .build()

    private val privateDir:File by lazy {
        File(context.filesDir, DIR_NAME).apply {
            if(!exists()) {
                mkdir()
            }
        }
    }
    val busy = UtObservableFlag()

    private fun createLocalFile(type:String):File? {
        return try {
            val ext = if (type.startsWith(".")) type else ".$type"
            File.createTempFile("olc", ext, privateDir)
        } catch(e:Throwable) {
            logger.stackTrace(e)
            null
        }
    }

    private fun getOfflineData(keyUrl:String?):OfflineData? {
        return database.dataTable().getByUrl(keyUrl ?: return null)
    }

    private fun isRegistered(keyUrl:String) : Boolean {
        return getOfflineData(keyUrl) != null
    }
    @Suppress("unused")
    private fun isRegistered(videoItem: IMediaSource):Boolean {
        return isRegistered(videoItem.keyUrl()?: return false)
    }

    private suspend fun registerVideo(videoItem: VideoItem, progress: DownloadProgress?):Boolean {
        logger.debug(videoItem.name)
        val url = videoItem.keyUrl() ?: return false
        if(isRegistered(url)) return false

        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            val videoFile = NetClient.executeAsync(req).use { response ->
                if (response.isSuccessful) {
                    logger.debug("ok: ${videoItem.name}")
                    response.body?.byteStream()?.use { inStream ->
                        createLocalFile(videoItem.type)?.let { file ->
                            try {
                                val totalLength = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
                                file.outputStream().use { outStream ->
//                                    inStream.copyTo(outStream)
                                    var bytesCopied: Long = 0
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var bytes = inStream.read(buffer)
                                    while (bytes >= 0) {
                                        outStream.write(buffer, 0, bytes)
                                        bytesCopied += bytes
                                        progress?.setBytesProgress(totalLength, bytesCopied)
                                        bytes = inStream.read(buffer)
                                    }
                                    outStream.flush()
                                }
                                logger.debug("saved: ${videoItem.name}")
                                file
                            } catch (e: Throwable) {
                                logger.stackTrace(e)
                                file.safeDelete()
                                null
                            }
                        }
                    }
                } else null
            }

            if(videoFile!=null) {
                database.dataTable().insert(OfflineData(url, videoFile.path, videoItem.name, videoItem.trimming.start, videoItem.trimming.end, videoItem.type, 0, 0, videoItem.size, videoItem.duration))
                val list = videoItem.getChapterList()
                if(!list.isEmpty) {
                    database.chapters().insert(* list.chapters.map {
                        ChapterCache(url, it.position, it.label, it.skip)
                    }.toTypedArray())
                }
                logger.debug("registered: ${videoItem.name}")
                true
            } else false
        }
    }

    private suspend fun registerVideos(list:List<VideoItem>, progress:DownloadProgress?) {
        withContext(Dispatchers.IO) {
            progress?.setCountProgress(list.size, 0)
            list.forEachIndexed {index, item ->
                val result = try {
                    progress?.setMessage("Downloading: ${item.name}")
                    registerVideo(item, progress)
                } catch (e: Throwable) {
                    logger.stackTrace(e)
                    false
                }
                progress?.setCountProgress(list.size, index+1)
                if(result) {
                    // 登録成功
                    progress?.setMessage("OK: ${item.name}")
                } else {
                    // 登録（ダウンロード）失敗
                    logger.debug("download error: ${item.name}")
                    progress?.setMessage("NG: ${item.name}")
                    unregisterVideo(item)
                }
            }
        }
    }

    private fun unregisterVideo(videoItem: IMediaSource) {
        logger.debug(videoItem.name)
        val url = videoItem.keyUrl() ?: return
        unregisterVideo(url)
    }

    private fun unregisterVideo(keyUrl: String) {
        try {
            val entry = getOfflineData(keyUrl) ?: return
            File(entry.filePath).safeDelete()
            database.dataTable().deleteByUrl(keyUrl)
            database.chapters().deleteByOwner(keyUrl)
        } catch(e:Throwable) {
            logger.stackTrace(e)
        }
    }

    private fun unregisterVideos(list:List<IMediaSource>, progress: DownloadProgress?) {
        if(list.isEmpty()) return
        progress?.setMessage("Deleting ...")
        database.runInTransaction {
            list.forEach { unregisterVideo(it) }
        }
    }

    private fun updateSortOrder(list:List<IMediaSource>, progress: DownloadProgress?) {
        if(list.isEmpty()) return
        progress?.setMessage("Sorting ...")
        database.runInTransaction {
            list.mapNotNull { it.keyUrl() }.forEachIndexed { index, url ->
                val entry = getOfflineData(url)
                if(entry!=null && entry.sortOrder!=index) {
                    database.dataTable().setSortOrder(url, index)
                }
            }
        }
    }

    fun getOfflineVideos(): List<CachedVideoItem> {
        return database.dataTable().getAll().mapNotNull {
            val file = File(it.filePath)
            if(!file.exists()||!file.isFile) {
                logger.debug("cache file not exists: ${it.name}")
                database.dataTable().deleteByUrl(it.videoUrl)
                null
            } else {
                logger.debug("loading: ${it.name}")
                CachedVideoItem(it, file)
            }
        }
    }

//    fun getOfflinePlayList(): List<IAmvSource> {
//        return getOfflineVideos() as List<IAmvSource>
//    }

    class ListUpdater(private val oldList:List<CachedVideoItem>, private val newList: List<IMediaSource>) {
        val remove = mutableListOf<CachedVideoItem>()
        val append = mutableListOf<VideoItem>()

        init {
            oldList.fold(remove) { acc, old->
                if(newList.firstOrNull { new-> new.keyUrl() == old.keyUrl() }==null) {
                    // oldListにあって、newListにはない --> 削除
                    acc.add(old)
                }
                acc
            }
            newList.fold(append) { acc, new ->
                if( new is VideoItem && oldList.firstOrNull{ old-> old.keyUrl() == new.keyUrl() }==null) {
                    // newListにあって、oldListにはない --> 追加
                    acc.add(new)
                }
                acc
            }
        }
    }

    open class DownloadProgress {
        val showProgressBar = MutableStateFlow(false)
        val message = MutableStateFlow<String>("")
        val count = MutableStateFlow<Int>(0)
        val index = MutableStateFlow<Int>(0)
        val length = MutableStateFlow<Long>(0L)
        val received = MutableStateFlow<Long>(0L)

        val percentInCount:Flow<Int> = combine(count,index) { c,i->
            if(c>0) {
                max(0,min(i*100 / c, 100))
            } else 0
        }
        val percentInBytes:Flow<Int> = combine(length, received) { l, r ->
            if(l>0) {
                max(0, min((r*100/l).toInt(), 100))
            } else 0
        }

        fun reset() {
            showProgressBar.value = false
            count.value = 0
            index.value = 0
            length.value = 0
            received.value = 0
        }

        fun setMessage(msg:String) {
            message.value = msg
        }
        fun setCountProgress(totalCount:Int, currentIndex:Int) {
            count.value = totalCount
            index.value = currentIndex
            if(totalCount>0) {
                if(currentIndex==0) {
                    showProgressBar.value = true
                } else if(currentIndex==totalCount) {
                    showProgressBar.value = false
                }
            }
        }
        fun setBytesProgress(contentLength:Long, receivedBytes:Long) {
            length.value = contentLength
            received.value = receivedBytes
        }
    }

    suspend fun setOfflineVideos(newList: List<IMediaSource>, progress:DownloadProgress?) : List<CachedVideoItem>? {
        progress?.reset()
        return busy.closeableTrySetIfNot()?.use {
            withContext(Dispatchers.IO) {
                val updater = ListUpdater(getOfflineVideos(), newList)
                unregisterVideos(updater.remove, progress)
                registerVideos(updater.append, progress)
                updateSortOrder(newList, progress)
                cleanup()
                getOfflineVideos()
            }
        }
    }

    suspend fun updateFilter(list:List<CachedVideoItem>) {
        withContext(Dispatchers.IO) {
            database.runInTransaction {
                list.forEachIndexed { index, item ->
                    val entry = getOfflineData(item.keyUrl())
                    if (entry != null && (entry.sortOrder != index || entry.filter != item.filter)) {
                        database.dataTable().setFilterAndSortOrder(entry.videoUrl, item.filter, index)
                    }
                }
            }
        }
    }


    fun cleanup() {
        val items = getOfflineVideos()
        val files = privateDir.listFiles()
        files?.forEach { file->
            if(file.isFile && items.find { item->item.file.path == file.path} == null) {
                // DB管理外のファイル
                file.safeDelete()
            }
        }
    }
}