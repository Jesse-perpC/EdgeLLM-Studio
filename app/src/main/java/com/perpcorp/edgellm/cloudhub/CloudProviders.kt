package com.perpcorp.edgellm.cloudhub

/**
 * Third-party cloud provider table ported from PrivateLM
 * (`cloud_service.dart` + `constants.dart` endpoints).
 *
 * EdgeLLM already has Gemini-via-Firebase and full on-device engines; this
 * table adds the REST cloud-fallback matrix and nothing else. All calls are
 * strictly opt-in: without a user-supplied key the app stays 100% on-device.
 */
enum class CloudProviderKind {
    /** POST {base}/chat/completions, Bearer auth, SSE optional. */
    OPENAI_COMPATIBLE,
    /** POST /v1/messages, x-api-key + anthropic-version, split system field. */
    ANTHROPIC,
    /** POST {base}/{model}:generateContent(:streamGenerateContent), ?key=. */
    GEMINI_REST,
    /** Multipart image generation returning base64. */
    STABILITY,
}

data class CloudProvider(
    val id: String,
    val label: String,
    val kind: CloudProviderKind,
    /** Base URL; full chat URL derived per kind. Ignored when [needsBaseUrl] uses stored URL. */
    val baseUrl: String,
    val defaultModel: String,
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    /** True for the user-defined OpenAI-compatible endpoint. */
    val needsCustomBaseUrl: Boolean = false,
) {
    /** Mirrors PrivateLM's per-provider endpoint resolution. */
    fun chatUrl(customBaseUrl: String = ""): String {
        val base = if (needsCustomBaseUrl) customBaseUrl.trimEnd('/') else baseUrl
        return when (kind) {
            CloudProviderKind.OPENAI_COMPATIBLE -> "$base/chat/completions"
            CloudProviderKind.ANTHROPIC -> "$base/v1/messages"
            // Gemini REST appends :generateContent per call (needs ?key=).
            CloudProviderKind.GEMINI_REST -> base
            CloudProviderKind.STABILITY -> "$base/v2beta/stable-image/generate/sd3"
        }
    }

    fun streamUrl(model: String, customBaseUrl: String = ""): String {
        require(kind == CloudProviderKind.GEMINI_REST)
        val base = if (needsCustomBaseUrl) customBaseUrl.trimEnd('/') else baseUrl
        return "$base/$model:streamGenerateContent"
    }

    fun generateUrl(model: String, customBaseUrl: String = ""): String {
        require(kind == CloudProviderKind.GEMINI_REST)
        val base = if (needsCustomBaseUrl) customBaseUrl.trimEnd('/') else baseUrl
        return "$base/$model:generateContent"
    }
}

object CloudProviders {
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val GOOGLE = "google"
    const val KIMI = "kimi"
    const val STABILITY = "stability"
    const val NVIDIA = "nvidia"
    const val OPENROUTER = "openrouter"
    const val DEEPSEEK = "deepseek"
    const val CUSTOM = "custom"

    val ALL: List<CloudProvider> = listOf(
        CloudProvider(
            id = OPENAI, label = "OpenAI", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1", defaultModel = "gpt-5.2",
            supportsStreaming = true, supportsVision = true,
        ),
        CloudProvider(
            id = ANTHROPIC, label = "Anthropic", kind = CloudProviderKind.ANTHROPIC,
            baseUrl = "https://api.anthropic.com", defaultModel = "claude-sonnet-4-6",
            supportsStreaming = false, supportsVision = true,
        ),
        CloudProvider(
            id = GOOGLE, label = "Google Gemini (REST)", kind = CloudProviderKind.GEMINI_REST,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
            defaultModel = "gemini-2.5-flash",
            supportsStreaming = false, supportsVision = true,
        ),
        CloudProvider(
            id = KIMI, label = "Kimi (Moonshot)", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://api.moonshot.ai/v1", defaultModel = "kimi-k2.6",
            supportsStreaming = true, supportsVision = false,
        ),
        CloudProvider(
            id = STABILITY, label = "Stability (image)", kind = CloudProviderKind.STABILITY,
            baseUrl = "https://api.stability.ai", defaultModel = "sd3.5-flash",
            supportsStreaming = false, supportsVision = false,
        ),
        CloudProvider(
            id = NVIDIA, label = "NVIDIA NIM", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://integrate.api.nvidia.com/v1",
            defaultModel = "meta/llama-3.1-8b-instruct",
            supportsStreaming = true, supportsVision = true,
        ),
        CloudProvider(
            id = OPENROUTER, label = "OpenRouter", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1", defaultModel = "openai/gpt-4o-mini",
            supportsStreaming = true, supportsVision = true,
        ),
        CloudProvider(
            id = DEEPSEEK, label = "DeepSeek", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepseek.com", defaultModel = "deepseek-v4-flash",
            supportsStreaming = true, supportsVision = false,
        ),
        CloudProvider(
            id = CUSTOM, label = "Custom (OpenAI-compatible)", kind = CloudProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "", defaultModel = "",
            supportsStreaming = true, supportsVision = true,
            needsCustomBaseUrl = true,
        ),
    )

    fun byId(id: String): CloudProvider =
        ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == OPENROUTER }

    /** Extra headers some providers require (mirrors PrivateLM). */
    fun extraHeaders(id: String): Map<String, String> =
        if (id == OPENROUTER) {
            mapOf("HTTP-Referer" to "https://ai-chat.local", "X-Title" to "AI Chat")
        } else {
            emptyMap()
        }
}
