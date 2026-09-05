package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.EncryptedExportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface BackgroundJobDao {
    @Query("SELECT * FROM background_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<BackgroundJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: BackgroundJobEntity)

    @Query("UPDATE background_jobs SET status = :status, progressPercent = :progress, resultSummary = :result, completedAt = :completedAt, tokensProcessed = :tokens, executionTimeMs = :execTime WHERE id = :id")
    suspend fun updateJobProgress(
        id: String,
        status: String,
        progress: Int,
        result: String,
        completedAt: Long?,
        tokens: Int,
        execTime: Long
    )

    @Query("DELETE FROM background_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)

    @Query("DELETE FROM background_jobs")
    suspend fun clearAllJobs()
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM encrypted_exports ORDER BY createdAt DESC")
    fun getAllExports(): Flow<List<EncryptedExportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(export: EncryptedExportEntity)

    @Query("DELETE FROM encrypted_exports WHERE id = :id")
    suspend fun deleteExport(id: String)
}
