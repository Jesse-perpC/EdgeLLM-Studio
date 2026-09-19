package com.example.agent.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Message received or sent through the Telegram Bot integration.
 */
data class TelegramBotMessage(
    val messageId: Long,
    val chatId: Long,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isIncoming: Boolean
)

/**
 * Configuration and credentials for Telegram Bot bridge.
 */
data class TelegramBotConfig(
    val botToken: String = "",
    val authorizedChatId: String = "",
    val isEnabled: Boolean = false,
    val forwardInferenceResults: Boolean = true,
    val allowRemoteCommands: Boolean = true,
    val pollingIntervalSeconds: Int = 10
)

/**
 * Manages the Telegram Bot service (from rikkahub-agent).
 * Allows users to interact with the offline or cloud LLM remotely via Telegram,
 * execute agent tools via chat commands (/battery, /status, /run, /ask), and receive alerts.
 */
class TelegramBotManager {

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Bot token cannot be empty"))
        try {
            val url = URL("https://api.telegram.org/bot${token.trim()}/getMe")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val username = result.optString("username", "UnknownBot")
                    val firstName = result.optString("first_name", "Edge Assistant")
                    Result.success("Connected to @$username ($firstName)")
                } else {
                    Result.failure(Exception("Telegram API returned: ${json.optString("description")}"))
                }
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception("HTTP $code: $errorStream"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(token: String, chatId: String, text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing bot token or chatId"))
        }

        try {
            val url = URL("https://api.telegram.org/bot${token.trim()}/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("chat_id", chatId.trim())
                put("text", text)
                put("parse_mode", "Markdown")
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code == 200) {
                Result.success(true)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Result.failure(Exception("Failed to send message: HTTP $code - $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
