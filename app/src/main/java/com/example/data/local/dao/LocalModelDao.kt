package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LocalModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY isActive DESC, name ASC")
    fun getAllModels(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getModelById(id: String): LocalModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<LocalModelEntity>)

    @Update
    suspend fun updateModel(model: LocalModelEntity)

    @Query("UPDATE local_models SET isDownloaded = :isDownloaded, downloadProgress = :progress, path = :path, fileSize = :fileSize WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, isDownloaded: Boolean, progress: Int, path: String, fileSize: Long)

    @Query("UPDATE local_models SET isActive = (id = :activeId)")
    suspend fun setActiveModel(activeId: String)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("DELETE FROM local_models")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM local_models")
    suspend fun getCount(): Int
}
