package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.model.ComputeBackend
import com.example.data.model.GenerationParameters
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelSpec
import com.example.data.model.PowerProfile
import com.example.engine.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OllamaInferenceServer(
    private val context: Context,
    private val inferenceEngine: LocalInferenceEngine,
    private val modelProvider: () -> List<ModelSpec>,
    private val activeModelProvider: () -> ModelSpec?,
    private val accelerationSettingsProvider: () -> HardwareAccelerationSettings
) {
    companion object {
        private const val TAG = "OllamaInferenceServer"
        const val DEFAULT_PORT = 11434
    }

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(4)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serverStats = MutableStateFlow(ApiServerStats())
    val serverStats: StateFlow<ApiServerStats> = _serverStats.asStateFlow()

    private val _requestLogs = MutableStateFlow<List<ApiRequestLog>>(emptyList())
    val requestLogs: StateFlow<List<ApiRequestLog>> = _requestLogs.asStateFlow()

    private val activeConnectionCounter = AtomicInteger(0)
    private val totalRequestsCounter = AtomicInteger(0)
    private val totalTokensCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private var startTimeMillis = 0L

    var config = ApiServerConfig()
        private set

    fun updateConfig(newConfig: ApiServerConfig) {
        config = newConfig
    }

    fun start(port: Int = config.port, bindToLan: Boolean = config.bindToLan): Boolean {
        if (isRunning.get()) {
            Log.w(TAG, "Server already running on port ${_serverStats.value.port}")
            return true
        }

        try {
            val bindAddress = if (bindToLan) {
                InetAddress.getByName("0.0.0.0")
            } else {
                InetAddress.getByName("127.0.0.1")
            }

            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(java.net.InetSocketAddress(bindAddress, port), 50)
            serverSocket = socket
            isRunning.set(true)
            startTimeMillis = System.currentTimeMillis()

            val lanIp = getLanIpAddress()
            _serverStats.update {
                it.copy(
                    isRunning = true,
                    port = port,
                    localIp = "127.0.0.1",
                    lanIp = lanIp,
                    startedAt = startTimeMillis
                )
            }

            startAcceptLoop()
            startUptimeTicker()
            Log.i(TAG, "Ollama Inference Server started on $lanIp:$port (LAN: $bindToLan)")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start Ollama server on port $port: ${e.message}", e)
            isRunning.set(false)
            _serverStats.update { it.copy(isRunning = false) }
            return false
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        } finally {
            serverSocket = null
            _serverStats.update {
                it.copy(
                    isRunning = false,
                    activeConnections = 0
                )
            }
            Log.i(TAG, "Ollama Inference Server stopped")
        }
    }

    private fun startAcceptLoop() {
        executor.execute {
            while (isRunning.get() && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val clientSocket = serverSocket!!.accept()
                    activeConnectionCounter.incrementAndGet()
                    _serverStats.update { it.copy(activeConnections = activeConnectionCounter.get()) }

                    executor.execute {
                        try {
                            handleClientSocket(clientSocket)
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.e(TAG, "Error handling client: ${e.message}")
                            }
                        } finally {
                            activeConnectionCounter.decrementAndGet()
                            _serverStats.update { it.copy(activeConnections = activeConnectionCounter.get()) }
                            try {
                                clientSocket.close()
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: SocketException) {
                    if (!isRunning.get()) break
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        val startReqTime = System.currentTimeMillis()
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        val out = socket.getOutputStream()

        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0].uppercase()
        val pathWithQuery = parts[1]
        val path = pathWithQuery.split("?")[0]
        val clientIp = socket.inetAddress.hostAddress ?: "unknown"

        // Read headers
        val headers = mutableMapOf<String, String>()
        var line: String? = reader.readLine()
        var contentLength = 0
        while (!line.isNullOrEmpty()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
                if (key == "content-length") {
                    contentLength = value.toIntOrNull() ?: 0
                }
            }
            line = reader.readLine()
        }

        // Read body if any
        val bodyBuilder = StringBuilder()
        if (contentLength > 0) {
            val buffer = CharArray(1024)
            var bytesRead = 0
            while (bytesRead < contentLength) {
                val toRead = (contentLength - bytesRead).coerceAtMost(buffer.size)
                val read = reader.read(buffer, 0, toRead)
                if (read == -1) break
                bodyBuilder.append(buffer, 0, read)
                bytesRead += read
            }
        }
        val requestBody = bodyBuilder.toString()

        var statusCode = 200
        var tokensGenerated = 0
        var targetModel = ""

        try {
            when {
                // CORS preflight
                method == "OPTIONS" -> {
                    sendCorsPreflight(out)
                }

                // Health / Root info
                method == "GET" && (path == "/" || path == "/health") -> {
                    val response = OllamaJsonHelper.createHealthResponse(
                        _serverStats.value,
                        activeModelProvider()?.name
                    )
                    sendJsonResponse(out, 200, response)
                }

                // Ollama version
                method == "GET" && path == "/api/version" -> {
                    val response = OllamaJsonHelper.createVersionResponse()
                    sendJsonResponse(out, 200, response)
                }

                // Ollama model tags list (e.g. `ollama list`)
                method == "GET" && path == "/api/tags" -> {
                    val models = modelProvider()
                    val response = OllamaJsonHelper.createTagsResponse(models)
                    sendJsonResponse(out, 200, response)
                }

                // Ollama process list (e.g. `ollama ps`)
                method == "GET" && path == "/api/ps" -> {
                    val models = modelProvider()
                    val activeId = activeModelProvider()?.id
                    val response = OllamaJsonHelper.createPsResponse(models, activeId)
                    sendJsonResponse(out, 200, response)
                }

                // OpenAI compatible: GET /v1/models
                method == "GET" && path == "/v1/models" -> {
                    val models = modelProvider()
                    val response = OllamaJsonHelper.createOpenAiModelsResponse(models)
                    sendJsonResponse(out, 200, response)
                }

                // Ollama generate endpoint: POST /api/generate
                method == "POST" && path == "/api/generate" -> {
                    val (toks, model) = handleOllamaGenerate(requestBody, out)
                    tokensGenerated = toks
                    targetModel = model
                }

                // Ollama chat endpoint: POST /api/chat
                method == "POST" && path == "/api/chat" -> {
                    val (toks, model) = handleOllamaChat(requestBody, out)
                    tokensGenerated = toks
                    targetModel = model
                }

                // OpenAI compatible: POST /v1/chat/completions
                method == "POST" && path == "/v1/chat/completions" -> {
                    val (toks, model) = handleOpenAiChatCompletion(requestBody, out)
                    tokensGenerated = toks
                    targetModel = model
                }

                else -> {
                    statusCode = 404
                    val errJson = JSONObject().apply {
                        put("error", "Endpoint not found: $method $path")
                    }.toString()
                    sendJsonResponse(out, 404, errJson)
                }
            }
        } catch (e: Exception) {
            statusCode = 500
            Log.e(TAG, "Request execution error on $method $path: ${e.message}", e)
            try {
                val errJson = JSONObject().apply {
                    put("error", e.message ?: "Internal server error")
                }.toString()
                sendJsonResponse(out, 500, errJson)
            } catch (_: Exception) {}
        } finally {
            val duration = System.currentTimeMillis() - startReqTime
            if (method != "OPTIONS") {
                recordRequestLog(
                    method = method,
                    path = path,
                    clientIp = clientIp,
                    statusCode = statusCode,
                    durationMs = duration,
                    tokens = tokensGenerated,
                    model = targetModel.ifEmpty { activeModelProvider()?.name ?: "auto" },
                    promptPreview = extractPromptPreview(requestBody)
                )
            }
        }
    }

    private fun handleOllamaGenerate(bodyStr: String, out: OutputStream): Pair<Int, String> {
        val json = if (bodyStr.isNotEmpty()) JSONObject(bodyStr) else JSONObject()
        val modelName = json.optString("model", activeModelProvider()?.name ?: "llama3.2:1b")
        val prompt = json.optString("prompt", "")
        val stream = json.optBoolean("stream", true)
        val systemPrompt = json.optString("system", "")

        val fullPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$prompt" else prompt
        val activeModel = resolveModel(modelName)

        val settings = accelerationSettingsProvider()
        val params = GenerationParameters(
            temperature = json.optJSONObject("options")?.optDouble("temperature", 0.7)?.toFloat() ?: 0.7f
        )

        var totalTokens = 0
        val startTime = System.currentTimeMillis()

        if (stream) {
            // Write HTTP headers for chunked NDJSON
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-ndjson; charset=utf-8\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    corsHeaderString() +
                    "\r\n"
            out.write(header.toByteArray(StandardCharsets.UTF_8))
            out.flush()

            kotlinx.coroutines.runBlocking {
                inferenceEngine.generateStreamingResponse(
                    prompt = fullPrompt,
                    model = activeModel,
                    settings = settings,
                    params = params
                ).collect { chunk ->
                    totalTokens = chunk.tokenCount
                    val chunkJson = JSONObject().apply {
                        put("model", modelName)
                        put("created_at", OllamaJsonHelper.nowIso())
                        put("response", chunk.token)
                        put("done", false)
                    }.toString() + "\n"

                    writeHttpChunk(out, chunkJson)
                }

                val evalDurationNs = (System.currentTimeMillis() - startTime) * 1_000_000L
                val finalJson = JSONObject().apply {
                    put("model", modelName)
                    put("created_at", OllamaJsonHelper.nowIso())
                    put("response", "")
                    put("done", true)
                    put("total_duration", evalDurationNs)
                    put("load_duration", 25_000_000L)
                    put("prompt_eval_count", fullPrompt.split(" ").size)
                    put("eval_count", totalTokens)
                    put("eval_duration", evalDurationNs)
                }.toString() + "\n"

                writeHttpChunk(out, finalJson)
                // Final terminating chunk
                out.write("0\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
        } else {
            val responseText = inferenceEngine.generateOfflineIntelligence(
                prompt = fullPrompt,
                model = activeModel,
                params = params
            )
            val tokens = inferenceEngine.tokenizeResponse(responseText)
            totalTokens = tokens.size

            val evalDurationNs = (System.currentTimeMillis() - startTime) * 1_000_000L
            val responseJson = JSONObject().apply {
                put("model", modelName)
                put("created_at", OllamaJsonHelper.nowIso())
                put("response", responseText)
                put("done", true)
                put("total_duration", evalDurationNs)
                put("prompt_eval_count", fullPrompt.split(" ").size)
                put("eval_count", totalTokens)
            }.toString()

            sendJsonResponse(out, 200, responseJson)
        }

        return Pair(totalTokens, activeModel.name)
    }

    private fun handleOllamaChat(bodyStr: String, out: OutputStream): Pair<Int, String> {
        val json = if (bodyStr.isNotEmpty()) JSONObject(bodyStr) else JSONObject()
        val modelName = json.optString("model", activeModelProvider()?.name ?: "llama3.2:1b")
        val stream = json.optBoolean("stream", true)
        val messagesArray = json.optJSONArray("messages") ?: JSONArray()

        val promptBuilder = StringBuilder()
        for (i in 0 until messagesArray.length()) {
            val msg = messagesArray.optJSONObject(i) ?: continue
            val role = msg.optString("role", "user")
            val content = msg.optString("content", "")
            promptBuilder.append("$role: $content\n")
        }
        promptBuilder.append("assistant: ")

        val fullPrompt = promptBuilder.toString()
        val activeModel = resolveModel(modelName)
        val settings = accelerationSettingsProvider()
        val params = GenerationParameters()

        var totalTokens = 0
        val startTime = System.currentTimeMillis()

        if (stream) {
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-ndjson; charset=utf-8\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    corsHeaderString() +
                    "\r\n"
            out.write(header.toByteArray(StandardCharsets.UTF_8))
            out.flush()

            kotlinx.coroutines.runBlocking {
                inferenceEngine.generateStreamingResponse(
                    prompt = fullPrompt,
                    model = activeModel,
                    settings = settings,
                    params = params
                ).collect { chunk ->
                    totalTokens = chunk.tokenCount
                    val chunkJson = JSONObject().apply {
                        put("model", modelName)
                        put("created_at", OllamaJsonHelper.nowIso())
                        val msgObj = JSONObject().apply {
                            put("role", "assistant")
                            put("content", chunk.token)
                        }
                        put("message", msgObj)
                        put("done", false)
                    }.toString() + "\n"

                    writeHttpChunk(out, chunkJson)
                }

                val evalDurationNs = (System.currentTimeMillis() - startTime) * 1_000_000L
                val finalJson = JSONObject().apply {
                    put("model", modelName)
                    put("created_at", OllamaJsonHelper.nowIso())
                    val emptyMsg = JSONObject().apply {
                        put("role", "assistant")
                        put("content", "")
                    }
                    put("message", emptyMsg)
                    put("done", true)
                    put("total_duration", evalDurationNs)
                    put("eval_count", totalTokens)
                }.toString() + "\n"

                writeHttpChunk(out, finalJson)
                out.write("0\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
        } else {
            val responseText = inferenceEngine.generateOfflineIntelligence(
                prompt = fullPrompt,
                model = activeModel,
                params = params
            )
            val tokens = inferenceEngine.tokenizeResponse(responseText)
            totalTokens = tokens.size

            val evalDurationNs = (System.currentTimeMillis() - startTime) * 1_000_000L
            val responseJson = JSONObject().apply {
                put("model", modelName)
                put("created_at", OllamaJsonHelper.nowIso())
                val msgObj = JSONObject().apply {
                    put("role", "assistant")
                    put("content", responseText)
                }
                put("message", msgObj)
                put("done", true)
                put("total_duration", evalDurationNs)
                put("eval_count", totalTokens)
            }.toString()

            sendJsonResponse(out, 200, responseJson)
        }

        return Pair(totalTokens, activeModel.name)
    }

    private fun handleOpenAiChatCompletion(bodyStr: String, out: OutputStream): Pair<Int, String> {
        val json = if (bodyStr.isNotEmpty()) JSONObject(bodyStr) else JSONObject()
        val modelName = json.optString("model", activeModelProvider()?.name ?: "llama3.2:1b")
        val stream = json.optBoolean("stream", false)
        val messagesArray = json.optJSONArray("messages") ?: JSONArray()

        val promptBuilder = StringBuilder()
        for (i in 0 until messagesArray.length()) {
            val msg = messagesArray.optJSONObject(i) ?: continue
            val role = msg.optString("role", "user")
            val content = msg.optString("content", "")
            promptBuilder.append("$role: $content\n")
        }
        promptBuilder.append("assistant: ")

        val fullPrompt = promptBuilder.toString()
        val activeModel = resolveModel(modelName)
        val settings = accelerationSettingsProvider()
        val params = GenerationParameters()

        val reqId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 12)
        val createdTimestamp = System.currentTimeMillis() / 1000
        var totalTokens = 0

        if (stream) {
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    corsHeaderString() +
                    "\r\n"
            out.write(header.toByteArray(StandardCharsets.UTF_8))
            out.flush()

            kotlinx.coroutines.runBlocking {
                inferenceEngine.generateStreamingResponse(
                    prompt = fullPrompt,
                    model = activeModel,
                    settings = settings,
                    params = params
                ).collect { chunk ->
                    totalTokens = chunk.tokenCount
                    val deltaJson = JSONObject().apply {
                        put("id", reqId)
                        put("object", "chat.completion.chunk")
                        put("created", createdTimestamp)
                        put("model", modelName)
                        val choice = JSONObject().apply {
                            put("index", 0)
                            val delta = JSONObject().apply {
                                put("content", chunk.token)
                            }
                            put("delta", delta)
                            put("finish_reason", JSONObject.NULL)
                        }
                        put("choices", JSONArray().put(choice))
                    }.toString()

                    val sseEvent = "data: $deltaJson\n\n"
                    writeHttpChunk(out, sseEvent)
                }

                val finalSse = "data: [DONE]\n\n"
                writeHttpChunk(out, finalSse)
                out.write("0\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
        } else {
            val responseText = inferenceEngine.generateOfflineIntelligence(
                prompt = fullPrompt,
                model = activeModel,
                params = params
            )
            val tokens = inferenceEngine.tokenizeResponse(responseText)
            totalTokens = tokens.size

            val responseJson = JSONObject().apply {
                put("id", reqId)
                put("object", "chat.completion")
                put("created", createdTimestamp)
                put("model", modelName)
                val choice = JSONObject().apply {
                    put("index", 0)
                    val message = JSONObject().apply {
                        put("role", "assistant")
                        put("content", responseText)
                    }
                    put("message", message)
                    put("finish_reason", "stop")
                }
                put("choices", JSONArray().put(choice))
                val usage = JSONObject().apply {
                    put("prompt_tokens", fullPrompt.split(" ").size)
                    put("completion_tokens", totalTokens)
                    put("total_tokens", fullPrompt.split(" ").size + totalTokens)
                }
                put("usage", usage)
            }.toString()

            sendJsonResponse(out, 200, responseJson)
        }

        return Pair(totalTokens, activeModel.name)
    }

    private fun resolveModel(requestedModelName: String): ModelSpec {
        val downloaded = modelProvider().filter { it.isDownloaded || it.isImported }
        return downloaded.firstOrNull {
            it.id.equals(requestedModelName, ignoreCase = true) ||
                    it.name.contains(requestedModelName, ignoreCase = true) ||
                    requestedModelName.contains(it.id, ignoreCase = true)
        } ?: activeModelProvider() ?: downloaded.firstOrNull() ?: ModelSpec(
            id = "llama-3-2-1b",
            name = "Llama 3.2 1B Instruct",
            parameterCount = "1.2B",
            format = com.example.data.model.ModelFormat.GGUF,
            quantization = "Q4_K_M",
            fileSizeBytes = 850_000_000L,
            requiredRamBytes = 1_200_000_000L,
            contextLength = 4096,
            description = "Default fallback model",
            category = com.example.data.model.ModelCategory.CHAT_REASONING,
            downloadUrl = "",
            sha256Checksum = "0000000000000000000000000000000000000000000000000000000000000000",
            isDownloaded = true
        )
    }

    private fun writeHttpChunk(out: OutputStream, chunkData: String) {
        val bytes = chunkData.toByteArray(StandardCharsets.UTF_8)
        val hexLen = Integer.toHexString(bytes.size)
        out.write("$hexLen\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun sendJsonResponse(out: OutputStream, statusCode: Int, jsonPayload: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Response"
        }
        val bytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                corsHeaderString() +
                "\r\n"

        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendCorsPreflight(out: OutputStream) {
        val headers = "HTTP/1.1 204 No Content\r\n" +
                "Connection: close\r\n" +
                corsHeaderString() +
                "\r\n"
        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun corsHeaderString(): String {
        return if (config.allowCors) {
            "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, Authorization, Accept\r\n"
        } else ""
    }

    private fun recordRequestLog(
        method: String,
        path: String,
        clientIp: String,
        statusCode: Int,
        durationMs: Long,
        tokens: Int,
        model: String,
        promptPreview: String
    ) {
        totalRequestsCounter.incrementAndGet()
        totalTokensCounter.addAndGet(tokens.toLong())

        val logEntry = ApiRequestLog(
            id = UUID.randomUUID().toString(),
            method = method,
            path = path,
            clientIp = clientIp,
            statusCode = statusCode,
            durationMs = durationMs,
            tokensGenerated = tokens,
            model = model,
            promptPreview = promptPreview
        )

        _requestLogs.update { current ->
            (listOf(logEntry) + current).take(50) // Keep last 50 logs in circular buffer
        }

        _serverStats.update {
            val totalReq = totalRequestsCounter.get()
            val totalTok = totalTokensCounter.get()
            val avgLatency = if (totalReq > 0) ((it.averageLatencyMs * (totalReq - 1)) + durationMs) / totalReq else durationMs
            it.copy(
                totalRequestsServed = totalReq,
                totalTokensGenerated = totalTok,
                averageLatencyMs = avgLatency,
                lastRequestTimestamp = System.currentTimeMillis()
            )
        }
    }

    private fun startUptimeTicker() {
        scope.launch {
            while (isRunning.get()) {
                kotlinx.coroutines.delay(1000)
                if (isRunning.get() && startTimeMillis > 0) {
                    val uptime = (System.currentTimeMillis() - startTimeMillis) / 1000
                    _serverStats.update { it.copy(uptimeSeconds = uptime) }
                }
            }
        }
    }

    private fun extractPromptPreview(body: String): String {
        return try {
            val json = JSONObject(body)
            when {
                json.has("prompt") -> json.getString("prompt").take(60)
                json.has("messages") -> {
                    val msgs = json.getJSONArray("messages")
                    val last = msgs.getJSONObject(msgs.length() - 1)
                    last.optString("content", "").take(60)
                }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getLanIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve LAN IP: ${e.message}")
        }
        return "127.0.0.1"
    }
}
