package io.github.toyota32k.boodroid.offline

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OfflineData::class, ChapterCache::class], version = 1, exportSchema = false)
abstract class OfflineDB : RoomDatabase() {
    abstract fun dataTable(): OfflineDataTable
    abstract fun chapters(): ChapterCacheTable
}