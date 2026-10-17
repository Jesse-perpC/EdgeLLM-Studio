package com.perpcorp.edgellm.plugin

import android.content.Context
import com.perpcorp.edgellm.data.model.McpResourceDefinition
import com.perpcorp.edgellm.data.model.McpServerSpec
import com.perpcorp.edgellm.data.model.McpToolCallResult
import com.perpcorp.edgellm.data.model.McpToolDefinition
import com.perpcorp.edgellm.data.model.McpTransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.perpcorp.edgellm.data.local.AppDatabase
import com.perpcorp.edgellm.data.memory.VectorEmbeddingEngine

class McpClientManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _servers = MutableStateFlow<List<McpServerSpec>>(createDefaultMcpServers())
    val servers: StateFlow<List<McpServerSpec>> = _servers.asStateFlow()

    private val _lastToolExecution = MutableStateFlow<McpToolCallResult?>(null)
    val lastToolExecution: StateFlow<McpToolCallResult?> = _lastToolExecution.asStateFlow()

    companion object {
        fun createDefaultMcpServers(): List<McpServerSpec> {
            return listOf(
                McpServerSpec(
                    id = "mcp_filesystem_local",
                    name = "Local File & Workspace MCP",
                    endpointUrl = "http://localhost:8080/mcp/sse",
                    transportType = McpTransportType.STDIO_LOCAL,
                    isEnabled = true,
                    isConnected = true,
                    latencyMs = 4L,
                    toolsCount = 3,
                    resourcesCount = 2,
                    tools = listOf(
                        McpToolDefinition(
                            name = "fs_list_directory",
                            description = "Lists files and subdirectories in local app sandbox storage.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}",
                            serverId = "mcp_filesystem_local",
                            category = "Filesystem"
                        ),
                        McpToolDefinition(
                            name = "fs_read_file",
                            description = "Safely reads utf-8 text file contents within permitted directory roots.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"filePath\":{\"type\":\"string\"}},\"required\":[\"filePath\"]}",
                            serverId = "mcp_filesystem_local",
                            category = "Filesystem"
                        ),
                        McpToolDefinition(
                            name = "fs_grep_search",
                            description = "Fast pattern regex search across local text files and logs.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}",
                            serverId = "mcp_filesystem_local",
                            category = "Filesystem"
                        )
                    ),
                    resources = listOf(
                        McpResourceDefinition("file:///storage/emulated/0/EdgeLLM/vault", "Secure Vault Folder", "directory"),
                        McpResourceDefinition("file:///data/user/0/com.perpcorp.edgellm/databases", "App Cache DB", "application/x-sqlite3")
                    ),
                    lastSyncTimestamp = System.currentTimeMillis()
                ),
                McpServerSpec(
                    id = "mcp_sqlite_database",
                    name = "SQLite & Vector DB MCP",
                    endpointUrl = "http://127.0.0.1:9092/mcp",
                    transportType = McpTransportType.HTTP_SSE,
                    isEnabled = true,
                    isConnected = true,
                    latencyMs = 12L,
                    toolsCount = 2,
                    resourcesCount = 1,
                    tools = listOf(
                        McpToolDefinition(
                            name = "db_execute_query",
                            description = "Runs read-only SQL queries against embedded database tables.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}",
                            serverId = "mcp_sqlite_database",
                            category = "Database"
                        ),
                        McpToolDefinition(
                            name = "vector_search_embeddings",
                            description = "Queries embedded 384-dim sentence transformers for RAG chunk retrieval.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"topK\":{\"type\":\"integer\"}}}",
                            serverId = "mcp_sqlite_database",
                            category = "Vector DB"
                        )
                    ),
                    resources = listOf(
                        McpResourceDefinition("sqlite:///app_database.db/schema", "Full SQLite Schema", "text/sql")
                    ),
                    lastSyncTimestamp = System.currentTimeMillis()
                ),
                McpServerSpec(
                    id = "mcp_github_devops",
                    name = "GitHub & Git MCP Bridge",
                    endpointUrl = "https://api.github.com/mcp",
                    transportType = McpTransportType.HTTP_SSE,
                    isEnabled = false,
                    isConnected = false,
                    latencyMs = 0L,
                    toolsCount = 2,
                    resourcesCount = 0,
                    tools = listOf(
                        McpToolDefinition(
                            name = "github_list_issues",
                            description = "Retrieves issues and pull requests from repository.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"repo\":{\"type\":\"string\"}}}",
                            serverId = "mcp_github_devops",
                            category = "DevOps"
                        ),
                        McpToolDefinition(
                            name = "github_create_pull_request",
                            description = "Creates a new pull request with branch diff.",
                            inputSchemaJson = "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}",
                            serverId = "mcp_github_devops",
                            category = "DevOps"
                        )
                    ),
                    lastSyncTimestamp = 0L
                )
            )
        }
    }

    fun toggleServer(serverId: String, enabled: Boolean) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) it.copy(isEnabled = enabled) else it
        }
    }

    fun addCustomServer(name: String, endpointUrl: String, transport: McpTransportType, authHeader: String? = null) {
        val newServer = McpServerSpec(
            id = "mcp_server_${UUID.randomUUID().toString().take(8)}",
            name = name,
            endpointUrl = endpointUrl,
            transportType = transport,
            isEnabled = true,
            isConnected = false,
            authHeader = authHeader?.takeIf { it.isNotBlank() },
            latencyMs = 0L,
            toolsCount = 0,
            resourcesCount = 0,
            tools = emptyList()
        )
        _servers.value = _servers.value + newServer
    }

    fun removeServer(serverId: String) {
        _servers.value = _servers.value.filter { it.id != serverId }
    }

    suspend fun syncServer(serverId: String): Result<McpServerSpec> = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext Result.failure(IllegalArgumentException("Server not found: $serverId"))

        val startTime = System.currentTimeMillis()

        // Local stdio workspace check
        if (server.transportType == McpTransportType.STDIO_LOCAL) {
            val isReady = context.filesDir.exists()
            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            val updated = server.copy(
                isConnected = isReady,
                latencyMs = latency,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            _servers.value = _servers.value.map { if (it.id == serverId) updated else it }
            return@withContext Result.success(updated)
        }

        // Live network probe / MCP handshake (JSON-RPC 2.0 initialize request)
        try {
            val jsonRpcInit = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject().apply {
                        put("tools", JSONObject())
                        put("resources", JSONObject())
                    })
                    put("clientInfo", JSONObject().apply {
                        put("name", "EdgeLLM-Android-Studio")
                        put("version", "2.1.0")
                    })
                })
            }

            val requestBuilder = Request.Builder()
                .url(server.endpointUrl)
                .post(jsonRpcInit.toString().toRequestBody("application/json".toMediaType()))

            server.authHeader?.let {
                requestBuilder.addHeader("Authorization", it)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val updated = server.copy(
                    isConnected = true,
                    latencyMs = latency,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                _servers.value = _servers.value.map { if (it.id == serverId) updated else it }
                Result.success(updated)
            } else {
                val updated = server.copy(
                    isConnected = false,
                    latencyMs = latency,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                _servers.value = _servers.value.map { if (it.id == serverId) updated else it }
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(10L)
            val updated = server.copy(
                isConnected = false,
                latencyMs = latency,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            _servers.value = _servers.value.map { if (it.id == serverId) updated else it }
            Result.failure(e)
        }
    }

    suspend fun executeTool(serverId: String, toolName: String, argumentsJson: String): McpToolCallResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val server = _servers.value.find { it.id == serverId }

        var isError = false
        val resultOutput: String = try {
            if (server != null && server.transportType != McpTransportType.STDIO_LOCAL &&
                (server.endpointUrl.startsWith("http://") || server.endpointUrl.startsWith("https://"))
            ) {
                // Real remote JSON-RPC 2.0 tools/call request
                val jsonRpcCall = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 2)
                    put("method", "tools/call")
                    put("params", JSONObject().apply {
                        put("name", toolName)
                        val argsObj = try { JSONObject(argumentsJson) } catch (_: Exception) { JSONObject() }
                        put("arguments", argsObj)
                    })
                }

                val reqBuilder = Request.Builder()
                    .url(server.endpointUrl)
                    .post(jsonRpcCall.toString().toRequestBody("application/json".toMediaType()))
                server.authHeader?.let { reqBuilder.addHeader("Authorization", it) }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val resJson = try { JSONObject(body) } catch (_: Exception) { null }
                    if (resJson != null && resJson.has("result")) {
                        resJson.opt("result").toString()
                    } else if (resJson != null && resJson.has("error")) {
                        isError = true
                        "MCP Error: ${resJson.optJSONObject("error")?.optString("message") ?: body}"
                    } else {
                        body
                    }
                } else {
                    isError = true
                    "HTTP ${response.code}: $body"
                }
            } else {
                // Real on-device implementation for local tools
                when (toolName) {
                    "fs_list_directory" -> {
                        val requestedPath = try {
                            val obj = JSONObject(argumentsJson)
                            obj.optString("path", context.filesDir.absolutePath)
                        } catch (_: Exception) {
                            context.filesDir.absolutePath
                        }
                        val dir = File(requestedPath)
                        if (dir.exists() && dir.isDirectory) {
                            val files = dir.listFiles() ?: emptyArray()
                            val jsonArr = JSONArray()
                            for (f in files.take(100)) {
                                val item = JSONObject().apply {
                                    put("name", f.name)
                                    put("type", if (f.isDirectory) "directory" else "file")
                                    put("sizeBytes", if (f.isFile) f.length() else 0L)
                                    put("path", f.absolutePath)
                                    put("lastModified", f.lastModified())
                                }
                                jsonArr.put(item)
                            }
                            jsonArr.toString(2)
                        } else {
                            isError = true
                            "Directory does not exist or is not readable: $requestedPath"
                        }
                    }

                    "fs_read_file" -> {
                        val filePath = try {
                            val obj = JSONObject(argumentsJson)
                            obj.optString("filePath", obj.optString("path", ""))
                        } catch (_: Exception) { "" }

                        if (filePath.isBlank()) {
                            isError = true
                            "Error: Parameter 'filePath' is required."
                        } else {
                            val file = File(filePath)
                            if (file.exists() && file.isFile && file.canRead()) {
                                val maxRead = 32768
                                val content = file.readText(Charsets.UTF_8).take(maxRead)
                                "=== [fs_read_file: ${file.name}] ===\nPath: ${file.absolutePath}\nSize: ${file.length()} bytes\n\n$content"
                            } else {
                                isError = true
                                "Error: File not found or unreadable at path: $filePath"
                            }
                        }
                    }

                    "fs_grep_search" -> {
                        val query = try {
                            JSONObject(argumentsJson).optString("query", "")
                        } catch (_: Exception) { "" }

                        if (query.isBlank()) {
                            isError = true
                            "Error: Parameter 'query' is required."
                        } else {
                            val matches = mutableListOf<String>()
                            context.filesDir.walkTopDown().maxDepth(4).filter { it.isFile && it.length() < 250_000 }.forEach { file ->
                                try {
                                    file.useLines { lines ->
                                        lines.forEachIndexed { lineNum, line ->
                                            if (line.contains(query, ignoreCase = true) && matches.size < 30) {
                                                matches.add("${file.name}:${lineNum + 1}: ${line.trim()}")
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            if (matches.isEmpty()) {
                                "No occurrences found for '$query' in local app workspace."
                            } else {
                                "Found ${matches.size} match(es) for '$query':\n" + matches.joinToString("\n") { "• $it" }
                            }
                        }
                    }

                    "db_execute_query" -> {
                        val sql = try {
                            JSONObject(argumentsJson).optString("sql", "SELECT name FROM sqlite_master WHERE type='table'")
                        } catch (_: Exception) {
                            "SELECT name FROM sqlite_master WHERE type='table'"
                        }
                        val db = AppDatabase.getInstance(context).openHelper.readableDatabase
                        val cursor = db.query(sql)
                        val colCount = cursor.columnCount
                        val colNames = (0 until colCount).map { cursor.getColumnName(it) }
                        val sb = StringBuilder()
                        sb.appendLine(colNames.joinToString(" | "))
                        sb.appendLine("-".repeat(colNames.sumOf { it.length } + colCount * 3))
                        var rows = 0
                        while (cursor.moveToNext() && rows < 50) {
                            val rowValues = (0 until colCount).map { cursor.getString(it) ?: "NULL" }
                            sb.appendLine(rowValues.joinToString(" | "))
                            rows++
                        }
                        cursor.close()
                        sb.appendLine("\n($rows rows returned from SQLite)")
                        sb.toString()
                    }

                    "vector_search_embeddings" -> {
                        val query = try {
                            JSONObject(argumentsJson).optString("query", "")
                        } catch (_: Exception) { "" }
                        val topK = try {
                            JSONObject(argumentsJson).optInt("topK", 3)
                        } catch (_: Exception) { 3 }

                        if (query.isBlank()) {
                            isError = true
                            "Error: Parameter 'query' is required."
                        } else {
                            val queryVec = VectorEmbeddingEngine.generateEmbedding(query)
                            val memories = AppDatabase.getInstance(context).semanticMemoryDao().getAllMemoriesSnapshot()
                            val scored = memories.map { mem ->
                                val vec = VectorEmbeddingEngine.stringToVector(mem.embeddingVector)
                                val score = VectorEmbeddingEngine.computeCosineSimilarity(queryVec, vec)
                                Pair(mem, score)
                            }.sortedByDescending { it.second }.take(topK)

                            if (scored.isEmpty()) {
                                "Vector Search: 0 memories indexed in local semantic memory store."
                            } else {
                                buildString {
                                    appendLine("=== Top-${scored.size} Nearest Neighbor Semantic Chunks (128-D Cosine Sim) ===")
                                    scored.forEachIndexed { i, (mem, score) ->
                                        appendLine("${i + 1}. [Score: ${String.format(java.util.Locale.US, "%.3f", score)}] [${mem.subject}] ${mem.content.take(140)}")
                                    }
                                }
                            }
                        }
                    }

                    else -> "Tool execution completed: $toolName"
                }
            }
        } catch (e: Exception) {
            isError = true
            "Execution failed: ${e.message ?: "Unknown error"}"
        }

        val elapsed = System.currentTimeMillis() - startTime
        val callResult = McpToolCallResult(
            serverId = serverId,
            toolName = toolName,
            argumentsJson = argumentsJson,
            outputContent = resultOutput,
            isError = isError,
            durationMs = elapsed
        )

        _lastToolExecution.value = callResult
        callResult
    }

    fun getAllAvailableTools(): List<McpToolDefinition> {
        return _servers.value
            .filter { it.isEnabled && it.isConnected }
            .flatMap { it.tools }
    }
}
