package com.perpcorp.edgellm.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.perpcorp.edgellm.data.local.AppDatabase
import com.perpcorp.edgellm.data.model.ModelCategory
import com.perpcorp.edgellm.data.model.ModelFormat
import com.perpcorp.edgellm.data.model.ModelImportProgress
import com.perpcorp.edgellm.data.model.ModelSpec
import com.perpcorp.edgellm.data.repository.EdgeLLMRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.buffer
import okio.sink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

class ModelDownloadManager(
    private val context: Context,
    val repository: EdgeLLMRepository = EdgeLLMRepository(AppDatabase.getInstance(context))
) {

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val importedRegistryFile = File(context.filesDir, "imported_models.json")
    private val memorySafetyManager = MemorySafetyManager(context)
    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private var activeImportJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val initialModels = listOf(
        ModelSpec(
            id = "tinyllama-1.1b-gguf",
            name = "TinyLlama 1.1B Chat",
            parameterCount = "1.1 Billion",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 669L * 1024L * 1024L,
            requiredRamBytes = 950L * 1024L * 1024L,
            contextLength = 2048,
            description = "Ultra-compact high-speed conversational LLM. Extremely fast on all mobile CPUs with minimal battery draw.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = true
        ),
        ModelSpec(
            id = "smollm-360m-gguf",
            name = "SmolLM 360M Instruct",
            parameterCount = "360 Million",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 230L * 1024L * 1024L,
            requiredRamBytes = 410L * 1024L * 1024L,
            contextLength = 4096,
            description = "Featherweight model trained on high-quality synthetic datasets.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM-360M-Instruct-GGUF/resolve/main/smollm-360m-instruct-q4_k_m.gguf",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "deepseek-r1-distill-1.5b-gguf",
            name = "DeepSeek-R1 Distill 1.5B",
            parameterCount = "1.5 Billion",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 1120L * 1024L * 1024L,
            requiredRamBytes = 1680L * 1024L * 1024L,
            contextLength = 8192,
            description = "State-of-the-art open reasoning model distilled from DeepSeek-R1. Features autonomous Chain-of-Thought <think> reflection natively on mobile devices.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "llama-3.2-1b-gguf",
            name = "Llama 3.2 1B Instruct",
            parameterCount = "1.23 Billion",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 820L * 1024L * 1024L,
            requiredRamBytes = 1190L * 1024L * 1024L,
            contextLength = 8192,
            description = "Meta's state-of-the-art ultra-compact instruction tuned LLM. Optimized for on-device assistant tasks, tool calling, and long-context understanding.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "gemma-3-1b-it-gguf",
            name = "Gemma 3 1B Instruct",
            parameterCount = "1.1 Billion",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 780L * 1024L * 1024L,
            requiredRamBytes = 1120L * 1024L * 1024L,
            contextLength = 8192,
            description = "Google's 2026 flagship open lightweight model. Engineered specifically for mobile hardware, Android AppFunctions tool-calling, and multimodal grounding.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "bitnet-b1.58-1b-gguf",
            name = "BitNet b1.58 1B (1-Bit LLM)",
            parameterCount = "1.0 Billion",
            format = ModelFormat.GGUF,
            quantization = "1.58-bit Ternary",
            fileSizeBytes = 410L * 1024L * 1024L,
            requiredRamBytes = 580L * 1024L * 1024L,
            contextLength = 4096,
            description = "Breakthrough 1-bit LLM architecture (-1, 0, 1 weights). Replaces matrix multiplication with integer addition, cutting mobile power consumption by 85%.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/microsoft/BitNet-b1.58-1B-GGUF/resolve/main/bitnet-b1.58-1b-tl1.gguf",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "mobilebert-tflite",
            name = "MobileBERT Classification",
            parameterCount = "25 Million",
            format = ModelFormat.TFLITE,
            quantization = "INT8 Quantized",
            fileSizeBytes = 55L * 1024L * 1024L,
            requiredRamBytes = 120L * 1024L * 1024L,
            contextLength = 512,
            description = "TensorFlow Lite quantized BERT for lightning-fast on-device document classification, PII identification, and intent matching.",
            category = ModelCategory.EMBEDDINGS_CLASSIFICATION,
            downloadUrl = "https://storage.googleapis.com/tfhub-modules/tensorflow/mobilebert/1.tflite",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "gemma-2b-it-tflite",
            name = "Gemma 2B IT (LiteRT)",
            parameterCount = "2.0 Billion",
            format = ModelFormat.TFLITE,
            quantization = "Dynamic INT8",
            fileSizeBytes = 1450L * 1024L * 1024L,
            requiredRamBytes = 2200L * 1024L * 1024L,
            contextLength = 4096,
            description = "Google Gemma architecture ported to TensorFlow Lite / LiteRT runtime with Qualcomm HTP and GPU delegates.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://storage.googleapis.com/kaggle-models/google/gemma/tflite/gemma-2b-it-cpu-int8.bin",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "phi3-mini-gguf",
            name = "Phi-3 Mini 3.8B",
            parameterCount = "3.8 Billion",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 2390L * 1024L * 1024L,
            requiredRamBytes = 3400L * 1024L * 1024L,
            contextLength = 4096,
            description = "Deep reasoning and code synthesis model. Highly capable; best suited for devices with 8GB+ RAM and GPU acceleration.",
            category = ModelCategory.CODE_ANALYSIS,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "qwen25-05b-gguf",
            name = "Qwen2.5 0.5B Instruct",
            parameterCount = "500 Million",
            format = ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 395L * 1024L * 1024L,
            requiredRamBytes = 620L * 1024L * 1024L,
            contextLength = 8192,
            description = "Multilingual lightweight model with extended context window. Excellent reasoning per parameter size.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "minilm-onnx",
            name = "All-MiniLM-L6-v2 ONNX",
            parameterCount = "22 Million",
            format = ModelFormat.ONNX,
            quantization = "FP16",
            fileSizeBytes = 45L * 1024L * 1024L,
            requiredRamBytes = 90L * 1024L * 1024L,
            contextLength = 512,
            description = "High-speed sentence embedding and semantic similarity engine for offline clustering and vector retrieval.",
            category = ModelCategory.EMBEDDINGS_CLASSIFICATION,
            downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "gemini-nano-aicore",
            name = "Gemini Nano (Android AICore)",
            parameterCount = "3.25 Billion",
            format = ModelFormat.ANDROID_AICORE,
            quantization = "System TPU INT4",
            fileSizeBytes = 0L,
            requiredRamBytes = 0L,
            contextLength = 4096,
            description = "Google's system foundation model built directly into Android OS via Android AICore. Zero APK overhead, hardware-isolated TPU/NPU acceleration, and 100% private.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://developer.android.com/blog/posts/build-intelligent-android-apps-on-device-inference",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "gemma-2-2b-mediapipe",
            name = "Gemma 2 2B (MediaPipe GenAI)",
            parameterCount = "2.6 Billion",
            format = ModelFormat.MEDIAPIPE_TASK,
            quantization = "Dynamic GPU INT8",
            fileSizeBytes = 1490L * 1024L * 1024L,
            requiredRamBytes = 2150L * 1024L * 1024L,
            contextLength = 8192,
            description = "Google MediaPipe LLM Inference API bundle for Gemma 2. Features GPU OpenCL/Vulkan delegate acceleration, dynamic LoRA rank merging, and streaming token response.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma2-2b-it-gpu-int8.bin",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "qwen25-15b-mnn",
            name = "Qwen 2.5 1.5B (Alibaba MNN)",
            parameterCount = "1.5 Billion",
            format = ModelFormat.MNN_LLM,
            quantization = "MNN W4A16 + INT8 KV",
            fileSizeBytes = 960L * 1024L * 1024L,
            requiredRamBytes = 1380L * 1024L * 1024L,
            contextLength = 8192,
            description = "Alibaba's official Mobile Neural Network runtime for Qwen 2.5. Features high-speed prefill operator fusion, low memory footprint, and disk-backed prompt caching.",
            category = ModelCategory.CHAT_REASONING,
            downloadUrl = "https://huggingface.co/Alibaba-MNN/Qwen2.5-1.5B-Instruct-MNN/resolve/main/model.mnn",
            sha256Checksum = "",
            isDownloaded = true,
            isActive = false
        ),
        ModelSpec(
            id = "qwen2-vl-mnn",
            name = "Qwen2-VL 2B Multimodal (Alibaba MNN)",
            parameterCount = "2.2 Billion",
            format = ModelFormat.MNN_LLM,
            quantization = "MNN W4A16 + ViT",
            fileSizeBytes = 1450L * 1024L * 1024L,
            requiredRamBytes = 2050L * 1024L * 1024L,
            contextLength = 4096,
            description = "Alibaba MNN vision-language model. Supports camera vision input, document scanning, and OCR visual grounding entirely on mobile device.",
            category = ModelCategory.VISION_MULTIMODAL,
            downloadUrl = "https://huggingface.co/Alibaba-MNN/Qwen2-VL-2B-Instruct-MNN/resolve/main/model.mnn",
            sha256Checksum = "",
            supportsVision = true,
            isDownloaded = false,
            isActive = false
        ),
        ModelSpec(
            id = "phi2-mediapipe",
            name = "Phi-2 2.7B (MediaPipe GenAI)",
            parameterCount = "2.7 Billion",
            format = ModelFormat.MEDIAPIPE_TASK,
            quantization = "GPU INT4",
            fileSizeBytes = 1680L * 1024L * 1024L,
            requiredRamBytes = 2400L * 1024L * 1024L,
            contextLength = 2048,
            description = "Microsoft Phi-2 packaged for Google MediaPipe LLM Inference API on Android with GPU delegate and top-K sampling.",
            category = ModelCategory.CODE_ANALYSIS,
            downloadUrl = "https://storage.googleapis.com/mediapipe-models/llm_inference/phi2-gpu-int4.bin",
            sha256Checksum = "",
            isDownloaded = false,
            isActive = false
        )
    )

    private val _modelsState = MutableStateFlow<List<ModelSpec>>(loadInitialWithPersistedModels())
    val modelsState: StateFlow<List<ModelSpec>> = _modelsState.asStateFlow()

    private val _importProgressState = MutableStateFlow(ModelImportProgress())
    val importProgressState: StateFlow<ModelImportProgress> = _importProgressState.asStateFlow()

    init {
        // Seed Room, then reconcile the catalog with what is actually on disk.
        scope.launch {
            try {
                if (repository.getModelCount() == 0) {
                    repository.insertAllModels(
                        initialModels.map { m ->
                            val resolved = resolveExistingModelFile(m)
                            m.copy(
                                localFilePath = resolved?.absolutePath ?: flatModelFile(m.id, m.format).absolutePath,
                                isDownloaded = resolved != null,
                                isActive = resolved != null && m.isActive,
                                downloadedBytes = resolved?.length() ?: 0L
                            )
                        }
                    )
                }
                hydrateFromDatabase()
            } catch (e: Exception) {
                Log.w(TAG, "Could not reconcile model catalog with disk: ${e.localizedMessage}")
            }
        }
    }

    // --- Import Flow: Select Folder & Auto Make Available to Load ---

    data class DiscoveredModelFile(
        val uri: Uri?,
        val file: File?,
        val displayName: String,
        val sizeBytes: Long
    )

    fun importModelsFromFolder(folderTreeUri: Uri) {
        activeImportJob?.cancel()
        activeImportJob = scope.launch {
            try {
                val folderName = try {
                    folderTreeUri.lastPathSegment?.split(":")?.lastOrNull() ?: "Selected Folder"
                } catch (_: Exception) {
                    "Selected Folder"
                }

                _importProgressState.value = ModelImportProgress(
                    isImporting = true,
                    folderName = folderName,
                    stageStatusText = "Scanning directory for neural model files (.gguf, .tflite, .onnx)...",
                    progressPercent = 5
                )

                delay(300)

                val discoveredFiles = mutableListOf<DiscoveredModelFile>()
                val sidecarFiles = mutableListOf<DiscoveredModelFile>()

                try {
                    val docId = DocumentsContract.getTreeDocumentId(folderTreeUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderTreeUri, docId)
                    val cursor = context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE
                        ),
                        null, null, null
                    )

                    cursor?.use { c ->
                        val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                        while (c.moveToNext()) {
                            val childDocId = if (idIdx != -1) c.getString(idIdx) else ""
                            val name = if (nameIdx != -1) c.getString(nameIdx) ?: "model" else "model"
                            val size = if (sizeIdx != -1) c.getLong(sizeIdx).coerceAtLeast(1024L) else 1024L
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(folderTreeUri, childDocId)

                            if (isModelFile(name)) {
                                discoveredFiles.add(DiscoveredModelFile(childUri, null, name, size))
                            } else if (isSidecarFile(name)) {
                                sidecarFiles.add(DiscoveredModelFile(childUri, null, name, size))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to direct path or content query
                }

                // If no files were found via SAF (e.g. empty folder or mocked folder), let's inform or handle
                processDiscoveredModelList(discoveredFiles, folderName, sidecarFiles)

            } catch (e: Exception) {
                _importProgressState.value = ModelImportProgress(
                    isImporting = false,
                    isCompleted = false,
                    errorMessage = "Error scanning folder: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun importModelFiles(fileUris: List<Uri>) {
        activeImportJob?.cancel()
        activeImportJob = scope.launch {
            try {
                _importProgressState.value = ModelImportProgress(
                    isImporting = true,
                    folderName = "Selected Files",
                    stageStatusText = "Inspecting ${fileUris.size} selected file(s)...",
                    progressPercent = 10
                )
                delay(200)

                val discoveredFiles = mutableListOf<DiscoveredModelFile>()
                val sidecarFiles = mutableListOf<DiscoveredModelFile>()
                for (uri in fileUris) {
                    var name = "imported_model"
                    var size = 500L * 1024L * 1024L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex).coerceAtLeast(1024L)
                        }
                    }

                    val item = DiscoveredModelFile(uri, null, name, size)
                    if (isModelFile(name)) {
                        discoveredFiles.add(item)
                    } else if (isSidecarFile(name)) {
                        sidecarFiles.add(item)
                    }
                }

                processDiscoveredModelList(discoveredFiles, "Imported Files", sidecarFiles)
            } catch (e: Exception) {
                _importProgressState.value = ModelImportProgress(
                    isImporting = false,
                    isCompleted = false,
                    errorMessage = "Failed to import selected files: ${e.localizedMessage}"
                )
            }
        }
    }

    fun importDemoModelFolder() {
        val sampleFolder = File(context.filesDir, "sample_models").apply { mkdirs() }
        val demoFiles = listOf(
            Pair("Llama-3.2-1B-Instruct-Q4_K_M.gguf", 812L * 1024L * 1024L),
            Pair("DeepSeek-R1-Distill-Qwen-1.5B-Q8_0.gguf", 1620L * 1024L * 1024L),
            Pair("Whisper-Tiny-Multilingual-FP16.tflite", 75L * 1024L * 1024L)
        )

        val discovered = mutableListOf<DiscoveredModelFile>()
        val directSpecs = mutableListOf<ModelSpec>()
        for ((filename, size) in demoFiles) {
            val f = File(sampleFolder, filename)
            if (!f.exists() || f.length() < 1024) {
                _importProgressState.value = ModelImportProgress(
                    isImporting = false,
                    isCompleted = false,
                    errorMessage = "Model file not found or too small: $filename. Please download models first."
                )
                return
            }
            discovered.add(DiscoveredModelFile(null, f, filename, size))
        }

        // Demo weights are local files, so import synchronously: callers observe
        // ready-to-load models immediately.
        for (item in discovered) {
            importSingleFile(item, emptyList(), "Sample Models Folder")?.let { directSpecs.add(it) }
        }

        if (directSpecs.isEmpty()) {
            _importProgressState.value = ModelImportProgress(
                isImporting = false,
                isCompleted = false,
                errorMessage = "No compatible model files were imported."
            )
            return
        }

        registerImportedModels(directSpecs, "Sample Models Folder", directSpecs.size, directSpecs.size)
    }

    private suspend fun processDiscoveredModelList(
        files: List<DiscoveredModelFile>,
        folderName: String,
        sidecarFiles: List<DiscoveredModelFile> = emptyList()
    ) {
        if (files.isEmpty()) {
            _importProgressState.value = ModelImportProgress(
                isImporting = false,
                isCompleted = false,
                folderName = folderName,
                errorMessage = "No compatible model files (.gguf, .tflite, .onnx, .task, .mnn) were found in this directory."
            )
            return
        }

        val total = files.size
        val newImportedSpecs = mutableListOf<ModelSpec>()
        val failedFiles = mutableListOf<String>()

        _importProgressState.value = ModelImportProgress(
            isImporting = true,
            folderName = folderName,
            totalDiscovered = total,
            processedCount = 0,
            progressPercent = 15,
            stageStatusText = "Found $total model file(s). Copying weights into app storage..."
        )
        delay(200)

        for ((index, item) in files.withIndex()) {
            val currentNumber = index + 1
            val basePct = 15 + ((currentNumber.toFloat() / total.toFloat()) * 75).toInt()
            val sizeFormatted = formatBytes(item.sizeBytes)

            _importProgressState.value = _importProgressState.value.copy(
                currentFileName = item.displayName,
                currentFileSizeFormatted = sizeFormatted,
                processedCount = currentNumber,
                progressPercent = basePct - 5,
                stageStatusText = "Copying ${item.displayName} ($sizeFormatted) into app storage..."
            )

            val spec = importSingleFile(item, sidecarFiles, folderName)
            if (spec != null) {
                newImportedSpecs.add(spec)
            } else {
                failedFiles.add(item.displayName)
            }

            _importProgressState.value = _importProgressState.value.copy(
                progressPercent = basePct,
                stageStatusText = if (spec != null) {
                    "Copied and verified ${spec.name}"
                } else {
                    "Failed to copy ${item.displayName}"
                }
            )
        }

        if (newImportedSpecs.isEmpty()) {
            _importProgressState.value = ModelImportProgress(
                isImporting = false,
                isCompleted = false,
                folderName = folderName,
                errorMessage = "No model files could be copied: ${failedFiles.joinToString(", ")}. " +
                    "The files may be inaccessible or empty."
            )
            return
        }

        registerImportedModels(newImportedSpecs, folderName, total, total)

        if (failedFiles.isNotEmpty()) {
            _importProgressState.value = _importProgressState.value.copy(
                errorMessage = "Some files were skipped: ${failedFiles.joinToString(", ")}"
            )
        }
    }

    /**
     * Copy a single discovered model (and any tokenizer sidecars) into the
     * app-private models directory. Returns the ready-to-load spec, or null
     * when the source is unreadable/empty. Never registers a phantom model.
     */
    private fun importSingleFile(
        item: DiscoveredModelFile,
        sidecarFiles: List<DiscoveredModelFile>,
        folderName: String
    ): ModelSpec? {
        val parsed = parseModelSpecFromFileName(
            fileName = item.displayName,
            fileSizeBytes = item.sizeBytes,
            filePathOrUri = "",
            folderName = folderName
        )
        val extension = formatToExtension(parsed.format)
        val targetDir = File(modelsDir, parsed.id).apply { mkdirs() }
        val target = File(targetDir, "model.$extension")

        val copiedBytes = try {
            copySourceToFile(item.uri, item.file, target)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy ${item.displayName}: ${e.localizedMessage}")
            null
        } ?: return null

        if (copiedBytes <= 0L) {
            target.delete()
            return null
        }

        if (parsed.format == ModelFormat.ONNX || parsed.format == ModelFormat.MEDIAPIPE_TASK) {
            copySidecars(targetDir, sidecarFiles)
        }

        return parsed.copy(
            fileSizeBytes = copiedBytes,
            isDownloaded = true,
            downloadProgressPercent = 100,
            isDownloading = false,
            isPaused = false,
            downloadSpeedFormatted = "Local Storage",
            downloadedBytes = copiedBytes,
            downloadStatusText = "Imported • Ready to load",
            localFilePath = target.absolutePath
        )
    }

    private fun copySourceToFile(uri: Uri?, file: File?, target: File): Long {
        val sameFile = file != null && file.exists() && runCatching {
            file!!.canonicalPath == target.canonicalPath
        }.getOrDefault(false)
        if (sameFile) {
            return file!!.length()
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) temp.delete()

        try {
            FileOutputStream(temp).use { output ->
                when {
                    uri != null -> {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw java.io.FileNotFoundException("Cannot open imported model: $uri")
                        input.use { it.copyTo(output, 1 shl 20) }
                    }
                    file != null -> {
                        if (!file.isFile) throw java.io.FileNotFoundException("Missing model file: ${file.absolutePath}")
                        file.inputStream().use { it.copyTo(output, 1 shl 20) }
                    }
                    else -> throw java.io.FileNotFoundException("No model source supplied")
                }
            }
            val length = temp.length()
            if (length <= 0L) {
                temp.delete()
                return 0L
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target.length()
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun copySidecars(targetDir: File, sidecarFiles: List<DiscoveredModelFile>) {
        for (sidecar in sidecarFiles) {
            try {
                val target = File(targetDir, sidecar.displayName)
                copySourceToFile(sidecar.uri, sidecar.file, target)
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy sidecar ${sidecar.displayName}: ${e.localizedMessage}")
            }
        }
    }

    private fun registerImportedModels(
        specs: List<ModelSpec>,
        folderName: String,
        discoveredCount: Int,
        processedCount: Int
    ) {
        val currentList = _modelsState.value.toMutableList()
        for (newModel in specs) {
            val existingIndex = currentList.indexOfFirst {
                it.name.equals(newModel.name, ignoreCase = true) || it.id == newModel.id
            }
            if (existingIndex >= 0) {
                currentList[existingIndex] = newModel
            } else {
                currentList.add(0, newModel)
            }
        }

        // A freshly imported, verified model becomes the active model so the
        // next prompt runs against real weights instead of an unusable stub.
        val firstImported = specs.firstOrNull()
        val activeName = firstImported?.name
        _modelsState.value = if (firstImported != null) {
            currentList.map { model -> model.copy(isActive = (model.id == firstImported.id)) }
        } else {
            currentList
        }

        saveImportedModels(specs)
        firstImported?.let { active ->
            scope.launch {
                try {
                    repository.setActiveModel(active.id)
                } catch (_: Exception) {}
            }
        }

        _importProgressState.value = ModelImportProgress(
            isImporting = false,
            isCompleted = true,
            folderName = folderName,
            totalDiscovered = discoveredCount,
            processedCount = processedCount,
            importedCount = specs.size,
            progressPercent = 100,
            stageStatusText = "Imported ${specs.size} model(s). '${activeName ?: ""}' is loaded and ready for inference.",
            importedModelNames = specs.map { it.name },
            autoActivatedModelName = activeName
        )
    }

    fun dismissImportProgress() {
        _importProgressState.value = ModelImportProgress()
    }

    fun cancelImport() {
        activeImportJob?.cancel()
        activeImportJob = null
        _importProgressState.value = ModelImportProgress(
            isImporting = false,
            isCompleted = false,
            errorMessage = "Import canceled by user."
        )
    }

    private fun isModelFile(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".gguf") ||
                lower.endsWith(".tflite") ||
                lower.endsWith(".onnx") ||
                lower.endsWith(".task") ||
                lower.endsWith(".mnn") ||
                lower.endsWith(".aicore")
    }

    private fun isSidecarFile(name: String): Boolean =
        SIDECAR_FILES.contains(name.lowercase(Locale.ROOT))

    fun parseModelSpecFromFileName(
        fileName: String,
        fileSizeBytes: Long,
        filePathOrUri: String = "",
        folderName: String = ""
    ): ModelSpec {
        val lower = fileName.lowercase(Locale.ROOT)

        val format = when {
            lower.endsWith(".gguf") -> ModelFormat.GGUF
            lower.endsWith(".tflite") -> ModelFormat.TFLITE
            lower.endsWith(".onnx") -> ModelFormat.ONNX
            lower.endsWith(".task") -> ModelFormat.MEDIAPIPE_TASK
            lower.endsWith(".mnn") -> ModelFormat.MNN_LLM
            lower.endsWith(".aicore") -> ModelFormat.ANDROID_AICORE
            else -> ModelFormat.MEDIAPIPE_TASK
        }

        val quantization = when {
            lower.contains("q4_k_m") -> "Q4_K_M"
            lower.contains("q4_0") -> "Q4_0"
            lower.contains("q5_k_m") -> "Q5_K_M"
            lower.contains("q8_0") -> "Q8_0"
            lower.contains("int8") -> "INT8"
            lower.contains("fp16") -> "FP16"
            lower.contains("fp32") -> "FP32"
            format == ModelFormat.GGUF -> "Q4_K_M"
            format == ModelFormat.TFLITE -> "Dynamic INT8"
            else -> "FP16"
        }

        // Infer parameters from filename or size
        val paramMatchB = Regex("(\\d+(\\.\\d+)?)\\s*[bB]").find(fileName)
        val paramMatchM = Regex("(\\d+)\\s*[mM]").find(fileName)

        val parameterCount = when {
            paramMatchB != null -> "${paramMatchB.groupValues[1]} Billion"
            paramMatchM != null -> "${paramMatchM.groupValues[1]} Million"
            fileSizeBytes > 3500L * 1024L * 1024L -> "7.0 Billion"
            fileSizeBytes > 1800L * 1024L * 1024L -> "3.5 Billion"
            fileSizeBytes > 700L * 1024L * 1024L -> "1.5 Billion"
            fileSizeBytes > 300L * 1024L * 1024L -> "500 Million"
            else -> "120 Million"
        }

        val category = when {
            lower.contains("code") || lower.contains("python") || lower.contains("coder") -> ModelCategory.CODE_ANALYSIS
            lower.contains("bert") || lower.contains("embed") || lower.contains("vector") || lower.contains("similarity") -> ModelCategory.EMBEDDINGS_CLASSIFICATION
            lower.contains("vision") || lower.contains("multimodal") || lower.contains("vl") || lower.contains("whisper") || lower.contains("audio") -> ModelCategory.VISION_MULTIMODAL
            else -> ModelCategory.CHAT_REASONING
        }

        // Clean display title
        val baseWithoutExt = fileName.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

        val cleanTitle = if (baseWithoutExt.isNotBlank()) baseWithoutExt else "Imported Model"

        // Context length heuristic
        val contextLength = when {
            lower.contains("8k") || lower.contains("8192") -> 8192
            lower.contains("16k") -> 16384
            lower.contains("32k") -> 32768
            format == ModelFormat.GGUF -> 4096
            else -> 2048
        }

        val ramRequired = (fileSizeBytes * 1.35).toLong().coerceAtLeast(180L * 1024L * 1024L)

        val id = "imported_" + UUID.nameUUIDFromBytes(fileName.toByteArray()).toString().take(12)

        return ModelSpec(
            id = id,
            name = cleanTitle,
            parameterCount = parameterCount,
            format = format,
            quantization = quantization,
            fileSizeBytes = fileSizeBytes,
            requiredRamBytes = ramRequired,
            contextLength = contextLength,
            description = "Locally imported from '$folderName'. Ready for instant acceleration on local hardware.",
            category = category,
            downloadUrl = filePathOrUri,
            sha256Checksum = "local-imported-verified",
            isDownloaded = false,
            downloadProgressPercent = 0,
            isDownloading = false,
            isPaused = false,
            downloadSpeedFormatted = "",
            downloadedBytes = 0L,
            downloadStatusText = "Ready to import",
            isActive = false,
            isImported = true,
            localFilePath = "",
            sourceFolder = folderName
        )
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            "$mb MB"
        }
    }

    // --- Model file resolution ---

    /** Flat layout used by catalog downloads: `<models>/<id>.<ext>`. */
    private fun flatModelFile(id: String, format: ModelFormat): File =
        File(modelsDir, "$id.${formatToExtension(format)}")

    /** Per-model directory used by imports so tokenizer sidecars stay isolated. */
    private fun importedModelDir(id: String): File = File(modelsDir, id)

    private fun importedModelFile(id: String, format: ModelFormat): File =
        File(importedModelDir(id), "model.${formatToExtension(format)}")

    /**
     * Find the real weight file for a spec, tolerating old/blank paths.
     * Returns null when the model is not actually present on disk.
     */
    private fun resolveExistingModelFile(spec: ModelSpec): File? {
        val extension = formatToExtension(spec.format)
        val candidates = mutableListOf<File>()
        if (spec.localFilePath.isNotBlank()) candidates.add(File(spec.localFilePath))
        candidates.add(flatModelFile(spec.id, spec.format))
        candidates.add(importedModelFile(spec.id, spec.format))
        val dir = importedModelDir(spec.id)
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { candidate ->
                if (candidate.isFile && candidate.extension.equals(extension, ignoreCase = true)) {
                    candidates.add(candidate)
                }
            }
        }
        return candidates
            .distinctBy { it.absolutePath }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    // --- Persistence for Imported Models ---

    private fun loadInitialWithPersistedModels(): List<ModelSpec> {
        val list = initialModels.map { m ->
            val resolved = resolveExistingModelFile(m)
            m.copy(
                localFilePath = resolved?.absolutePath ?: flatModelFile(m.id, m.format).absolutePath,
                isDownloaded = resolved != null,
                isActive = resolved != null && m.isActive,
                downloadProgressPercent = if (resolved != null) 100 else 0,
                downloadedBytes = resolved?.length() ?: 0L,
                downloadStatusText = if (resolved != null) "Installed" else "Not downloaded"
            )
        }.toMutableList()
        try {
            if (importedRegistryFile.exists()) {
                val json = importedRegistryFile.readText()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val raw = jsonToModelSpec(obj)
                    val resolved = resolveExistingModelFile(raw)
                    val spec = if (resolved != null) {
                        raw.copy(
                            localFilePath = resolved.absolutePath,
                            isDownloaded = true,
                            downloadedBytes = resolved.length(),
                            fileSizeBytes = resolved.length(),
                            downloadProgressPercent = 100,
                            downloadStatusText = "Imported • Ready to load"
                        )
                    } else {
                        raw.copy(
                            localFilePath = "",
                            isDownloaded = false,
                            downloadedBytes = 0L,
                            downloadProgressPercent = 0,
                            downloadStatusText = "File missing — re-import required"
                        )
                    }
                    if (list.none { it.id == spec.id }) {
                        list.add(0, spec)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun hydrateFromDatabase() {
        val dbModels = try {
            repository.getAllModelsOnce()
        } catch (_: Exception) {
            emptyList()
        }
        if (dbModels.isEmpty()) return

        val byId = LinkedHashMap<String, ModelSpec>()
        _modelsState.value.forEach { byId[it.id] = it }

        for (raw in dbModels) {
            val resolved = resolveExistingModelFile(raw)
            val db = raw.copy(
                localFilePath = resolved?.absolutePath ?: raw.localFilePath,
                isDownloaded = resolved != null,
                downloadProgressPercent = if (resolved != null) 100 else raw.downloadProgressPercent,
                downloadedBytes = resolved?.length() ?: 0L,
                downloadStatusText = when {
                    resolved != null -> "Installed"
                    raw.isImported -> "File missing — re-import required"
                    else -> "Not downloaded"
                }
            )
            val existing = byId[db.id]
            byId[db.id] = if (existing != null) {
                existing.copy(
                    fileSizeBytes = db.fileSizeBytes,
                    isDownloaded = db.isDownloaded,
                    localFilePath = db.localFilePath,
                    downloadProgressPercent = db.downloadProgressPercent,
                    downloadedBytes = db.downloadedBytes,
                    downloadStatusText = db.downloadStatusText,
                    isImported = db.isImported,
                    isActive = db.isDownloaded && db.isActive
                )
            } else {
                db.copy(isActive = db.isDownloaded && db.isActive)
            }
        }

        val merged = byId.values.toList()
        val active = merged.firstOrNull { it.isActive && it.isDownloaded }
            ?: merged.firstOrNull { it.isDownloaded }
        val reconciled = merged.map { it.copy(isActive = active != null && it.id == active.id) }

        _modelsState.value = reconciled
        try {
            repository.insertAllModels(reconciled)
        } catch (_: Exception) {}
    }

    private fun saveImportedModels(newModels: List<ModelSpec>) {
        // Save to Room Database
        scope.launch {
            try {
                repository.insertAllModels(newModels)
            } catch (_: Exception) {}
        }

        try {
            val existing = mutableListOf<ModelSpec>()
            if (importedRegistryFile.exists()) {
                val json = importedRegistryFile.readText()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    existing.add(jsonToModelSpec(array.getJSONObject(i)))
                }
            }
            for (m in newModels) {
                val idx = existing.indexOfFirst { it.id == m.id }
                if (idx >= 0) existing[idx] = m else existing.add(m)
            }

            val array = JSONArray()
            for (m in existing) {
                array.put(modelSpecToJson(m))
            }
            importedRegistryFile.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    private fun removeImportedModelFromPersistence(modelId: String) {
        scope.launch {
            try {
                repository.deleteModel(modelId)
            } catch (_: Exception) {}
        }

        try {
            if (!importedRegistryFile.exists()) return
            val json = importedRegistryFile.readText()
            val array = JSONArray(json)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != modelId) {
                    newArray.put(obj)
                }
            }
            importedRegistryFile.writeText(newArray.toString(2))
        } catch (_: Exception) {}
    }

    private fun modelSpecToJson(m: ModelSpec): JSONObject {
        return JSONObject().apply {
            put("id", m.id)
            put("name", m.name)
            put("parameterCount", m.parameterCount)
            put("format", m.format.name)
            put("quantization", m.quantization)
            put("fileSizeBytes", m.fileSizeBytes)
            put("requiredRamBytes", m.requiredRamBytes)
            put("contextLength", m.contextLength)
            put("description", m.description)
            put("category", m.category.name)
            put("downloadUrl", m.downloadUrl)
            put("sha256Checksum", m.sha256Checksum)
            put("localFilePath", m.localFilePath)
            put("sourceFolder", m.sourceFolder)
        }
    }

    private fun jsonToModelSpec(obj: JSONObject): ModelSpec {
        val formatName = obj.optString("format", "GGUF")
        val format = try { ModelFormat.valueOf(formatName) } catch (_: Exception) { ModelFormat.GGUF }
        val categoryName = obj.optString("category", "CHAT_REASONING")
        val category = try { ModelCategory.valueOf(categoryName) } catch (_: Exception) { ModelCategory.CHAT_REASONING }

        val size = obj.optLong("fileSizeBytes", 500L * 1024L * 1024L)

        return ModelSpec(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Imported Model"),
            parameterCount = obj.optString("parameterCount", "1.5 Billion"),
            format = format,
            quantization = obj.optString("quantization", "Q4_K_M"),
            fileSizeBytes = size,
            requiredRamBytes = obj.optLong("requiredRamBytes", (size * 1.35).toLong()),
            contextLength = obj.optInt("contextLength", 4096),
            description = obj.optString("description", "Locally imported model."),
            category = category,
            downloadUrl = obj.optString("downloadUrl", ""),
            sha256Checksum = obj.optString("sha256Checksum", "local-verified"),
            isDownloaded = false,
            downloadProgressPercent = 0,
            isDownloading = false,
            isPaused = false,
            downloadSpeedFormatted = "",
            downloadedBytes = 0L,
            downloadStatusText = "Imported",
            isActive = false,
            isImported = true,
            localFilePath = obj.optString("localFilePath", ""),
            sourceFolder = obj.optString("sourceFolder", "")
        )
    }

    // --- Standard Catalog Downloads ---

    fun startDownload(modelId: String) {
        if (activeDownloadJobs.containsKey(modelId)) return

        val initialModel = _modelsState.value.firstOrNull { it.id == modelId } ?: return

        // Memory and storage constraint safety evaluation
        val safety = memorySafetyManager.evaluateModelSafety(initialModel)
        if (!safety.isStorageSufficient) {
            updateModel(modelId) {
                it.copy(
                    isDownloading = false,
                    downloadStatusText = "Failed: Insufficient storage space"
                )
            }
            return
        }

        val totalBytes = initialModel.fileSizeBytes
        val startPercent = if (initialModel.isPaused) initialModel.downloadProgressPercent else 0

        // This manager is the single downloader. Starting ModelDownloadService
        // as well used to race two writers against the same destination file,
        // producing truncated/corrupted weights.
        val job = scope.launch {
            updateModel(modelId) {
                it.copy(
                    isDownloading = true,
                    isPaused = false,
                    downloadProgressPercent = startPercent,
                    downloadedBytes = (totalBytes * (startPercent / 100.0)).toLong(),
                    downloadSpeedFormatted = "0 MB/s",
                    downloadStatusText = "Starting download..."
                )
            }

            val ext = formatToExtension(initialModel.format)
            val modelFile = File(modelsDir, "$modelId.$ext")
            val url = initialModel.downloadUrl

            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateModel(modelId) {
                        it.copy(
                            isDownloading = false,
                            downloadStatusText = "Failed: HTTP ${response.code}"
                        )
                    }
                    return@launch
                }

                val body = response.body ?: run {
                    updateModel(modelId) {
                        it.copy(
                            isDownloading = false,
                            downloadStatusText = "Failed: Empty response"
                        )
                    }
                    return@launch
                }

                val contentLength = body.contentLength().coerceAtLeast(totalBytes)
                val source = body.source()
                val sink = modelFile.sink().buffer()

                val buffer = okio.Buffer()
                var totalBytesRead = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var lastUpdateBytes = 0L

                while (true) {
                    val bytesRead = source.read(buffer, 8192)
                    if (bytesRead == -1L) break
                    sink.write(buffer, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= 500L) {
                        val elapsed = (now - lastUpdateTime).coerceAtLeast(1L)
                        val bytesSinceLastUpdate = totalBytesRead - lastUpdateBytes
                        val speedBps = (bytesSinceLastUpdate * 1000L) / elapsed
                        val speedMbps = speedBps / (1024.0 * 1024.0)
                        val progress = ((totalBytesRead.toDouble() / contentLength.toDouble()) * 100.0).toInt().coerceIn(0, 100)

                        updateModel(modelId) {
                            it.copy(
                                downloadProgressPercent = progress,
                                downloadedBytes = totalBytesRead,
                                downloadSpeedFormatted = String.format("%.1f MB/s", speedMbps),
                                downloadStatusText = "Downloading..."
                            )
                        }

                        lastUpdateTime = now
                        lastUpdateBytes = totalBytesRead
                    }
                }

                sink.close()
                source.close()

                val writtenBytes = modelFile.length()
                if (totalBytesRead <= 0L || writtenBytes <= 0L) {
                    modelFile.delete()
                    throw java.io.IOException("Downloaded file was empty")
                }

                val noActiveModel = getUsableActiveModel() == null
                _modelsState.value = _modelsState.value.map { model ->
                    if (model.id == modelId) {
                        model.copy(
                            isDownloading = false,
                            isPaused = false,
                            isDownloaded = true,
                            isActive = if (noActiveModel) true else model.isActive,
                            downloadProgressPercent = 100,
                            downloadedBytes = writtenBytes,
                            fileSizeBytes = writtenBytes,
                            downloadSpeedFormatted = "Ready",
                            downloadStatusText = "Installed",
                            localFilePath = modelFile.absolutePath
                        )
                    } else if (noActiveModel) {
                        model.copy(isActive = false)
                    } else {
                        model
                    }
                }

                if (noActiveModel) {
                    scope.launch {
                        try {
                            repository.setActiveModel(modelId)
                        } catch (_: Exception) {}
                    }
                }

                try {
                    repository.updateModelDownloadStatus(
                        id = modelId,
                        isDownloaded = true,
                        progress = 100,
                        path = modelFile.absolutePath,
                        fileSize = writtenBytes
                    )
                } catch (_: Exception) {}

            } catch (e: Exception) {
                updateModel(modelId) {
                    it.copy(
                        isDownloading = false,
                        downloadStatusText = "Failed: ${e.localizedMessage ?: "Network error"}"
                    )
                }
            }

            activeDownloadJobs.remove(modelId)
        }
        activeDownloadJobs[modelId] = job
    }

    fun pauseDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        updateModel(modelId) {
            it.copy(
                isDownloading = false,
                isPaused = true,
                downloadSpeedFormatted = "Paused",
                downloadStatusText = "Download paused at ${it.downloadProgressPercent}%"
            )
        }
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
        updateModel(modelId) {
            it.copy(
                isDownloading = false,
                isPaused = false,
                downloadProgressPercent = 0,
                downloadedBytes = 0L,
                downloadSpeedFormatted = "",
                downloadStatusText = ""
            )
        }
        // Remove the partial weight file so a later download starts clean.
        for (ext in listOf("gguf", "onnx", "tflite", "task", "mnn", "aicore")) {
            val file = File(modelsDir, "$modelId.$ext")
            if (file.exists()) file.delete()
        }
        scope.launch {
            try {
                repository.updateModelDownloadStatus(modelId, false, 0, "", 0L)
            } catch (_: Exception) {}
        }
    }

    fun deleteModel(modelId: String) {
        cancelDownload(modelId)
        for (ext in listOf("bin", "gguf", "onnx", "tflite", "task", "mnn", "aicore")) {
            val file = File(modelsDir, "$modelId.$ext")
            if (file.exists()) file.delete()
        }
        importedModelDir(modelId).deleteRecursively()

        val target = _modelsState.value.firstOrNull { it.id == modelId }

        if (target != null && target.isImported) {
            // Completely remove imported model
            removeImportedModelFromPersistence(modelId)
            val updated = _modelsState.value.filter { it.id != modelId }
            if (target.isActive) {
                val fallback = updated.firstOrNull { it.isDownloaded }
                _modelsState.value = updated.map { it.copy(isActive = (it.id == fallback?.id)) }
            } else {
                _modelsState.value = updated
            }
        } else {
            // Built-in catalog model reset
            updateModel(modelId) {
                it.copy(
                    isDownloaded = false,
                    downloadProgressPercent = 0,
                    downloadedBytes = 0L,
                    downloadSpeedFormatted = "",
                    downloadStatusText = "",
                    isActive = false
                )
            }
            scope.launch {
                try {
                    repository.updateModelDownloadStatus(modelId, false, 0, "", 0L)
                } catch (_: Exception) {}
            }
        }
    }

    /** Activate a model only when its weights are actually present on disk. */
    fun setActiveModel(modelId: String): Boolean {
        val target = _modelsState.value.firstOrNull { it.id == modelId } ?: return false
        val resolved = resolveExistingModelFile(target) ?: run {
            updateModel(modelId) {
                it.copy(
                    isActive = false,
                    isDownloaded = false,
                    localFilePath = "",
                    downloadStatusText = if (it.isImported) {
                        "File missing — re-import required"
                    } else {
                        "Not downloaded"
                    }
                )
            }
            return false
        }

        _modelsState.value = _modelsState.value.map { model ->
            model.copy(
                isActive = model.id == modelId,
                isDownloaded = if (model.id == modelId) true else model.isDownloaded,
                localFilePath = if (model.id == modelId) resolved.absolutePath else model.localFilePath,
                fileSizeBytes = if (model.id == modelId) resolved.length() else model.fileSizeBytes
            )
        }
        scope.launch {
            try {
                repository.setActiveModel(modelId)
            } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Returns only a model whose weights are actually present on disk. There is
     * deliberately no "first model" fallback: callers must handle null so the
     * UI can say "no model loaded" instead of running a phantom checkpoint.
     */
    fun getActiveModel(): ModelSpec? = getUsableActiveModel()

    /**
     * Strict resolver for inference/API paths: returns a model whose weights
     * really exist, or null so callers can report the truth instead of
     * running against a phantom model.
     */
    fun getUsableActiveModel(): ModelSpec? {
        val active = _modelsState.value.firstOrNull { it.isActive }
        if (active != null) {
            resolveExistingModelFile(active)?.let { file ->
                return active.copy(
                    isDownloaded = true,
                    localFilePath = file.absolutePath,
                    fileSizeBytes = file.length()
                )
            }
        }
        val fallback = _modelsState.value.firstOrNull { it.isDownloaded && resolveExistingModelFile(it) != null }
            ?: _modelsState.value.firstNotNullOfOrNull { spec -> resolveExistingModelFile(spec)?.let { spec } }
            ?: return null
        return fallback.copy(
            isDownloaded = true,
            localFilePath = resolveExistingModelFile(fallback)?.absolutePath ?: fallback.localFilePath
        )
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
        val sanitized = name.lowercase().replace(" ", "-").filter { it.isLetterOrDigit() || it == '-' }
        val id = "custom-$sanitized-${System.currentTimeMillis() % 10000}"
        val ext = formatToExtension(format)
        val sizeBytes = fileSizeMb * 1024L * 1024L
        val ramRequired = (sizeBytes * 1.35).toLong()
        val localPath = File(modelsDir, "$id.$ext").absolutePath

        val newModel = ModelSpec(
            id = id,
            name = name,
            parameterCount = parameterCount,
            format = format,
            quantization = quantization,
            fileSizeBytes = sizeBytes,
            requiredRamBytes = ramRequired,
            contextLength = 4096,
            description = "Custom $format model from URL: $downloadUrl",
            category = category,
            downloadUrl = downloadUrl,
            sha256Checksum = "custom-user-model",
            isDownloaded = false,
            downloadProgressPercent = 0,
            isDownloading = false,
            isPaused = false,
            downloadSpeedFormatted = "",
            downloadedBytes = 0L,
            downloadStatusText = "Added to library • Ready to download",
            isActive = false,
            isImported = false,
            localFilePath = localPath,
            sourceFolder = "Custom Model URL"
        )

        val currentList = _modelsState.value.toMutableList()
        currentList.add(0, newModel)
        _modelsState.value = currentList

        scope.launch {
            try {
                repository.insertModel(newModel)
            } catch (_: Exception) {}
        }

        startDownload(id)
        return newModel
    }

    fun verifyModelChecksum(modelId: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val model = _modelsState.value.firstOrNull { it.id == modelId }
            if (model == null) {
                onResult(false, "Model not found in library.")
                return@launch
            }
            val targetFile = resolveExistingModelFile(model)

            if (targetFile == null) {
                onResult(false, "Model weight file not found on disk at ${model.localFilePath.ifBlank { modelsDir.path }}")
                return@launch
            }

            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16384) // 16KB bounded streaming buffer to prevent OOM
                targetFile.inputStream().use { input ->
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        digest.update(buffer, 0, bytesRead)
                        bytesRead = input.read(buffer)
                    }
                }
                val hashBytes = digest.digest()
                val computedHash = hashBytes.joinToString("") { "%02x".format(it) }
                val isValid = model.sha256Checksum.isBlank() ||
                        model.sha256Checksum == "local-verified" ||
                        model.sha256Checksum == "local-imported-verified" ||
                        model.sha256Checksum == "custom-user-model" ||
                        model.sha256Checksum.equals(computedHash, ignoreCase = true)

                onResult(isValid, computedHash)
            } catch (e: Exception) {
                onResult(false, "Verification error: ${e.localizedMessage}")
            }
        }
    }

    private fun updateModel(modelId: String, transform: (ModelSpec) -> ModelSpec) {
        _modelsState.value = _modelsState.value.map { if (it.id == modelId) transform(it) else it }
    }

    companion object {
        private const val TAG = "ModelDownloadManager"

        private val SIDECAR_FILES = setOf(
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json",
            "vocab.json",
            "vocab.txt",
            "merges.txt",
            "config.json",
            "generation_config.json"
        )

        fun formatToExtension(format: ModelFormat): String = when (format) {
            ModelFormat.GGUF -> "gguf"
            ModelFormat.ONNX -> "onnx"
            ModelFormat.TFLITE -> "tflite"
            ModelFormat.MEDIAPIPE_TASK -> "task"
            ModelFormat.MNN_LLM -> "mnn"
            ModelFormat.ANDROID_AICORE -> "aicore"
        }
    }
}
