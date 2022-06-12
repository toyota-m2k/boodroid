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

data class VideoItem(
    override val id:String,
    override val name:String,
    override val trimming: Range,
    override val type:String,             // 拡張子
    ) :IAmvSource {
    internal constructor(j: JSONObject)
    : this(
        j.getString("id"),
        j.safeGetString("name"),
        Range(j.safeGetLong("start", 0), j.safeGetLong("end", 0)),
        j.safeGetString("type", "mp4"),
    )
    override val uri:String
        get() = AppViewModel.instance.settings.videoUrl(id)

    override suspend fun getChapterList(): IChapterList? {
        return ChapterList.get(id)
    }
}

