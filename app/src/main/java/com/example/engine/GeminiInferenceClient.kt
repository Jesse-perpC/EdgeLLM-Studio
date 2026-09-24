package com.example.engine

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AiPersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiInferenceClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiInferenceClient"
        // Following system skill guidelines: Default model for basic text & reasoning tasks
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return !key.isNullOrBlank() && key != "DEFAULT_API_KEY" && !key.startsWith("YOUR_")
    }

    suspend fun generateContent(
        prompt: String,
        persona: AiPersona? = null,
        systemInstructionOverride: String? = null,
        modelName: String = DEFAULT_MODEL,
        temperature: Float = 0.2f,
        topP: Float = 0.85f,
        maxOutputTokens: Int = 2048,
        enableChainOfThoughtValidation: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY is not configured in BuildConfig"))
        }

        try {
            val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"

            // Construct JSON payload
            val rootJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            contentObj.put("role", "user")

            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)

            rootJson.put("contents", contentsArray)

            // System Instruction enforcing strict on-topic precision and decisive straight-to-the-point answers
            val baseSystemPrompt = systemInstructionOverride ?: persona?.systemPrompt
            val cotDirective = if (enableChainOfThoughtValidation) {
                """
[CHAIN-OF-THOUGHT MANDATE & PRECISION VALIDATION]
Structure your response into two sequential phases:
1. Inside a `<think>...</think>` block, articulate step-by-step intermediate reasoning.
2. Immediately following `</think>`, output ONLY the verified, direct, high-precision final answer without preamble.
""".trimIndent()
            } else {
                "[MANDATORY DIRECTIVE: Be decisive, authoritative, and straight to the point. Reply strictly to what the user asks without preamble, conversational fluff, or template repetition.]"
            }

            val strictSystemPrompt = if (baseSystemPrompt.isNullOrBlank()) {
                "You are an on-device, high-precision, straight-to-the-point AI assistant.\n\n$cotDirective"
            } else {
                "$baseSystemPrompt\n\n$cotDirective"
            }

            val sysInstructionObj = JSONObject()
            val sysPartsArray = JSONArray()
            val sysPart = JSONObject()
            sysPart.put("text", strictSystemPrompt)
            sysPartsArray.put(sysPart)
            sysInstructionObj.put("parts", sysPartsArray)
            rootJson.put("systemInstruction", sysInstructionObj)

            // Precision generation config (low temperature for deterministic, on-point answers)
            val genConfig = JSONObject().apply {
                put("temperature", temperature.coerceIn(0.0f, 1.0f))
                put("topP", topP.coerceIn(0.1f, 1.0f))
                put("maxOutputTokens", maxOutputTokens)
            }
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    Log.w(TAG, "Gemini API error code: ${response.code}, body: $bodyString")
                    return@withContext Result.failure(Exception("Gemini API error ${response.code}: $bodyString"))
                }

                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val stringBuilder = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            val textPart = p.optString("text", "")
                            if (textPart.isNotBlank()) {
                                stringBuilder.append(textPart)
                            }
                        }
                        val text = stringBuilder.toString()
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                }

                Result.failure(Exception("No content returned in Gemini API response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed calling Gemini API", e)
            Result.failure(e)
        }
    }
}
