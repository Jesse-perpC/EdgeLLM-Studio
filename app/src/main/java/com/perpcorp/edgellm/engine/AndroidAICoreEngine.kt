package com.perpcorp.edgellm.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.perpcorp.edgellm.data.model.AiPersona
import com.perpcorp.edgellm.data.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Android AICore / Gemini Nano Engine using Google Generative AI client.
 *
 * On-device Gemini Nano requires Android 14+ (API 34+), the AI Core system
 * service, and the model downloaded via Google Play Services. This engine
 * only reports the backend as available after a real on-device generation
 * has succeeded — it never claims availability from a bare model
 * construction, and it never synthesizes output.
 *
 * References:
 * - https://github.com/google-gemini/generative-ai-android
 * - https://developer.android.com/ai/on-device
 */
class AndroidAICoreEngine(private val context: Context?) {

    private companion object {
        private const val TAG = "AICoreEngine"
        private const val MIN_API = 34 // Android 14
        private const val ON_DEVICE_MODEL = "gemini-nano"
        private const val PROBE_PROMPT = "ping"
    }

    @Volatile
    private var modelInstance: GenerativeModel? = null

    @Volatile
    private var cachedStatus: AICoreStatus? = null

    data class AICoreStatus(
        val isAvailable: Boolean,
        val message: String,
        val modelVersion: String?,
        val acceleratorType: String?
    )

    data class AICoreTokenChunk(
        val token: String,
        val accumulatedText: String,
        val tokenCount: Int,
        val tokensPerSecond: Float,
        val timeToFirstTokenMs: Long,
        val isComplete: Boolean,
        val backendUsed: String
    )

    /**
     * Check if AICore/Gemini Nano is actually usable on this device.
     *
     * Availability is only reported after a real generation succeeds. A
     * successful model construction alone is not treated as availability,
     * because an empty API key plus a model name does not prove that the
     * on-device runtime answered.
     */
    suspend fun checkAvailability(): AICoreStatus {
        cachedStatus?.let { return it }

        if (Build.VERSION.SDK_INT < MIN_API) {
            return AICoreStatus(
                isAvailable = false,
                message = "Requires Android 14+ (API $MIN_API)",
                modelVersion = null,
                acceleratorType = null
            ).also { cachedStatus = it }
        }

        return try {
            val model = GenerativeModel(
                modelName = ON_DEVICE_MODEL,
                apiKey = ""
            )
            // Probe with a real generation so we never report availability
            // without the on-device runtime actually answering.
            model.generateContent(PROBE_PROMPT)
            modelInstance = model
            AICoreStatus(
                isAvailable = true,
                message = "Gemini Nano available on-device",
                modelVersion = ON_DEVICE_MODEL,
                acceleratorType = "AI Core"
            ).also { cachedStatus = it }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano not available: ${e.message}")
            AICoreStatus(
                isAvailable = false,
                message = "AI Core not ready or model not downloaded: ${e.message}",
                modelVersion = null,
                acceleratorType = null
            ).also { cachedStatus = it }
        }
    }

    /**
     * Generate a response using on-device Gemini Nano.
     *
     * The Generative AI client does not expose true token streaming for the
     * on-device model, so the full response is emitted as a single complete
     * chunk with measured timing. No artificial delay is added.
     */
    fun generateStreamingResponse(
        prompt: String,
        systemInstruction: String = "",
        model: ModelSpec? = null,
        persona: AiPersona? = null
    ): Flow<AICoreTokenChunk> = flow {
        val startTime = System.currentTimeMillis()

        val status = checkAvailability()
        if (!status.isAvailable || modelInstance == null) {
            emit(
                AICoreTokenChunk(
                    token = "",
                    accumulatedText = "",
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = 0L,
                    isComplete = true,
                    backendUsed = "Unavailable: ${status.message}"
                )
            )
            return@flow
        }

        val fullPrompt = if (systemInstruction.isNotBlank()) "$systemInstruction\n\n$prompt" else prompt

        try {
            val response: GenerateContentResponse = modelInstance!!.generateContent(fullPrompt)
            val text = response.text ?: ""
            val elapsedMs = System.currentTimeMillis() - startTime
            val elapsedSec = (elapsedMs / 1000f).coerceAtLeast(0.001f)
            val tokenCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size

            emit(
                AICoreTokenChunk(
                    token = text,
                    accumulatedText = text,
                    tokenCount = tokenCount,
                    tokensPerSecond = ((tokenCount / elapsedSec) * 10).toInt() / 10f,
                    timeToFirstTokenMs = elapsedMs,
                    isComplete = true,
                    backendUsed = "Gemini Nano (AI Core)"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Nano generation failed", e)
            emit(
                AICoreTokenChunk(
                    token = "",
                    accumulatedText = "",
                    tokenCount = 0,
                    tokensPerSecond = 0f,
                    timeToFirstTokenMs = System.currentTimeMillis() - startTime,
                    isComplete = true,
                    backendUsed = "Unavailable: ${e.message}"
                )
            )
        }
    }

    /**
     * Synchronous availability hint for UI. Only true once a generation has
     * been verified — never guessed from the API level alone.
     */
    fun isAvailableSync(): Boolean = cachedStatus?.isAvailable ?: false

    /**
     * Lightweight capability hint (Android version only). Does not prove the
     * on-device model is present; call [checkAvailability] for the truth.
     */
    fun quickCapabilityCheck(): Boolean = Build.VERSION.SDK_INT >= MIN_API
}
