package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.EncryptedExportEntity
import com.example.data.local.entity.SemanticMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessionMessages(sessionId: String, limit: Int = 10): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    suspend fun getAllMessagesSnapshot(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface SemanticMemoryDao {
    @Query("SELECT * FROM semantic_memories ORDER BY lastRecalledAt DESC, importanceScore DESC")
    fun getAllMemories(): Flow<List<SemanticMemoryEntity>>

    @Query("SELECT * FROM semantic_memories ORDER BY importanceScore DESC")
    suspend fun getAllMemoriesSnapshot(): List<SemanticMemoryEntity>

    @Query("SELECT * FROM semantic_memories WHERE memoryType = :memoryType ORDER BY importanceScore DESC")
    fun getMemoriesByType(memoryType: String): Flow<List<SemanticMemoryEntity>>

    @Query("SELECT * FROM semantic_memories WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getMemoriesBySession(sessionId: String): Flow<List<SemanticMemoryEntity>>

    @Query("SELECT * FROM semantic_memories WHERE subject LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY importanceScore DESC")
    fun searchMemoriesByText(query: String): Flow<List<SemanticMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: SemanticMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<SemanticMemoryEntity>)

    @Query("UPDATE semantic_memories SET lastRecalledAt = :lastRecalled, recallCount = :newCount WHERE id = :id")
    suspend fun updateRecall(id: String, lastRecalled: Long, newCount: Int)

    @Query("DELETE FROM semantic_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM semantic_memories")
    suspend fun clearAllMemories()

    @Query("SELECT COUNT(*) FROM semantic_memories")
    suspend fun getCount(): Int
}

@Dao
interface ConversationSessionDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY lastActiveAt DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET contextSummary = :summary, messageCount = :messageCount, totalTokens = :tokens, lastActiveAt = :lastActive WHERE id = :id")
    suspend fun updateSessionStats(id: String, summary: String, messageCount: Int, tokens: Int, lastActive: Long)

    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM conversation_sessions")
    suspend fun clearAllSessions()
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
