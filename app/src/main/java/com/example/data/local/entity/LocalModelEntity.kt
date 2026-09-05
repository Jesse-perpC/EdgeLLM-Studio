package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val fileSize: Long,
    val format: String,
    val parameterCount: String,
    val quantization: String,
    val requiredRamBytes: Long,
    val contextLength: Int,
    val description: String,
    val category: String,
    val downloadUrl: String,
    val sha256Checksum: String,
    val isDownloaded: Boolean,
    val downloadProgress: Int = 0,
    val isActive: Boolean = false,
    val isImported: Boolean = false,
    val sourceFolder: String = "",
    val lastModified: Long = System.currentTimeMillis()
)
