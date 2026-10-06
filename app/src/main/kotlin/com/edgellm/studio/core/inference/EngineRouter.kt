package com.edgellm.studio.core.inference

import java.io.File

// Extension function for EngineResult sealed class
private fun <T, R> EngineResult<T>.map(transform: (T) -> R): EngineResult<R> {
    return when (this) {
        is EngineResult.Success -> EngineResult.Success(transform(value))
        is EngineResult.Failure -> EngineResult.Failure(error)
    }
}

class EngineRouter(
    private val llamaEngine: InferenceEngine,
    private val mediaPipeEngine: InferenceEngine,
    private val execuTorchEngine: InferenceEngine?
) {
    private var activeEngine: InferenceEngine? = null

    fun selectEngineForFile(modelFile: File): InferenceEngine {
        val extension = modelFile.extension.lowercase()
        return when (extension) {
            "gguf" -> llamaEngine
            "bin", "task" -> mediaPipeEngine
            "pte" -> execuTorchEngine
                ?: throw IllegalArgumentException("ExecuTorch engine not configured")
            else -> throw IllegalArgumentException(
                "Unsupported model file extension: .$extension"
            )
        }
    }

    suspend fun switchAndLoad(modelFile: File, config: EngineConfig): EngineResult<InferenceEngine> {
        // Step 1: Safely close active engine to reclaim RAM/VRAM
        activeEngine?.close()
        activeEngine = null

        // Step 2: Resolve suitable engine driver
        val engine = selectEngineForFile(modelFile)

        // Step 3: Load new model
        return engine.loadModel(config).map {
            activeEngine = engine
            engine
        }
    }

    fun getActiveEngine(): InferenceEngine? = activeEngine
}