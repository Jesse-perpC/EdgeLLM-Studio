package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.EncryptedExportEntity
import com.example.data.local.entity.LocalModelEntity
import com.example.data.model.BackgroundJob
import com.example.data.model.CloudStorageTarget
import com.example.data.model.EncryptedExportRecord
import com.example.data.model.InferenceMessage
import com.example.data.model.JobStatus
import com.example.data.model.JobType
import com.example.data.model.MessageSender
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EdgeLLMRepository(private val database: AppDatabase) {

    val localModels: Flow<List<ModelSpec>> = database.localModelDao().getAllModels().map { entities ->
        entities.map { entityToModelSpec(it) }
    }

    val chatMessages: Flow<List<InferenceMessage>> = database.chatDao().getAllMessages().map { entities ->
        entities.map { entity ->
            InferenceMessage(
                id = entity.id,
                sender = try { MessageSender.valueOf(entity.sender) } catch (e: Exception) { MessageSender.USER },
                text = entity.text,
                timestamp = entity.timestamp,
                tokensGenerated = entity.tokensGenerated,
                tokensPerSecond = entity.tokensPerSecond,
                timeToFirstTokenMs = entity.timeToFirstTokenMs,
                executionBackend = entity.executionBackend,
                modelId = entity.modelId
            )
        }
    }

    val backgroundJobs: Flow<List<BackgroundJob>> = database.backgroundJobDao().getAllJobs().map { entities ->
        entities.map { entity ->
            BackgroundJob(
                id = entity.id,
                title = entity.title,
                jobType = try { JobType.valueOf(entity.jobType) } catch (e: Exception) { JobType.LOG_ANOMALY_SCAN },
                inputData = entity.inputData,
                status = try { JobStatus.valueOf(entity.status) } catch (e: Exception) { JobStatus.QUEUED },
                progressPercent = entity.progressPercent,
                resultSummary = entity.resultSummary,
                errorMessage = entity.errorMessage,
                createdAt = entity.createdAt,
                completedAt = entity.completedAt,
                tokensProcessed = entity.tokensProcessed,
                executionTimeMs = entity.executionTimeMs
            )
        }
    }

    val encryptedExports: Flow<List<EncryptedExportRecord>> = database.exportDao().getAllExports().map { entities ->
        entities.map { entity ->
            EncryptedExportRecord(
                id = entity.id,
                title = entity.title,
                sourceType = entity.sourceType,
                plainSizeBytes = entity.plainSizeBytes,
                cipherSizeBytes = entity.cipherSizeBytes,
                algorithm = entity.algorithm,
                sha256Hash = entity.sha256Hash,
                ivBase64 = entity.ivBase64,
                ciphertextPreview = entity.ciphertextPreview,
                cloudTarget = try { CloudStorageTarget.valueOf(entity.cloudTarget) } catch (e: Exception) { CloudStorageTarget.GOOGLE_DRIVE },
                temporaryShareUrl = entity.temporaryShareUrl,
                temporaryShareToken = entity.temporaryShareToken,
                temporaryShareExpiresAt = entity.temporaryShareExpiresAt,
                isUploaded = entity.isUploaded,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun insertMessage(message: InferenceMessage) {
        database.chatDao().insertMessage(
            ChatMessageEntity(
                id = message.id,
                sender = message.sender.name,
                text = message.text,
                timestamp = message.timestamp,
                tokensGenerated = message.tokensGenerated,
                tokensPerSecond = message.tokensPerSecond,
                timeToFirstTokenMs = message.timeToFirstTokenMs,
                executionBackend = message.executionBackend,
                modelId = message.modelId
            )
        )
    }

    suspend fun deleteChatMessage(id: String) {
        database.chatDao().deleteMessage(id)
    }

    suspend fun clearChat() {
        database.chatDao().clearHistory()
    }

    suspend fun insertJob(job: BackgroundJob) {
        database.backgroundJobDao().insertJob(
            BackgroundJobEntity(
                id = job.id,
                title = job.title,
                jobType = job.jobType.name,
                inputData = job.inputData,
                status = job.status.name,
                progressPercent = job.progressPercent,
                resultSummary = job.resultSummary,
                errorMessage = job.errorMessage,
                createdAt = job.createdAt,
                completedAt = job.completedAt,
                tokensProcessed = job.tokensProcessed,
                executionTimeMs = job.executionTimeMs
            )
        )
    }

    suspend fun updateJobProgress(
        id: String,
        status: JobStatus,
        progress: Int,
        resultSummary: String,
        completedAt: Long?,
        tokens: Int,
        executionTimeMs: Long
    ) {
        database.backgroundJobDao().updateJobProgress(
            id = id,
            status = status.name,
            progress = progress,
            result = resultSummary,
            completedAt = completedAt,
            tokens = tokens,
            execTime = executionTimeMs
        )
    }

    suspend fun deleteJob(id: String) {
        database.backgroundJobDao().deleteJob(id)
    }

    suspend fun insertExport(export: EncryptedExportRecord) {
        database.exportDao().insertExport(
            EncryptedExportEntity(
                id = export.id,
                title = export.title,
                sourceType = export.sourceType,
                plainSizeBytes = export.plainSizeBytes,
                cipherSizeBytes = export.cipherSizeBytes,
                algorithm = export.algorithm,
                sha256Hash = export.sha256Hash,
                ivBase64 = export.ivBase64,
                ciphertextPreview = export.ciphertextPreview,
                cloudTarget = export.cloudTarget.name,
                temporaryShareUrl = export.temporaryShareUrl,
                temporaryShareToken = export.temporaryShareToken,
                temporaryShareExpiresAt = export.temporaryShareExpiresAt,
                isUploaded = export.isUploaded,
                createdAt = export.createdAt
            )
        )
    }

    suspend fun deleteExport(id: String) {
        database.exportDao().deleteExport(id)
    }

    // --- Local Model Metadata Persistence (Room) ---

    suspend fun insertModel(model: ModelSpec) {
        database.localModelDao().insertModel(modelSpecToEntity(model))
    }

    suspend fun insertAllModels(models: List<ModelSpec>) {
        database.localModelDao().insertAll(models.map { modelSpecToEntity(it) })
    }

    suspend fun updateModel(model: ModelSpec) {
        database.localModelDao().updateModel(modelSpecToEntity(model))
    }

    suspend fun updateModelDownloadStatus(id: String, isDownloaded: Boolean, progress: Int, path: String, fileSize: Long) {
        database.localModelDao().updateDownloadStatus(id, isDownloaded, progress, path, fileSize)
    }

    suspend fun setActiveModel(activeId: String) {
        database.localModelDao().setActiveModel(activeId)
    }

    suspend fun deleteModel(id: String) {
        database.localModelDao().deleteModel(id)
    }

    suspend fun getModelCount(): Int {
        return database.localModelDao().getCount()
    }

    suspend fun getModelById(id: String): ModelSpec? {
        val entity = database.localModelDao().getModelById(id) ?: return null
        return entityToModelSpec(entity)
    }

    companion object {
        fun modelSpecToEntity(spec: ModelSpec): LocalModelEntity {
            return LocalModelEntity(
                id = spec.id,
                name = spec.name,
                path = spec.localFilePath,
                fileSize = spec.fileSizeBytes,
                format = spec.format.name,
                parameterCount = spec.parameterCount,
                quantization = spec.quantization,
                requiredRamBytes = spec.requiredRamBytes,
                contextLength = spec.contextLength,
                description = spec.description,
                category = spec.category.name,
                downloadUrl = spec.downloadUrl,
                sha256Checksum = spec.sha256Checksum,
                isDownloaded = spec.isDownloaded,
                downloadProgress = spec.downloadProgressPercent,
                isActive = spec.isActive,
                isImported = spec.isImported,
                sourceFolder = spec.sourceFolder,
                lastModified = System.currentTimeMillis()
            )
        }

        fun entityToModelSpec(entity: LocalModelEntity): ModelSpec {
            val format = try {
                ModelFormat.valueOf(entity.format)
            } catch (_: Exception) {
                ModelFormat.GGUF
            }
            val category = try {
                ModelCategory.valueOf(entity.category)
            } catch (_: Exception) {
                ModelCategory.CHAT_REASONING
            }

            return ModelSpec(
                id = entity.id,
                name = entity.name,
                parameterCount = entity.parameterCount,
                format = format,
                quantization = entity.quantization,
                fileSizeBytes = entity.fileSize,
                requiredRamBytes = entity.requiredRamBytes,
                contextLength = entity.contextLength,
                description = entity.description,
                category = category,
                downloadUrl = entity.downloadUrl,
                sha256Checksum = entity.sha256Checksum,
                isDownloaded = entity.isDownloaded,
                downloadProgressPercent = entity.downloadProgress,
                isDownloading = false,
                isPaused = false,
                downloadSpeedFormatted = if (entity.isDownloaded) "Ready" else "",
                downloadedBytes = if (entity.isDownloaded) entity.fileSize else (entity.fileSize * (entity.downloadProgress / 100.0)).toLong(),
                downloadStatusText = if (entity.isDownloaded) "Installed locally" else "Available for download",
                isActive = entity.isActive,
                isImported = entity.isImported,
                localFilePath = entity.path,
                sourceFolder = entity.sourceFolder
            )
        }
    }
}
