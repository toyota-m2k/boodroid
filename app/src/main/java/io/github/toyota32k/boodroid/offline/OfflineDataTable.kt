package io.github.toyota32k.boodroid.offline

import androidx.room.*

@Entity(tableName = "t_offline_data")
data class OfflineData (
    @PrimaryKey
    val videoUrl:String,
    val filePath:String,
    val name:String?,
    val trimmingStart:Long,
    val trimmingEnd:Long,
    val type:String?,
    val sortOrder:Int,
    val filter:Int,
    )

@Dao
interface OfflineDataTable {
    @Query("SELECT * from t_offline_data ORDER BY sortOrder ASC")
    fun getAll(): List<OfflineData>

    @Query("SELECT * from t_offline_data WHERE videoUrl=:url")
    fun getByUrl(url:String): List<OfflineData>

    @Query("DELETE from t_offline_data WHERE videoUrl=:url")
    fun deleteByUrl(url:String)

    @Query("UPDATE t_offline_data SET sortOrder=:order WHERE videoUrl=:url")
    fun setSortOrder(url:String, order:Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg offlineData:OfflineData)

    @Delete
    fun delete(vararg offlineData: OfflineData)

    @Update
    fun update(vararg offlineData: OfflineData)
}