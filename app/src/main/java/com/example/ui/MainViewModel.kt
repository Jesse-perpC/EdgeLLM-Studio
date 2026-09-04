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
import com.example.data.model.ModelSpec
import com.example.data.model.PluginSpec
import com.example.data.model.PluginResult
import com.example.data.repository.EdgeLLMRepository
import com.example.engine.CryptoManager
import com.example.engine.HardwareCapabilityDetector
import com.example.engine.LocalInferenceEngine
import com.example.engine.ModelDownloadManager
import com.example.engine.ShareDuration
import com.example.engine.StreamTokenChunk
import com.example.engine.TemporaryShareManager
import com.example.plugin.PluginRegistry
import com.example.service.BackgroundInferenceService
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
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

    // Benchmark Diagnostic State
    private val _benchmarkState = MutableStateFlow(com.example.engine.BenchmarkRunState())
    val benchmarkState: StateFlow<com.example.engine.BenchmarkRunState> = _benchmarkState.asStateFlow()

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

        refreshHardware()
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

    fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        downloadManager.deleteModel(modelId)
    }

    fun setActiveModel(modelId: String) {
        downloadManager.setActiveModel(modelId)
    }

    fun sendPrompt(userText: String) {
        if (userText.isBlank() || _isGenerating.value) return

        val userMessage = InferenceMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = userText,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.insertMessage(userMessage)
            _isGenerating.value = true
            _streamingChunk.value = null

            val activeModel = downloadManager.getActiveModel()
            val currentSettings = _accelerationSettings.value
            val currentParams = _generationParameters.value

            activeInferenceJob = launch {
                try {
                    inferenceEngine.generateStreamingResponse(
                        prompt = userText,
                        model = activeModel,
                        settings = currentSettings,
                        params = currentParams
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
                            _streamingChunk.value = null
                            _isGenerating.value = false
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
}
