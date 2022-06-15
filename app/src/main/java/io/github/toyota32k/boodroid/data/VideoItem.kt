package io.github.toyota32k.boodroid.data

import io.github.toyota32k.boodroid.common.safeGetInt
import io.github.toyota32k.boodroid.common.safeGetLong
import io.github.toyota32k.boodroid.common.safeGetNullableString
import io.github.toyota32k.boodroid.common.safeGetString
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.video.common.IAmvSource
import io.github.toyota32k.video.model.IChapterList
import org.json.JSONObject

interface ISizedItem : IAmvSource {
    val size:Long
    val duration:Long
}

data class VideoItem(
    override val id:String,
    override val name:String,
    override val trimming: Range,
    override val type:String,             // 拡張子
    override val size:Long,
    override val duration: Long,
    ) :IAmvSource, ISizedItem {
    internal constructor(j: JSONObject)
    : this(
        j.getString("id"),
        j.optString("name", ""),
        Range(j.optLong("start", 0), j.optLong("end", 0)),
        j.optString("type", "mp4"),
        j.optLong("size", 0L),
        j.optLong("duration", 0L),
    )
    override val uri:String
        get() = AppViewModel.instance.settings.videoUrl(id)

    override suspend fun getChapterList(): IChapterList? {
        return ChapterList.get(id)
    }
}

