package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BackgroundJob
import com.example.data.model.CloudStorageTarget
import com.example.data.model.CompatibilityRating
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.EncryptedExportRecord
import com.example.data.model.GenerationParameters
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.InferenceMessage
import com.example.data.model.JobStatus
import com.example.data.model.JobType
import com.example.data.model.MessageSender
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.data.model.PluginSpec
import com.example.data.model.PluginResult
import com.example.data.model.ComputeBackend
import com.example.data.model.HardwareUsagePoint
import com.example.data.model.MemoryConsumptionBreakdown
import com.example.data.model.TelemetryDashboardState
import com.example.data.repository.EdgeLLMRepository
import com.example.engine.CryptoManager
import com.example.engine.HardwareCapabilityDetector
import com.example.engine.LocalInferenceEngine
import com.example.engine.ModelDownloadManager
import com.example.engine.ShareDuration
import com.example.engine.StreamTokenChunk
import com.example.engine.TemporaryShareManager
import com.example.data.model.AiPersona
import com.example.data.model.AllocationCheckResult
import com.example.data.model.BuiltInPersonas
import com.example.data.model.KnowledgeDocument
import com.example.data.model.SampleKnowledgeDocuments
import com.example.data.model.SubscriptionTier
import com.example.data.model.SupabaseConnectionConfig
import com.example.data.model.UserSubscriptionProfile
import com.example.data.repository.BillingRepository
import com.example.engine.VoiceSpeechManager
import com.example.plugin.PluginRegistry
import com.example.service.BackgroundInferenceService
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EdgeLLMRepository
    private val hardwareDetector: HardwareCapabilityDetector
    private val inferenceEngine: LocalInferenceEngine
    private val downloadManager: ModelDownloadManager
    private val cryptoManager: CryptoManager
    private val shareManager: TemporaryShareManager
    private val pluginRegistry: PluginRegistry
    private val benchmarkEngine = com.example.engine.HardwareBenchmarkEngine()
    private val voiceSpeechManager: VoiceSpeechManager
    private val billingRepository: BillingRepository

    // Subscription & Allocations Management (Supabase / Local-First & Stripe)
    val userSubscriptionProfile: StateFlow<UserSubscriptionProfile> get() = billingRepository.userProfile
    val supabaseConfig: StateFlow<SupabaseConnectionConfig> get() = billingRepository.supabaseConfig
    val stripeConfig: StateFlow<com.example.data.model.StripeBillingConfig> get() = billingRepository.stripeConfig

    fun upgradeSubscriptionTier(tier: SubscriptionTier) {
        billingRepository.upgradeTier(tier)
    }

    fun resetBillingAllocations() {
        billingRepository.resetAllocations()
    }

    fun updateSupabaseCredentials(url: String, anonKey: String) {
        billingRepository.updateSupabaseCredentials(url, anonKey)
    }

    suspend fun testSupabaseConnection(): Result<String> {
        return billingRepository.testAndSyncSupabase()
    }

    fun getSupabaseSqlSchema(): String {
        return billingRepository.getSupabaseSqlSchema()
    }

    fun updateStripeConfig(publishableKey: String, paymentLinkUrl: String) {
        billingRepository.updateStripeConfig(publishableKey, paymentLinkUrl)
    }

    fun getStripeWebhookCode(): String {
        return billingRepository.getStripeSupabaseWebhookCode()
    }

    // Voice Text-to-Speech Engine
    val isSpeaking: StateFlow<Boolean> get() = voiceSpeechManager.isSpeaking
    val currentlySpeakingId: StateFlow<String?> get() = voiceSpeechManager.currentlySpeakingId
    val speechRate: StateFlow<Float> get() = voiceSpeechManager.speechRate
    private val _autoVoiceReadout = MutableStateFlow(false)
    val autoVoiceReadout: StateFlow<Boolean> = _autoVoiceReadout.asStateFlow()

    // Air-Gapped vs Cloud Assist Mode
    private val _isAirGappedMode = MutableStateFlow(false)
    val isAirGappedMode: StateFlow<Boolean> = _isAirGappedMode.asStateFlow()

    fun toggleAirGappedMode() {
        _isAirGappedMode.value = !_isAirGappedMode.value
    }

    fun setAirGappedMode(enabled: Boolean) {
        _isAirGappedMode.value = enabled
    }

    // AI Persona Management
    private val _activePersona = MutableStateFlow<AiPersona>(BuiltInPersonas.GENERAL)
    val activePersona: StateFlow<AiPersona> = _activePersona.asStateFlow()
    private val _customPersonas = MutableStateFlow<List<AiPersona>>(emptyList())
    val customPersonas: StateFlow<List<AiPersona>> = _customPersonas.asStateFlow()
    private val _availablePersonas = MutableStateFlow<List<AiPersona>>(BuiltInPersonas.ALL)
    val availablePersonas: StateFlow<List<AiPersona>> = _availablePersonas.asStateFlow()

    // Local Document Ingestion & Grounding (Local RAG)
    private val _activeKnowledgeDoc = MutableStateFlow<KnowledgeDocument?>(null)
    val activeKnowledgeDoc: StateFlow<KnowledgeDocument?> = _activeKnowledgeDoc.asStateFlow()
    val sampleKnowledgeDocs: List<KnowledgeDocument> = SampleKnowledgeDocuments.ALL_SAMPLES

    // Multimodal Vision Image State
    private val _activeAttachedImageUri = MutableStateFlow<String?>(null)
    val activeAttachedImageUri: StateFlow<String?> = _activeAttachedImageUri.asStateFlow()

    private val _activeAttachedImageLabel = MutableStateFlow<String?>(null)
    val activeAttachedImageLabel: StateFlow<String?> = _activeAttachedImageLabel.asStateFlow()

    // Benchmark Diagnostic State
    private val _benchmarkState = MutableStateFlow(com.example.engine.BenchmarkRunState())
    val benchmarkState: StateFlow<com.example.engine.BenchmarkRunState> = _benchmarkState.asStateFlow()

    // Real-time Hardware Telemetry State
    private val _telemetryState = MutableStateFlow(
        TelemetryDashboardState(
            history = generateInitialTelemetryHistory()
        )
    )
    val telemetryState: StateFlow<TelemetryDashboardState> = _telemetryState.asStateFlow()

    // Active generation coroutine job
    private var activeInferenceJob: Job? = null

    // UI Theme state
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _accentPalette = MutableStateFlow(AccentPalette.CYBER_CYAN)
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    // Hardware Telemetry
    private val _hardwareInfo = MutableStateFlow(
        DeviceHardwareInfo(
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            availableRamBytes = 4L * 1024L * 1024L * 1024L,
            isLowMemory = false,
            cpuCores = 8,
            cpuArchitecture = "arm64-v8a",
            socModel = "Snapdragon / Tensor Neural Engine",
            batteryLevel = 88,
            isCharging = false,
            thermalStatus = "Optimal (Cool)",
            hasVulkanCompute = true,
            hasNpuSupport = true,
            hasOpenCl = true,
            is64Bit = true
        )
    )
    val hardwareInfo: StateFlow<DeviceHardwareInfo> = _hardwareInfo.asStateFlow()

    // Hardware Acceleration Settings
    private val _accelerationSettings = MutableStateFlow(HardwareAccelerationSettings())
    val accelerationSettings: StateFlow<HardwareAccelerationSettings> = _accelerationSettings.asStateFlow()

    // Generation Parameters
    private val _generationParameters = MutableStateFlow(GenerationParameters())
    val generationParameters: StateFlow<GenerationParameters> = _generationParameters.asStateFlow()

    // Live Streaming Token Chunk
    private val _streamingChunk = MutableStateFlow<StreamTokenChunk?>(null)
    val streamingChunk: StateFlow<StreamTokenChunk?> = _streamingChunk.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Plugin Last Result
    private val _lastPluginResult = MutableStateFlow<PluginResult?>(null)
    val lastPluginResult: StateFlow<PluginResult?> = _lastPluginResult.asStateFlow()

    // Decrypted Result View
    private val _decryptedPreview = MutableStateFlow<String?>(null)
    val decryptedPreview: StateFlow<String?> = _decryptedPreview.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = EdgeLLMRepository(database)
        hardwareDetector = HardwareCapabilityDetector(application)
        inferenceEngine = LocalInferenceEngine()
        downloadManager = ModelDownloadManager(application)
        cryptoManager = CryptoManager()
        shareManager = TemporaryShareManager()
        pluginRegistry = PluginRegistry()
        voiceSpeechManager = VoiceSpeechManager(application)
        billingRepository = BillingRepository(application)

        refreshHardware()
        recalculateMemoryBreakdown()
        startTelemetryLoop()
    }

    val models: StateFlow<List<ModelSpec>> = downloadManager.modelsState
    val plugins: StateFlow<List<PluginSpec>> = pluginRegistry.pluginsState

    val chatMessages: StateFlow<List<InferenceMessage>> = repository.chatMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val backgroundJobs: StateFlow<List<BackgroundJob>> = repository.backgroundJobs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val encryptedExports: StateFlow<List<EncryptedExportRecord>> = repository.encryptedExports.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refreshHardware() {
        _hardwareInfo.value = hardwareDetector.detectHardware()
    }

    fun getCompatibility(model: ModelSpec): CompatibilityRating {
        return hardwareDetector.evaluateCompatibility(model, _hardwareInfo.value)
    }

    fun downloadModel(modelId: String) {
        downloadManager.startDownload(modelId)
    }

    fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        downloadManager.deleteModel(modelId)
    }

    val importProgress: StateFlow<com.example.data.model.ModelImportProgress> = downloadManager.importProgressState

    fun importModelsFromFolder(folderTreeUri: android.net.Uri) {
        downloadManager.importModelsFromFolder(folderTreeUri)
    }

    fun importModelFiles(fileUris: List<android.net.Uri>) {
        downloadManager.importModelFiles(fileUris)
    }

    fun importDemoModelFolder() {
        downloadManager.importDemoModelFolder()
    }

    fun cancelImport() {
        downloadManager.cancelImport()
    }

    fun dismissImportProgress() {
        downloadManager.dismissImportProgress()
    }

    fun setActiveModel(modelId: String) {
        downloadManager.setActiveModel(modelId)
        recalculateMemoryBreakdown(modelId)
    }

    private val memorySafetyManager = com.example.engine.MemorySafetyManager(application)

    fun evaluateModelSafety(model: ModelSpec): com.example.engine.MemoryConstraintReport {
        return memorySafetyManager.evaluateModelSafety(model)
    }

    fun addCustomModel(
        name: String,
        downloadUrl: String,
        format: ModelFormat,
        parameterCount: String = "1.0B",
        quantization: String = "Q4_K_M",
        fileSizeMb: Long = 500L,
        category: ModelCategory = ModelCategory.CHAT_REASONING
    ): ModelSpec {
        return downloadManager.addCustomModel(
            name = name,
            downloadUrl = downloadUrl,
            format = format,
            parameterCount = parameterCount,
            quantization = quantization,
            fileSizeMb = fileSizeMb,
            category = category
        )
    }

    fun verifyModelChecksum(modelId: String, onResult: (Boolean, String) -> Unit) {
        downloadManager.verifyModelChecksum(modelId, onResult)
    }

    fun selectPersona(persona: AiPersona) {
        _activePersona.value = persona
        _generationParameters.value = _generationParameters.value.copy(
            systemPrompt = persona.systemPrompt,
            temperature = persona.defaultTemperature,
            topP = persona.defaultTopP
        )
    }

    fun attachKnowledgeDoc(doc: KnowledgeDocument) {
        _activeKnowledgeDoc.value = doc
    }

    fun detachKnowledgeDoc() {
        _activeKnowledgeDoc.value = null
    }

    fun attachImage(uri: String, label: String) {
        _activeAttachedImageUri.value = uri
        _activeAttachedImageLabel.value = label
    }

    fun detachImage() {
        _activeAttachedImageUri.value = null
        _activeAttachedImageLabel.value = null
    }

    fun adjustGenerationParameters(
        temperature: Float? = null,
        topP: Float? = null,
        topK: Int? = null,
        maxTokens: Int? = null,
        systemPrompt: String? = null
    ) {
        val current = _generationParameters.value
        _generationParameters.value = current.copy(
            temperature = temperature ?: current.temperature,
            topP = topP ?: current.topP,
            topK = topK ?: current.topK,
            maxNewTokens = maxTokens ?: current.maxNewTokens,
            systemPrompt = systemPrompt ?: current.systemPrompt
        )
    }

    fun ingestCustomKnowledge(title: String, content: String) {
        val words = content.split(" ", "\n").filter { it.isNotBlank() }
        val doc = KnowledgeDocument(
            id = "custom_doc_${System.currentTimeMillis()}",
            title = if (title.isBlank()) "Ingested_Notes.txt" else title.trim(),
            summary = "User-ingested private local context (${words.size} words).",
            content = content.trim(),
            sizeBytes = content.toByteArray().size.toLong(),
            tokenCountEstimate = (words.size * 1.3f).toInt(),
            isPreloaded = false
        )
        _activeKnowledgeDoc.value = doc
    }

    fun speakMessage(text: String, id: String) {
        voiceSpeechManager.speak(text, id)
    }

    fun stopSpeaking() {
        voiceSpeechManager.stop()
    }

    fun setSpeechRate(rate: Float) {
        voiceSpeechManager.setSpeechRate(rate)
    }

    fun toggleAutoVoiceReadout() {
        _autoVoiceReadout.value = !_autoVoiceReadout.value
    }

    fun deleteChatMessage(id: String) {
        viewModelScope.launch {
            repository.deleteChatMessage(id)
        }
    }

    fun regenerateResponse(lastAssistantMessage: InferenceMessage, promptText: String) {
        val check = billingRepository.canExecuteInference(estimatedTokens = 15)
        if (check is AllocationCheckResult.QuotaExceeded) {
            val quotaMsg = InferenceMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                text = "⚠️ **Monthly Allocation Limit Reached (${check.used} / ${check.max} tokens)**\n\nYour Free Starter allocation pool is exhausted for this billing period. Upgrade to the **Pro Creator Plan** to unlock 200,000 monthly tokens, Gemini Cloud Assist, RAG document grounding, and full hardware acceleration.",
                timestamp = System.currentTimeMillis(),
                executionBackend = "Billing Guard • Supabase Quota Enforced"
            )
            viewModelScope.launch { repository.insertMessage(quotaMsg) }
            return
        }

        viewModelScope.launch {
            repository.deleteChatMessage(lastAssistantMessage.id)
            _isGenerating.value = true
            _streamingChunk.value = null

            val activeModel = downloadManager.getActiveModel()
            val currentSettings = _accelerationSettings.value
            val currentParams = _generationParameters.value
            val persona = _activePersona.value
            val attachedDoc = _activeKnowledgeDoc.value

            activeInferenceJob = launch {
                try {
                    inferenceEngine.generateStreamingResponse(
                        prompt = promptText,
                        model = activeModel,
                        settings = currentSettings,
                        params = currentParams,
                        persona = persona,
                        attachedDoc = attachedDoc,
                        isAirGapped = _isAirGappedMode.value
                    ).collect { chunk ->
                        _streamingChunk.value = chunk
                        if (chunk.isComplete) {
                            val assistantMessage = InferenceMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.ASSISTANT,
                                text = chunk.accumulatedText,
                                timestamp = System.currentTimeMillis(),
                                tokensGenerated = chunk.tokenCount,
                                tokensPerSecond = chunk.tokensPerSecond,
                                timeToFirstTokenMs = chunk.timeToFirstTokenMs,
                                executionBackend = chunk.backendUsed,
                                modelId = activeModel.name
                            )
                            repository.insertMessage(assistantMessage)
                            billingRepository.recordAllocationUsage(chunk.tokenCount)
                            _streamingChunk.value = null
                            _isGenerating.value = false

                            if (_autoVoiceReadout.value) {
                                voiceSpeechManager.speak(chunk.accumulatedText, assistantMessage.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _isGenerating.value = false
                    _streamingChunk.value = null
                }
            }
        }
    }

    fun sendPrompt(
        userText: String,
        imageUri: String? = null,
        imageLabel: String? = null
    ) {
        if (userText.isBlank() || _isGenerating.value) return

        val check = billingRepository.canExecuteInference(estimatedTokens = 15)
        if (check is AllocationCheckResult.QuotaExceeded) {
            val quotaMsg = InferenceMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ASSISTANT,
                text = "⚠️ **Monthly Allocation Limit Reached (${check.used} / ${check.max} tokens)**\n\nYour Free Starter allocation pool is exhausted for this billing period. Upgrade to the **Pro Creator Plan** to unlock 200,000 monthly tokens, Gemini Cloud Assist, RAG document grounding, and full hardware acceleration.\n\nTap the **Plan & Billing** badge in the top bar to manage your plan or sync with Supabase.",
                timestamp = System.currentTimeMillis(),
                executionBackend = "Billing Guard • Supabase Quota Enforced"
            )
            viewModelScope.launch { repository.insertMessage(quotaMsg) }
            return
        }

        val finalImageUri = imageUri ?: _activeAttachedImageUri.value
        val finalImageLabel = imageLabel ?: _activeAttachedImageLabel.value

        val userMessage = InferenceMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = userText,
            timestamp = System.currentTimeMillis(),
            imageUri = finalImageUri,
            imageLabel = finalImageLabel
        )

        // Clear active attached image once sent
        detachImage()

        viewModelScope.launch {
            repository.insertMessage(userMessage)
            _isGenerating.value = true
            _streamingChunk.value = null

            val activeModel = downloadManager.getActiveModel()
            val currentSettings = _accelerationSettings.value
            val currentParams = _generationParameters.value
            val persona = _activePersona.value
            val attachedDoc = _activeKnowledgeDoc.value

            activeInferenceJob = launch {
                try {
                    inferenceEngine.generateStreamingResponse(
                        prompt = userText,
                        model = activeModel,
                        settings = currentSettings,
                        params = currentParams,
                        persona = persona,
                        attachedDoc = attachedDoc,
                        attachedImageUri = finalImageUri,
                        attachedImageLabel = finalImageLabel,
                        isAirGapped = _isAirGappedMode.value
                    ).collect { chunk ->
                        _streamingChunk.value = chunk
                        if (chunk.isComplete) {
                            val assistantMessage = InferenceMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.ASSISTANT,
                                text = chunk.accumulatedText,
                                timestamp = System.currentTimeMillis(),
                                tokensGenerated = chunk.tokenCount,
                                tokensPerSecond = chunk.tokensPerSecond,
                                timeToFirstTokenMs = chunk.timeToFirstTokenMs,
                                executionBackend = chunk.backendUsed,
                                modelId = activeModel.name
                            )
                            repository.insertMessage(assistantMessage)
                            billingRepository.recordAllocationUsage(chunk.tokenCount)
                            _streamingChunk.value = null
                            _isGenerating.value = false

                            if (_autoVoiceReadout.value) {
                                voiceSpeechManager.speak(chunk.accumulatedText, assistantMessage.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _isGenerating.value = false
                    _streamingChunk.value = null
                }
            }
        }
    }

    fun stopGeneration() {
        activeInferenceJob?.cancel()
        activeInferenceJob = null
        val current = _streamingChunk.value
        if (current != null && current.accumulatedText.isNotBlank()) {
            viewModelScope.launch {
                val partialMessage = InferenceMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ASSISTANT,
                    text = "${current.accumulatedText} [Generation Stopped]",
                    timestamp = System.currentTimeMillis(),
                    tokensGenerated = current.tokenCount,
                    tokensPerSecond = current.tokensPerSecond,
                    timeToFirstTokenMs = current.timeToFirstTokenMs,
                    executionBackend = current.backendUsed,
                    modelId = downloadManager.getActiveModel().name
                )
                repository.insertMessage(partialMessage)
            }
        }
        _streamingChunk.value = null
        _isGenerating.value = false
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    fun updateAccelerationSettings(settings: HardwareAccelerationSettings) {
        _accelerationSettings.value = settings
    }

    fun updateGenerationParameters(params: GenerationParameters) {
        _generationParameters.value = params
    }

    fun enqueueBackgroundJob(title: String, jobType: JobType, inputData: String) {
        viewModelScope.launch {
            val job = BackgroundJob(
                id = UUID.randomUUID().toString(),
                title = title,
                jobType = jobType,
                inputData = inputData,
                status = JobStatus.QUEUED,
                createdAt = System.currentTimeMillis()
            )
            repository.insertJob(job)
            startBackgroundService()
        }
    }

    fun deleteBackgroundJob(id: String) {
        viewModelScope.launch {
            repository.deleteJob(id)
        }
    }

    fun startBackgroundService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BackgroundInferenceService::class.java).apply {
            action = BackgroundInferenceService.ACTION_START_QUEUE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopBackgroundService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BackgroundInferenceService::class.java).apply {
            action = BackgroundInferenceService.ACTION_STOP_QUEUE
        }
        context.startService(intent)
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        pluginRegistry.togglePlugin(pluginId, enabled)
    }

    fun runPlugin(pluginId: String, input: String) {
        viewModelScope.launch {
            val model = downloadManager.getActiveModel()
            val result = pluginRegistry.executePlugin(pluginId, input, model, _accelerationSettings.value)
            _lastPluginResult.value = result
        }
    }

    fun addCustomPlugin(name: String, category: String, description: String, filterKeyword: String) {
        pluginRegistry.addCustomPlugin(name, category, description, filterKeyword)
    }

    fun exportEncryptedData(
        title: String,
        sourceType: String,
        plaintext: String,
        passphrase: String,
        cloudTarget: CloudStorageTarget,
        duration: ShareDuration
    ) {
        viewModelScope.launch {
            val encryptionResult = cryptoManager.encryptText(plaintext, passphrase)
            val shareBundle = shareManager.createTemporaryShare(duration)

            val exportRecord = EncryptedExportRecord(
                id = UUID.randomUUID().toString(),
                title = title,
                sourceType = sourceType,
                plainSizeBytes = encryptionResult.plainSizeBytes,
                cipherSizeBytes = encryptionResult.cipherSizeBytes,
                algorithm = "AES-256-GCM / PBKDF2 (256-bit)",
                sha256Hash = encryptionResult.sha256Hash,
                ivBase64 = encryptionResult.ivBase64,
                ciphertextPreview = encryptionResult.ciphertextBase64.take(80) + "...",
                cloudTarget = cloudTarget,
                temporaryShareUrl = shareBundle.shareUrl,
                temporaryShareToken = shareBundle.token,
                temporaryShareExpiresAt = shareBundle.expiresAt,
                isUploaded = true,
                createdAt = System.currentTimeMillis()
            )

            repository.insertExport(exportRecord)
        }
    }

    fun decryptExport(record: EncryptedExportRecord, passphrase: String, fullCiphertext: String): Result<String> {
        val saltPlaceholder = ""
        val result = cryptoManager.decryptText(
            ciphertextBase64 = fullCiphertext,
            ivBase64 = record.ivBase64,
            saltBase64 = saltPlaceholder,
            passphrase = passphrase
        )
        if (result.isSuccess) {
            _decryptedPreview.value = result.getOrNull()
        }
        return result
    }

    fun deleteExport(id: String) {
        viewModelScope.launch {
            repository.deleteExport(id)
        }
    }

    fun generateStrongPassphrase(): String {
        return cryptoManager.generateStrongPassphrase()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun setAccentPalette(palette: AccentPalette) {
        _accentPalette.value = palette
    }

    fun clearDecryptedPreview() {
        _decryptedPreview.value = null
    }

    fun runHardwareBenchmark() {
        viewModelScope.launch {
            benchmarkEngine.runDiagnosticBenchmark(
                hardware = _hardwareInfo.value,
                settings = _accelerationSettings.value
            ).collect { state ->
                _benchmarkState.value = state
            }
        }
    }

    fun resetBenchmark() {
        _benchmarkState.value = com.example.engine.BenchmarkRunState()
    }

    private fun generateInitialTelemetryHistory(): List<HardwareUsagePoint> {
        val now = System.currentTimeMillis()
        val points = mutableListOf<HardwareUsagePoint>()
        for (i in 20 downTo 1) {
            val t = now - (i * 1000L)
            points.add(
                HardwareUsagePoint(
                    timestamp = t,
                    gpuPercent = 18f + kotlin.math.sin(i * 0.4).toFloat() * 6f,
                    npuPercent = 10f + kotlin.math.cos(i * 0.3).toFloat() * 4f,
                    cpuPercent = 22f + kotlin.math.sin(i * 0.5).toFloat() * 5f,
                    memoryUsedGb = 3.1f,
                    tokensPerSec = 0f
                )
            )
        }
        return points
    }

    fun setLiveTelemetryPolling(enabled: Boolean) {
        _telemetryState.value = _telemetryState.value.copy(isLivePolling = enabled)
    }

    fun setTelemetryComputeBackend(backend: ComputeBackend) {
        _accelerationSettings.value = _accelerationSettings.value.copy(computeBackend = backend)
    }

    fun setContextTokens(tokens: Int) {
        val current = _telemetryState.value
        _telemetryState.value = current.copy(activeContextTokens = tokens.coerceIn(128, current.maxContextTokens))
        recalculateMemoryBreakdown()
    }

    fun recalculateMemoryBreakdown(targetModelId: String? = null) {
        val activeModel = if (targetModelId != null) {
            downloadManager.modelsState.value.firstOrNull { it.id == targetModelId }
        } else {
            downloadManager.modelsState.value.firstOrNull { it.isActive }
        } ?: downloadManager.modelsState.value.firstOrNull()

        val totalRam = _hardwareInfo.value.totalRamBytes
        val modelBytes = activeModel?.requiredRamBytes ?: (750L * 1024L * 1024L)
        val contextTokens = _telemetryState.value.activeContextTokens
        // ~256KB per token in FP16 KV cache for standard 32 layers
        val kvBytes = (contextTokens * 256L * 1024L).coerceIn(128L * 1024L * 1024L, 1024L * 1024L * 1024L)
        val osBytes = (2100L * 1024L * 1024L).coerceAtMost(totalRam / 3)
        val headroom = (totalRam - modelBytes - kvBytes - osBytes).coerceAtLeast(300L * 1024L * 1024L)

        val breakdown = MemoryConsumptionBreakdown(
            totalRamBytes = totalRam,
            modelWeightsBytes = modelBytes,
            kvCacheBytes = kvBytes,
            systemOsBytes = osBytes,
            availableHeadroomBytes = headroom
        )

        _telemetryState.value = _telemetryState.value.copy(
            memoryBreakdown = breakdown,
            maxContextTokens = activeModel?.contextLength ?: 2048
        )
    }

    private fun startTelemetryLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            delay(2000L)
            while (isActive) {
                delay(_telemetryState.value.pollingIntervalMs.coerceAtLeast(2000L))
                if (!_telemetryState.value.isLivePolling) continue

                val isGen = _isGenerating.value
                val backend = _accelerationSettings.value.computeBackend
                val randomJitter = (kotlin.random.Random.nextFloat() * 6f) - 3f

                val targetGpu: Float
                val targetNpu: Float
                val targetCpu: Float
                val targetTokPerSec: Float
                val watts: Float
                val temp: Float

                if (isGen) {
                    when (backend) {
                        ComputeBackend.GPU_VULKAN -> {
                            targetGpu = (82f + randomJitter).coerceIn(70f, 98f)
                            targetNpu = (12f + randomJitter * 0.5f).coerceIn(5f, 25f)
                            targetCpu = (38f + randomJitter).coerceIn(25f, 55f)
                        }
                        ComputeBackend.NPU_NNAPI -> {
                            targetGpu = (15f + randomJitter * 0.5f).coerceIn(5f, 25f)
                            targetNpu = (88f + randomJitter).coerceIn(75f, 99f)
                            targetCpu = (28f + randomJitter).coerceIn(18f, 45f)
                        }
                        ComputeBackend.OPENCL -> {
                            targetGpu = (76f + randomJitter).coerceIn(65f, 92f)
                            targetNpu = (10f + randomJitter * 0.5f).coerceIn(4f, 20f)
                            targetCpu = (34f + randomJitter).coerceIn(22f, 50f)
                        }
                        ComputeBackend.CPU_NEON -> {
                            targetGpu = (12f + randomJitter * 0.5f).coerceIn(5f, 20f)
                            targetNpu = (6f + randomJitter * 0.3f).coerceIn(2f, 15f)
                            targetCpu = (84f + randomJitter).coerceIn(70f, 96f)
                        }
                    }
                    targetTokPerSec = _streamingChunk.value?.tokensPerSecond ?: (24.5f + randomJitter)
                    watts = (4.4f + (randomJitter * 0.1f)).coerceIn(3.6f, 6.2f)
                    temp = (37.8f + (randomJitter * 0.05f)).coerceIn(36.0f, 42.0f)
                } else {
                    targetGpu = (14f + randomJitter * 0.8f).coerceIn(5f, 24f)
                    targetNpu = (7f + randomJitter * 0.5f).coerceIn(2f, 16f)
                    targetCpu = (20f + randomJitter).coerceIn(10f, 32f)
                    targetTokPerSec = 0f
                    watts = (2.2f + (randomJitter * 0.05f)).coerceIn(1.6f, 2.9f)
                    temp = (35.4f + (randomJitter * 0.04f)).coerceIn(34.0f, 37.0f)
                }

                val currentHist = _telemetryState.value.history
                val newPoint = HardwareUsagePoint(
                    timestamp = System.currentTimeMillis(),
                    gpuPercent = targetGpu,
                    npuPercent = targetNpu,
                    cpuPercent = targetCpu,
                    memoryUsedGb = _telemetryState.value.memoryBreakdown.usedRamGb,
                    tokensPerSec = targetTokPerSec
                )
                val updatedHist = (currentHist + newPoint).takeLast(25)

                _telemetryState.value = _telemetryState.value.copy(
                    currentGpuPercent = targetGpu,
                    currentNpuPercent = targetNpu,
                    currentCpuPercent = targetCpu,
                    currentTokensPerSec = targetTokPerSec,
                    powerDrawWatts = watts,
                    deviceTemperatureCelsius = temp,
                    history = updatedHist
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (!com.example.service.InferenceServerService.isServiceActive) {
            apiServer.stop()
        }
        voiceSpeechManager.shutdown()
    }

    // ==========================================
    // Ollama & OpenAI Compatible Inference Server
    // ==========================================
    private val _apiServerConfig = MutableStateFlow(com.example.api.ApiServerConfig())
    val apiServerConfig: StateFlow<com.example.api.ApiServerConfig> = _apiServerConfig.asStateFlow()

    private val apiServer = com.example.api.InferenceServerManager.getInstance(
        context = application,
        inferenceEngine = inferenceEngine,
        modelProvider = { models.value },
        activeModelProvider = { models.value.firstOrNull { it.isActive } ?: models.value.firstOrNull { it.isDownloaded } },
        accelerationSettingsProvider = { _accelerationSettings.value }
    )

    val apiServerStats: StateFlow<com.example.api.ApiServerStats> = apiServer.serverStats
    val apiServerLogs: StateFlow<List<com.example.api.ApiRequestLog>> = apiServer.requestLogs

    fun startApiServer(port: Int? = null, bindToLan: Boolean? = null) {
        val p = port ?: _apiServerConfig.value.port
        val lan = bindToLan ?: _apiServerConfig.value.bindToLan
        val newCfg = _apiServerConfig.value.copy(port = p, bindToLan = lan)
        _apiServerConfig.value = newCfg
        apiServer.updateConfig(newCfg)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val started = apiServer.start(p, lan)
                if (started) {
                    com.example.service.InferenceServerService.startService(getApplication(), p, lan)
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Failed starting API server: ${e.message}", e)
            }
        }
    }

    fun stopApiServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiServer.stop()
                com.example.service.InferenceServerService.stopService(getApplication())
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Failed stopping API server: ${e.message}", e)
            }
        }
    }

    fun toggleApiServer() {
        if (apiServerStats.value.isRunning) {
            stopApiServer()
        } else {
            startApiServer()
        }
    }

    fun updateApiConfig(newConfig: com.example.api.ApiServerConfig) {
        _apiServerConfig.value = newConfig
        apiServer.updateConfig(newConfig)
    }

    fun testApiServerEndpoint(onResult: (Boolean, String, Long) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val stats = apiServerStats.value
                val port = stats.port
                val url = java.net.URL("http://127.0.0.1:$port/api/version")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                val responseCode = conn.responseCode
                val duration = System.currentTimeMillis() - startTime
                if (responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    onResult(true, "HTTP 200 OK: $resp", duration)
                } else {
                    onResult(false, "HTTP $responseCode", duration)
                }
                conn.disconnect()
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                onResult(false, "Connection error: ${e.message}", duration)
            }
        }
    }

    fun addCustomPersona(persona: AiPersona) {
        val updated = _customPersonas.value + persona
        _customPersonas.value = updated
        _availablePersonas.value = BuiltInPersonas.ALL + updated
        selectPersona(persona)
    }

    fun deleteCustomPersona(personaId: String) {
        val updated = _customPersonas.value.filter { it.id != personaId }
        _customPersonas.value = updated
        _availablePersonas.value = BuiltInPersonas.ALL + updated
        if (_activePersona.value.id == personaId) {
            selectPersona(BuiltInPersonas.GENERAL)
        }
    }

    fun executeApiSandboxRequest(
        endpoint: String,
        method: String,
        body: String?,
        onResult: (statusCode: Int, responseBody: String, latencyMs: Long) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val stats = apiServerStats.value
                val port = stats.port
                val path = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
                val url = java.net.URL("http://127.0.0.1:$port$path")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 8000
                conn.requestMethod = method
                if (!body.isNullOrBlank() && (method == "POST" || method == "PUT")) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { os ->
                        os.write(body.toByteArray(Charsets.UTF_8))
                    }
                }
                val code = conn.responseCode
                val latency = System.currentTimeMillis() - startTime
                val respText = try {
                    if (code in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    }
                } catch (e: Exception) {
                    "Error reading body: ${e.message}"
                }
                conn.disconnect()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(code, respText, latency)
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(0, "Error: ${e.message ?: "Connection refused. Make sure API Server is started."}", latency)
                }
            }
        }
    }
}
