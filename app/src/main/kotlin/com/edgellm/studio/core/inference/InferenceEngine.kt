package com.edgellm.studio.core.inference

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

data class EngineConfig(
    val modelPath: String,
    val contextWindow: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val useNpu: Boolean = false
)

data class GenerationParams(
    val prompt: String,
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512,
    val stopSequences: List<String> = emptyList()
)

data class InferenceMetrics(
    val timeToFirstTokenMs: Long,
    val tokensPerSecond: Double,
    val totalTokens: Int,
    val memoryUsedMb: Float
)

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class Completed(val metrics: InferenceMetrics) : StreamEvent()
    data class Error(val throwable: Throwable) : StreamEvent()
}

sealed class EngineResult<out T> {
    data class Success<T>(val value: T) : EngineResult<T>()
    data class Failure<T>(val error: String) : EngineResult<T>
}

interface InferenceEngine : Closeable {
    val engineName: String
    val isInitialized: Boolean

    suspend fun loadModel(config: EngineConfig): EngineResult<Unit>
    fun generateStream(params: GenerationParams): Flow<StreamEvent>
    fun cancelGeneration()
}