package io.github.toyota32k.boodroid.offline

import androidx.room.*

@Entity(tableName = "t_chapter_cache", primaryKeys = ["videoUrl", "position"])
data class ChapterCache(
    val videoUrl:String,       // OfflineData.videoUrl
    val position:Long,
    val label:String?,
    val skip:Boolean
)
@Dao
interface ChapterCacheTable {
    @Query("SELECT * from t_chapter_cache WHERE videoUrl=:videoUrl ORDER BY position ASC")
    fun getForOwner(videoUrl:String): List<ChapterCache>

    @Query("DELETE from t_chapter_cache WHERE videoUrl=:videoUrl")
    fun deleteByOwner(videoUrl:String)

    @Delete
    fun delete(vararg cache:ChapterCache)

    @Update
    fun update(vararg cache:ChapterCache)

    @Insert
    fun insert(vararg cache:ChapterCache)
}