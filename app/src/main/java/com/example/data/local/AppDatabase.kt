package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BackgroundJobDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.dao.ExportDao
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.EncryptedExportEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        BackgroundJobEntity::class,
        EncryptedExportEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun backgroundJobDao(): BackgroundJobDao
    abstract fun exportDao(): ExportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edgellm_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
