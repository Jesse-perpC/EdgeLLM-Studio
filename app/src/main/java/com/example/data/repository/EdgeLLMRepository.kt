package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.BackgroundJobEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.EncryptedExportEntity
import com.example.data.local.entity.LocalModelEntity
import com.example.data.local.entity.SemanticMemoryEntity
import com.example.data.memory.VectorEmbeddingEngine
import com.example.data.model.BackgroundJob
import com.example.data.model.CloudStorageTarget
import com.example.data.model.ConversationSession
import com.example.data.model.EncryptedExportRecord
import com.example.data.model.InferenceMessage
import com.example.data.model.JobStatus
import com.example.data.model.JobType
import com.example.data.model.MemoryType
import com.example.data.model.MessageSender
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.data.model.SemanticMemoryRecord
import com.example.data.model.SemanticSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

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
                modelId = entity.modelId,
                imageUri = entity.imageUri,
                imageLabel = entity.imageLabel,
                sessionId = entity.sessionId,
                thoughtChain = entity.thoughtTrace,
                conversationalContext = entity.conversationalContext,
                embeddingVector = VectorEmbeddingEngine.stringToVector(entity.embeddingVectorJson),
                importanceScore = entity.importanceScore,
                semanticTags = entity.semanticTags
            )
        }
    }

    val semanticMemories: Flow<List<SemanticMemoryRecord>> = database.semanticMemoryDao().getAllMemories().map { entities ->
        entities.map { entity ->
            SemanticMemoryRecord(
                id = entity.id,
                sessionId = entity.sessionId,
                sourceMessageId = entity.sourceMessageId,
                memoryType = try { MemoryType.valueOf(entity.memoryType) } catch (_: Exception) { MemoryType.FACT },
                subject = entity.subject,
                content = entity.content,
                embeddingVector = VectorEmbeddingEngine.stringToVector(entity.embeddingVector),
                embeddingDimension = entity.embeddingDimension,
                importanceScore = entity.importanceScore,
                recallCount = entity.recallCount,
                createdAt = entity.createdAt,
                lastRecalledAt = entity.lastRecalledAt
            )
        }
    }

    val conversationSessions: Flow<List<ConversationSession>> = database.conversationSessionDao().getAllSessions().map { entities ->
        entities.map { entity ->
            ConversationSession(
                id = entity.id,
                title = entity.title,
                contextSummary = entity.contextSummary,
                activePersonaId = entity.activePersonaId,
                messageCount = entity.messageCount,
                totalTokens = entity.totalTokens,
                createdAt = entity.createdAt,
                lastActiveAt = entity.lastActiveAt
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
        val vector = if (message.embeddingVector.isNotEmpty()) {
            message.embeddingVector
        } else {
            VectorEmbeddingEngine.generateEmbedding(message.text)
        }

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
                modelId = message.modelId,
                imageUri = message.imageUri,
                imageLabel = message.imageLabel,
                sessionId = message.sessionId,
                thoughtTrace = message.thoughtChain,
                conversationalContext = message.conversationalContext,
                embeddingVectorJson = VectorEmbeddingEngine.vectorToString(vector),
                importanceScore = message.importanceScore,
                semanticTags = message.semanticTags
            )
        )

        // Automatically extract and persist semantic memory facts and user preferences from user turns
        if (message.sender == MessageSender.USER && message.text.length >= 8 && !message.text.startsWith("/")) {
            val extracted = VectorEmbeddingEngine.extractSemanticMemoriesFromTurn(
                userText = message.text,
                assistantText = "",
                sessionId = message.sessionId,
                sourceMessageId = message.id
            )
            for (mem in extracted) {
                insertSemanticMemory(mem)
            }
        }
    }

    // --- Semantic Memory & Vector Search (Room) ---

    suspend fun searchSemanticMemories(
        query: String,
        topK: Int = 5,
        threshold: Float = 0.35f
    ): List<SemanticSearchResult> {
        val queryVector = VectorEmbeddingEngine.generateEmbedding(query)
        val allEntities = database.semanticMemoryDao().getAllMemoriesSnapshot()

        val results = allEntities.mapNotNull { entity ->
            val memVector = VectorEmbeddingEngine.stringToVector(entity.embeddingVector)
            val sim = VectorEmbeddingEngine.computeCosineSimilarity(queryVector, memVector)
            if (sim >= threshold) {
                val record = SemanticMemoryRecord(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    sourceMessageId = entity.sourceMessageId,
                    memoryType = try { MemoryType.valueOf(entity.memoryType) } catch (_: Exception) { MemoryType.FACT },
                    subject = entity.subject,
                    content = entity.content,
                    embeddingVector = memVector,
                    embeddingDimension = entity.embeddingDimension,
                    importanceScore = entity.importanceScore,
                    recallCount = entity.recallCount,
                    createdAt = entity.createdAt,
                    lastRecalledAt = entity.lastRecalledAt
                )
                SemanticSearchResult(
                    memory = record,
                    similarityScore = sim,
                    matchedExcerpt = entity.content
                )
            } else null
        }.sortedByDescending { it.similarityScore }
            .take(topK)

        // Update recall statistics for top matched memories in Room
        val now = System.currentTimeMillis()
        for (res in results) {
            database.semanticMemoryDao().updateRecall(
                id = res.memory.id,
                lastRecalled = now,
                newCount = res.memory.recallCount + 1
            )
        }

        return results
    }

    suspend fun getRecalledContextForPrompt(prompt: String): List<String> {
        val searchResults = searchSemanticMemories(prompt, topK = 3, threshold = 0.45f)
        return searchResults.map { result ->
            val matchPercent = (result.similarityScore * 100).toInt()
            "[${result.memory.memoryType.displayName}] ${result.memory.content} (Semantic Match: $matchPercent%)"
        }
    }

    suspend fun insertSemanticMemory(record: SemanticMemoryRecord) {
        val vectorStr = if (record.embeddingVector.isNotEmpty()) {
            VectorEmbeddingEngine.vectorToString(record.embeddingVector)
        } else {
            VectorEmbeddingEngine.vectorToString(VectorEmbeddingEngine.generateEmbedding(record.content))
        }

        database.semanticMemoryDao().insertMemory(
            SemanticMemoryEntity(
                id = record.id,
                sessionId = record.sessionId,
                sourceMessageId = record.sourceMessageId,
                memoryType = record.memoryType.name,
                subject = record.subject,
                content = record.content,
                embeddingVector = vectorStr,
                embeddingDimension = record.embeddingDimension,
                importanceScore = record.importanceScore,
                recallCount = record.recallCount,
                createdAt = record.createdAt,
                lastRecalledAt = record.lastRecalledAt
            )
        )
    }

    suspend fun deleteSemanticMemory(id: String) {
        database.semanticMemoryDao().deleteMemory(id)
    }

    suspend fun clearAllSemanticMemories() {
        database.semanticMemoryDao().clearAllMemories()
    }

    suspend fun seedInitialMemoriesIfEmpty() {
        if (database.semanticMemoryDao().getCount() == 0) {
            val defaults = listOf(
                SemanticMemoryRecord(
                    id = "mem_seed_1",
                    sessionId = "default_session",
                    memoryType = MemoryType.USER_PREFERENCE,
                    subject = "Engineering Style",
                    content = "User prefers clear, production-grade Jetpack Compose and Kotlin code with concise explanations.",
                    embeddingVector = VectorEmbeddingEngine.generateEmbedding("User prefers clear, production-grade Jetpack Compose and Kotlin code"),
                    importanceScore = 0.95f
                ),
                SemanticMemoryRecord(
                    id = "mem_seed_2",
                    sessionId = "default_session",
                    memoryType = MemoryType.FACT,
                    subject = "Hardware Architecture",
                    content = "System operates on an on-device local runtime with GGUF quantizations (Q4_K_M) and Vulkan/NPU acceleration.",
                    embeddingVector = VectorEmbeddingEngine.generateEmbedding("System operates on an on-device local runtime with GGUF quantizations"),
                    importanceScore = 0.88f
                ),
                SemanticMemoryRecord(
                    id = "mem_seed_3",
                    sessionId = "default_session",
                    memoryType = MemoryType.FACT,
                    subject = "Privacy Guarantee",
                    content = "Conversations and memory embeddings are persisted exclusively in local encrypted Room database with zero cloud egress.",
                    embeddingVector = VectorEmbeddingEngine.generateEmbedding("Conversations and memory embeddings are persisted exclusively in local Room database"),
                    importanceScore = 0.92f
                )
            )
            for (mem in defaults) {
                insertSemanticMemory(mem)
            }
        }
    }

    // --- Conversation Sessions ---

    suspend fun createOrUpdateSession(
        id: String,
        title: String,
        contextSummary: String,
        personaId: String,
        tokensDelta: Int
    ) {
        val existing = database.conversationSessionDao().getSessionById(id)
        val now = System.currentTimeMillis()
        if (existing == null) {
            database.conversationSessionDao().insertSession(
                ConversationSessionEntity(
                    id = id,
                    title = title,
                    contextSummary = contextSummary,
                    activePersonaId = personaId,
                    messageCount = 1,
                    totalTokens = tokensDelta,
                    createdAt = now,
                    lastActiveAt = now
                )
            )
        } else {
            database.conversationSessionDao().updateSessionStats(
                id = id,
                summary = if (contextSummary.isNotBlank()) contextSummary else existing.contextSummary,
                messageCount = existing.messageCount + 1,
                tokens = existing.totalTokens + tokensDelta,
                lastActive = now
            )
        }
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
