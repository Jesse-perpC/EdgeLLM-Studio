package com.perpcorp.edgellm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.perpcorp.edgellm.data.local.dao.AgentFeedbackDao
import com.perpcorp.edgellm.data.local.dao.BackgroundJobDao
import com.perpcorp.edgellm.data.local.dao.ChatDao
import com.perpcorp.edgellm.data.local.dao.ConversationSessionDao
import com.perpcorp.edgellm.data.local.dao.ExportDao
import com.perpcorp.edgellm.data.local.dao.LocalModelDao
import com.perpcorp.edgellm.data.local.dao.SemanticMemoryDao
import com.perpcorp.edgellm.data.local.entity.AgentFeedbackLogEntity
import com.perpcorp.edgellm.data.local.entity.BackgroundJobEntity
import com.perpcorp.edgellm.data.local.entity.ChatMessageEntity
import com.perpcorp.edgellm.data.local.entity.ConversationSessionEntity
import com.perpcorp.edgellm.data.local.entity.EncryptedExportEntity
import com.perpcorp.edgellm.data.local.entity.LocalModelEntity
import com.perpcorp.edgellm.data.local.entity.SemanticMemoryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        BackgroundJobEntity::class,
        EncryptedExportEntity::class,
        LocalModelEntity::class,
        ConversationSessionEntity::class,
        SemanticMemoryEntity::class,
        AgentFeedbackLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun backgroundJobDao(): BackgroundJobDao
    abstract fun exportDao(): ExportDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun semanticMemoryDao(): SemanticMemoryDao
    abstract fun conversationSessionDao(): ConversationSessionDao
    abstract fun agentFeedbackDao(): AgentFeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edgellm_database.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
