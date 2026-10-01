package com.example.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device weight inference via MediaPipe tasks-genai.
 *
 * - Returns non-null text only when a valid .task model file + Context exist
 *   and the native call succeeds.
 * - Returns null on any missing file / missing Context / native failure so the
 *   caller can fall back to the honest offline-KB path. Never throws.
 */
class MediaPipeInferenceEngine(private val context: Context? = null) {

    companion object {
        private const val TAG = "MediaPipeEngine"
    }

    @Volatile
    private var cachedLlm: LlmInference? = null

    @Volatile
    private var cachedKey: String? = null

    enum class MediaPipeDelegate(val displayName: String) {
        GPU("GPU (OpenCL / Vulkan)"),
        CPU("CPU (Multi-Threaded ARM)")
    }

    data class MediaPipeLlmOptions(
        val modelPath: String,
        val maxTokens: Int = 1024,
        val topK: Int = 40,
        val temperature: Float = 0.7f,
        val randomSeed: Int = 0,
        val delegate: MediaPipeDelegate = MediaPipeDelegate.GPU
    )

    data class MediaPipeTokenChunk(
        val token: String,
        val accumulatedText: String,
        val tokenCount: Int,
        val tokensPerSecond: Float,
        val timeToFirstTokenMs: Long,
        val isComplete: Boolean,
        val delegateUsed: MediaPipeDelegate
    )

    suspend fun tryRealLlmInference(prompt: String, options: MediaPipeLlmOptions): String? {
        val appContext = context?.applicationContext ?: return null
        val path = options.modelPath.ifBlank { return null }
        if (!File(path).exists()) return null
        var session: LlmInferenceSession? = null
        return try {
            val llm = withContext(Dispatchers.IO) { getOrCreateLlm(appContext, options, path) }
            val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(options.topK)
                .setTemperature(options.temperature)
                .setRandomSeed(options.randomSeed)
                .build()
            session = LlmInferenceSession.createFromOptions(llm, sessionOpts)
            val raw = withContext(Dispatchers.IO) {
                session!!.addQueryChunk(prompt)
                session!!.generateResponse()
            }
            val text = raw.trim()
            if (text.isEmpty()) {
                Log.w(TAG, "Native returned empty text; using fallback")
                null
            } else text
        } catch (e: Throwable) {
            Log.w(TAG, "Real inference unavailable (${e.message}); using fallback")
            closeQuietly()
            null
        } finally {
            try { session?.close() } catch (_: Throwable) { }
        }
    }

    @Synchronized
    private fun getOrCreateLlm(appContext: Context, options: MediaPipeLlmOptions, modelPath: String): LlmInference {
        val delegateValue = when (options.delegate) {
            MediaPipeDelegate.GPU -> LlmInference.LlmInferenceOptions.PreferredBackend.GPU
            MediaPipeDelegate.CPU -> LlmInference.LlmInferenceOptions.PreferredBackend.CPU
        }
        val key = "$modelPath|${options.maxTokens}|${delegateValue}"
        val existing = cachedLlm
        if (existing != null && cachedKey == key) return existing
        closeQuietly()
        val llmOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(options.maxTokens)
            .setPreferredBackend(delegateValue)
            .build()
        val created = LlmInference.createFromOptions(appContext, llmOptions)
        cachedLlm = created
        cachedKey = key
        return created
    }

    @Synchronized
    fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        try { cachedLlm?.close() } catch (_: Throwable) { }
        finally {
            cachedLlm = null
            cachedKey = null
        }
    }
}
