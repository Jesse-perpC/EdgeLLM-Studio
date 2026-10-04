package com.perpcorp.edgellm.cloudhub

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Single chat turn. Roles: "system" | "user" | "assistant". */
data class CloudMessage(val role: String, val content: String)

/**
 * Multi-provider cloud REST client ported from PrivateLM `cloud_service.dart`.
 *
 * Covers OpenAI-compatible (OpenAI/NVIDIA/OpenRouter/DeepSeek/Kimi/custom),
 * Anthropic Messages (split `system`), Gemini REST (`generateContent` +
 * `streamGenerateContent` SSE with `inline_data` vision), and Stability
 * multipart image generation (returns `[IMAGE_BASE64]<b64>`).
 *
 * Strictly opt-in: [isConfigured] is false until the user stores a key (and,
 * for `custom`, a base URL + model). All network work runs on Dispatchers.IO.
 * Uses OkHttp + org.json only — no new dependencies.
 */
class CloudHubService(
    private val store: CloudHubStore,
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        private const val TAG = "CloudHubService"
        const val IMAGE_PREFIX = "[IMAGE_BASE64]"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun currentProvider(): CloudProvider = store.provider()

    fun currentModel(provider: CloudProvider = currentProvider()): String =
        store.modelFor(provider)

    fun isConfigured(provider: CloudProvider = currentProvider()): Boolean {
        val key = store.apiKeyFor(provider.id)
        if (key.isBlank()) return false
        if (provider.needsCustomBaseUrl) {
            val base = store.getString(CloudHubKeys.CUSTOM_BASE_URL, "").orEmpty()
            val model = store.modelFor(provider)
            return base.isNotBlank() && model.isNotBlank()
        }
        return true
    }

    fun configurationError(provider: CloudProvider = currentProvider()): String =
        "ERROR: No API key configured for ${provider.label}. Open Settings → Cloud Hub."

    // ─── Public API ───────────────────────────────────────────────

    /**
     * Full (non-streaming) completion. Returns text, `[IMAGE_BASE64]...` for
     * Stability, or an `ERROR: ...` string — same contract as PrivateLM.
     */
    suspend fun sendMessage(
        messages: List<CloudMessage>,
        imageBase64: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
    ): String = withContext(Dispatchers.IO) {
        val provider = currentProvider()
        if (!isConfigured(provider)) return@withContext configurationError(provider)
        try {
            when (provider.kind) {
                CloudProviderKind.ANTHROPIC ->
                    postAnthropic(provider, messages, imageBase64, temperature, maxTokens)
                CloudProviderKind.GEMINI_REST ->
                    postGemini(provider, messages, imageBase64, temperature, maxTokens)
                CloudProviderKind.STABILITY ->
                    postStability(provider, messages)
                CloudProviderKind.OPENAI_COMPATIBLE ->
                    postOpenAiCompatible(
                        endpoint = provider.chatUrl(customBase()),
                        provider = provider,
                        messages = messages,
                        imageBase64 = imageBase64,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        streaming = false,
                        onToken = null,
                    )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud request failed (${provider.id})", e)
            "ERROR: Cloud API request failed — ${e.message}"
        }
    }

    /**
     * Streaming completion. Emits each token via [onToken] as it arrives and
     * returns the full text. Providers without SSE fall back to [sendMessage]
     * and deliver the whole reply as one chunk (mirrors PrivateLM).
     */
    suspend fun streamMessage(
        messages: List<CloudMessage>,
        imageBase64: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        onToken: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val provider = currentProvider()
        if (!isConfigured(provider)) return@withContext configurationError(provider)
        try {
            if (provider.supportsStreaming && provider.kind == CloudProviderKind.OPENAI_COMPATIBLE) {
                return@withContext postOpenAiCompatible(
                    endpoint = provider.chatUrl(customBase()),
                    provider = provider,
                    messages = messages,
                    imageBase64 = imageBase64,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    streaming = true,
                    onToken = onToken,
                )
            }
            if (provider.kind == CloudProviderKind.GEMINI_REST) {
                return@withContext streamGemini(
                    provider, messages, imageBase64, temperature, maxTokens, onToken,
                )
            }
            val full = sendMessage(messages, imageBase64, temperature, maxTokens)
            if (full.isNotEmpty() && !full.startsWith("ERROR")) onToken(full)
            full
        } catch (e: Exception) {
            Log.w(TAG, "Cloud stream failed (${provider.id})", e)
            "ERROR: Cloud API request failed — ${e.message}"
        }
    }

    // ─── OpenAI-compatible ────────────────────────────────────────

    private fun openAiMessages(
        messages: List<CloudMessage>,
        imageBase64: String?,
    ): JSONArray {
        val out = JSONArray()
        messages.forEachIndexed { index, msg ->
            if (msg.role == "user" && imageBase64 != null && index == messages.lastIndex) {
                out.put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", msg.content))
                                .put(
                                    JSONObject()
                                        .put("type", "image_url")
                                        .put(
                                            "image_url",
                                            JSONObject().put(
                                                "url",
                                                "data:image/jpeg;base64,$imageBase64",
                                            ),
                                        ),
                                ),
                        ),
                )
            } else {
                out.put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        return out
    }

    private fun postOpenAiCompatible(
        endpoint: String,
        provider: CloudProvider,
        messages: List<CloudMessage>,
        imageBase64: String?,
        temperature: Double?,
        maxTokens: Int?,
        streaming: Boolean,
        onToken: ((String) -> Unit)?,
    ): String {
        val body = JSONObject()
            .put("model", currentModel(provider))
            .put("messages", openAiMessages(messages, imageBase64))
            .put("temperature", temperature ?: store.temperature().toDouble())
            .put("max_tokens", maxTokens ?: store.maxTokens())
        if (streaming) body.put("stream", true)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${store.apiKeyFor(provider.id)}")
        if (streaming) reqBuilder.header("Accept", "text/event-stream")
        CloudProviders.extraHeaders(provider.id).forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                return "ERROR: ${provider.label} returned ${response.code} — $err"
            }
            if (!streaming || onToken == null) {
                val data = JSONObject(response.body?.string().orEmpty())
                return data.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content").orEmpty()
            }
            val sb = StringBuilder()
            val source = response.body?.source() ?: return ""
            while (!source.exhausted()) {
                val line = source.readUtf8Line()?.trim() ?: break
                if (line.isEmpty() || !line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                try {
                    val token = JSONObject(payload)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                    if (!token.isNullOrEmpty()) {
                        sb.append(token)
                        onToken(token)
                    }
                } catch (_: Exception) {
                    // Ignore malformed keep-alive chunks and keep reading.
                }
            }
            return sb.toString()
        }
    }

    // ─── Anthropic ────────────────────────────────────────────────

    private fun postAnthropic(
        provider: CloudProvider,
        messages: List<CloudMessage>,
        imageBase64: String?,
        temperature: Double?,
        maxTokens: Int?,
    ): String {
        var system: String? = null
        val apiMessages = JSONArray()
        messages.forEachIndexed { index, msg ->
            if (msg.role == "system" && system == null) {
                system = msg.content
            } else if (msg.role == "user" && imageBase64 != null && index == messages.lastIndex) {
                apiMessages.put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("type", "image")
                                        .put(
                                            "source",
                                            JSONObject()
                                                .put("type", "base64")
                                                .put("media_type", "image/jpeg")
                                                .put("data", imageBase64),
                                        ),
                                )
                                .put(JSONObject().put("type", "text").put("text", msg.content)),
                        ),
                )
            } else {
                apiMessages.put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        val body = JSONObject()
            .put("model", currentModel(provider))
            .put("messages", apiMessages)
            .put("max_tokens", maxTokens ?: store.maxTokens())
            .put("temperature", temperature ?: store.temperature().toDouble())
        if (system != null) body.put("system", system)

        val request = Request.Builder()
            .url(provider.chatUrl())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("x-api-key", store.apiKeyFor(provider.id))
            .header("anthropic-version", "2023-06-01")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return "ERROR: Anthropic returned ${response.code} — ${response.body?.string().orEmpty()}"
            }
            val content = JSONObject(response.body?.string().orEmpty()).optJSONArray("content")
            if (content == null || content.length() == 0) return ""
            return content.optJSONObject(0)?.optString("text").orEmpty()
        }
    }

    // ─── Gemini REST ──────────────────────────────────────────────

    private fun geminiParts(
        messages: List<CloudMessage>,
        imageBase64: String?,
    ): JSONArray {
        val parts = JSONArray()
        for (msg in messages) {
            parts.put(JSONObject().put("text", "${msg.role}: ${msg.content}"))
        }
        if (imageBase64 != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64),
                ),
            )
        }
        return parts
    }

    private fun geminiBody(
        messages: List<CloudMessage>,
        imageBase64: String?,
        temperature: Double?,
        maxTokens: Int?,
    ): JSONObject = JSONObject()
        .put(
            "contents",
            JSONArray().put(JSONObject().put("parts", geminiParts(messages, imageBase64))),
        )
        .put(
            "generationConfig",
            JSONObject()
                .put("temperature", temperature ?: store.temperature().toDouble())
                .put("maxOutputTokens", maxTokens ?: store.maxTokens()),
        )

    private fun geminiText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
    }

    private fun postGemini(
        provider: CloudProvider,
        messages: List<CloudMessage>,
        imageBase64: String?,
        temperature: Double?,
        maxTokens: Int?,
    ): String {
        val url = "${provider.generateUrl(currentModel(provider))}?key=${store.apiKeyFor(provider.id)}"
        val request = Request.Builder()
            .url(url)
            .post(
                geminiBody(messages, imageBase64, temperature, maxTokens)
                    .toString().toRequestBody("application/json".toMediaType()),
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return "ERROR: Google returned ${response.code} — ${response.body?.string().orEmpty()}"
            }
            return geminiText(JSONObject(response.body?.string().orEmpty()))
        }
    }

    private fun streamGemini(
        provider: CloudProvider,
        messages: List<CloudMessage>,
        imageBase64: String?,
        temperature: Double?,
        maxTokens: Int?,
        onToken: (String) -> Unit,
    ): String {
        val url =
            "${provider.streamUrl(currentModel(provider))}?key=${store.apiKeyFor(provider.id)}&alt=sse"
        val request = Request.Builder()
            .url(url)
            .post(
                geminiBody(messages, imageBase64, temperature, maxTokens)
                    .toString().toRequestBody("application/json".toMediaType()),
            )
            .header("Accept", "text/event-stream")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return "ERROR: Google returned ${response.code} — ${response.body?.string().orEmpty()}"
            }
            val sb = StringBuilder()
            val source = response.body?.source() ?: return ""
            while (!source.exhausted()) {
                val line = source.readUtf8Line()?.trim() ?: break
                if (line.isEmpty() || !line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                try {
                    val token = geminiText(JSONObject(payload))
                    if (token.isNotEmpty()) {
                        sb.append(token)
                        onToken(token)
                    }
                } catch (_: Exception) {
                    // Ignore malformed keep-alive chunks and keep reading.
                }
            }
            return sb.toString()
        }
    }

    // ─── Stability (image) ────────────────────────────────────────

    private fun postStability(
        provider: CloudProvider,
        messages: List<CloudMessage>,
    ): String {
        val prompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (prompt.isBlank()) return "ERROR: No user prompt found for image generation."
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("model", currentModel(provider))
            .addFormDataPart("output_format", "jpeg")
            .build()
        val request = Request.Builder()
            .url(provider.chatUrl())
            .post(body)
            .header("Authorization", "Bearer ${store.apiKeyFor(provider.id)}")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "ERROR: Stability AI returned ${response.code} — $raw"
            }
            val image = JSONObject(raw).optString("image", "")
            return if (image.isNotEmpty()) "$IMAGE_PREFIX$image" else "ERROR: No image generated."
        }
    }

    private fun customBase(): String =
        store.getString(CloudHubKeys.CUSTOM_BASE_URL, "").orEmpty()
}
