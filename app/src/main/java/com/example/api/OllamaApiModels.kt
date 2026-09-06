package com.example.api

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ApiServerConfig(
    val port: Int = 11434,
    val bindToLan: Boolean = true,
    val allowCors: Boolean = true,
    val enableOpenAiCompat: Boolean = true,
    val batteryProtectionThreshold: Int = 15,
    val maxConcurrentRequests: Int = 4,
    val defaultModelId: String? = null
)

data class ApiServerStats(
    val isRunning: Boolean = false,
    val port: Int = 11434,
    val localIp: String = "127.0.0.1",
    val lanIp: String = "127.0.0.1",
    val activeConnections: Int = 0,
    val totalRequestsServed: Int = 0,
    val totalTokensGenerated: Long = 0L,
    val averageLatencyMs: Long = 0L,
    val uptimeSeconds: Long = 0L,
    val startedAt: Long = 0L,
    val lastRequestTimestamp: Long = 0L,
    val isBatteryLow: Boolean = false,
    val thermalThrottled: Boolean = false
) {
    val localUrl: String get() = "http://127.0.0.1:$port"
    val lanUrl: String get() = "http://$lanIp:$port"
}

data class ApiRequestLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val clientIp: String,
    val statusCode: Int,
    val durationMs: Long,
    val tokensGenerated: Int,
    val model: String,
    val promptPreview: String = "",
    val errorMessage: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

object OllamaJsonHelper {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun nowIso(): String = isoFormat.format(Date())

    fun createVersionResponse(): String {
        return JSONObject().apply {
            put("version", "0.4.0-edge-android")
        }.toString()
    }

    fun createHealthResponse(stats: ApiServerStats, activeModelName: String?): String {
        return JSONObject().apply {
            put("status", "ok")
            put("service", "EdgeLLM Ollama Inference Bridge")
            put("version", "1.0.0")
            put("active_model", activeModelName ?: "none")
            put("total_requests", stats.totalRequestsServed)
            put("total_tokens", stats.totalTokensGenerated)
            put("uptime_sec", stats.uptimeSeconds)
        }.toString()
    }

    fun createTagsResponse(models: List<com.example.data.model.ModelSpec>): String {
        val root = JSONObject()
        val modelsArray = JSONArray()

        models.filter { it.isDownloaded || it.isImported }.forEach { model ->
            val item = JSONObject().apply {
                put("name", "${model.name.lowercase().replace(" ", "-").replace(":", "-")}:${model.parameterCount.lowercase()}")
                put("model", model.id)
                put("modified_at", nowIso())
                put("size", model.fileSizeBytes)
                put("digest", "sha256:${model.sha256Checksum.ifEmpty { "00000000000000000000000000000000" }}")

                val details = JSONObject().apply {
                    put("format", model.format.name.lowercase())
                    put("family", model.name.split(" ").firstOrNull()?.lowercase() ?: "llama")
                    put("parameter_size", model.parameterCount)
                    put("quantization_level", model.quantization)
                }
                put("details", details)
            }
            modelsArray.put(item)
        }

        root.put("models", modelsArray)
        return root.toString()
    }

    fun createOpenAiModelsResponse(models: List<com.example.data.model.ModelSpec>): String {
        val root = JSONObject()
        root.put("object", "list")
        val dataArray = JSONArray()

        models.filter { it.isDownloaded || it.isImported }.forEach { model ->
            val item = JSONObject().apply {
                put("id", model.id)
                put("object", "model")
                put("created", System.currentTimeMillis() / 1000)
                put("owned_by", "edgellm-on-device")
            }
            dataArray.put(item)
        }

        root.put("data", dataArray)
        return root.toString()
    }

    fun createPsResponse(models: List<com.example.data.model.ModelSpec>, activeModelId: String?): String {
        val root = JSONObject()
        val modelsArray = JSONArray()

        models.firstOrNull { it.id == activeModelId }?.let { active ->
            val item = JSONObject().apply {
                put("name", active.name)
                put("model", active.id)
                put("size", active.fileSizeBytes)
                put("digest", "sha256:${active.sha256Checksum}")
                put("expires_at", nowIso())
                put("size_vram", active.requiredRamBytes)
            }
            modelsArray.put(item)
        }

        root.put("models", modelsArray)
        return root.toString()
    }
}
