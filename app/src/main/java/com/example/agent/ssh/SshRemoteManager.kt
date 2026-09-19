package com.example.agent.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Configuration for remote SSH server profile.
 */
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: String = "Password", // Password or PrivateKey
    val isConnected: Boolean = false,
    val lastPingMs: Long = -1L
)

/**
 * Result of an SSH command execution.
 */
data class SshExecutionResult(
    val profileId: String,
    val command: String,
    val exitCode: Int,
    val output: String,
    val latencyMs: Long
)

/**
 * Manages remote SSH host configurations, reachability pings, and remote command workflows (from rikkahub-agent).
 */
class SshRemoteManager {

    suspend fun pingHost(host: String, port: Int = 22, timeoutMs: Int = 3000): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val duration = System.currentTimeMillis() - start
                Result.success(duration)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testSshBanner(host: String, port: Int = 22, timeoutMs: Int = 3500): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val banner = reader.readLine() ?: "No banner received"
                Result.success(banner)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
