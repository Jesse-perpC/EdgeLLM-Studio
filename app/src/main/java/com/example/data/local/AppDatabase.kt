package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BackgroundJobDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.dao.ConversationSessionDao
import com.example.data.local.dao.ExportDao
import com.example.data.local.dao.LocalModelDao
import com.example.data.local.dao.SemanticMemoryDao
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.EncryptedExportEntity
import com.example.data.local.entity.LocalModelEntity
import com.example.data.local.entity.SemanticMemoryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        BackgroundJobEntity::class,
        EncryptedExportEntity::class,
        LocalModelEntity::class,
        ConversationSessionEntity::class,
        SemanticMemoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun backgroundJobDao(): BackgroundJobDao
    abstract fun exportDao(): ExportDao
    abstract fun localModelDao(): LocalModelDao
    abstract fun semanticMemoryDao(): SemanticMemoryDao
    abstract fun conversationSessionDao(): ConversationSessionDao

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
