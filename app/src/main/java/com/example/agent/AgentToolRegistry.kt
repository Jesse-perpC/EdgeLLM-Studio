package com.example.agent

import android.content.Context
import com.example.agent.linux.LinuxWorkspaceManager
import com.example.agent.ssh.SshProfile
import com.example.agent.ssh.SshRemoteManager
import com.example.agent.telegram.TelegramBotConfig
import com.example.agent.telegram.TelegramBotManager
import com.example.agent.tools.CommunicationProductivityTools
import com.example.agent.tools.DeviceControlTools
import com.example.agent.tools.DiagnosticsSensorTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Central registry and dispatcher for all RikkaHub Agent features, tools, and services:
 * - 30+ Device, Communication, Productivity, and Diagnostic tools
 * - Linux Workspace & PRoot execution
 * - Telegram Bot Integration
 * - SSH Remote Host Manager
 */
class AgentToolRegistry(private val context: Context) {

    private val _tools = MutableStateFlow<List<AgentTool>>(emptyList())
    val tools: StateFlow<List<AgentTool>> = _tools.asStateFlow()

    // Subsystem managers
    val linuxManager = LinuxWorkspaceManager(context)
    val telegramManager = TelegramBotManager()
    val sshManager = SshRemoteManager()

    // Configuration States
    private val _telegramConfig = MutableStateFlow(TelegramBotConfig())
    val telegramConfig: StateFlow<TelegramBotConfig> = _telegramConfig.asStateFlow()

    private val _sshProfiles = MutableStateFlow<List<SshProfile>>(
        listOf(
            SshProfile(name = "Home Raspberry Pi 5", host = "192.168.1.150", port = 22, username = "pi"),
            SshProfile(name = "Cloud GPU Cluster", host = "gpu.internal.net", port = 2222, username = "ubuntu")
        )
    )
    val sshProfiles: StateFlow<List<SshProfile>> = _sshProfiles.asStateFlow()

    private val _lastExecutionLog = MutableStateFlow<String?>(null)
    val lastExecutionLog: StateFlow<String?> = _lastExecutionLog.asStateFlow()

    init {
        val initialList = mutableListOf<AgentTool>()
        initialList.addAll(DeviceControlTools.allTools)
        initialList.addAll(CommunicationProductivityTools.allTools)
        initialList.addAll(DiagnosticsSensorTools.allTools)

        // Add Linux Workspace Tools
        initialList.add(LinuxShellExecuteTool())
        initialList.add(LinuxDistroStatusTool())

        // Add Telegram Bot Tools
        initialList.add(TelegramSendNotificationTool())
        initialList.add(TelegramCheckStatusTool())

        // Add SSH Remote Tools
        initialList.add(SshPingHostTool())

        _tools.value = initialList
    }

    fun getTool(id: String): AgentTool? = _tools.value.firstOrNull { it.id == id }

    fun getToolsByCategory(category: AgentToolCategory): List<AgentTool> =
        _tools.value.filter { it.category == category }

    suspend fun executeTool(toolId: String, arguments: Map<String, String>): AgentToolResult {
        val tool = getTool(toolId) ?: return AgentToolResult(
            isSuccess = false,
            output = "Tool with ID \"$toolId\" not found in AgentToolRegistry."
        )

        val result = tool.execute(context, arguments)
        _lastExecutionLog.value = "[${tool.id}] Success=${result.isSuccess} (${result.executionTimeMs}ms): ${result.output.take(120)}"
        return result
    }

    fun updateTelegramConfig(config: TelegramBotConfig) {
        _telegramConfig.value = config
    }

    fun addSshProfile(profile: SshProfile) {
        _sshProfiles.value = _sshProfiles.value + profile
    }

    fun removeSshProfile(id: String) {
        _sshProfiles.value = _sshProfiles.value.filter { it.id != id }
    }

    // Embedded Dynamic Tools for Linux, Telegram, and SSH

    inner class LinuxShellExecuteTool : AgentTool {
        override val id = "linux_shell_exec"
        override val name = "Linux Shell Command"
        override val description = "Executes a POSIX shell command in the local Linux userland sandbox (e.g. ls, uname, cat, python)."
        override val category = AgentToolCategory.LINUX_WORKSPACE
        override val securityLevel = AgentSecurityLevel.RESTRICTED
        override val parameters = listOf(
            AgentToolParameter("command", "string", "Shell command string to execute", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val cmd = arguments["command"] ?: "uname -a"
            val result = linuxManager.executeCommand(cmd)
            val output = if (result.exitCode == 0) {
                if (result.stdout.isNotBlank()) result.stdout else "(Command succeeded with empty output)"
            } else {
                "Error (Exit ${result.exitCode}): ${result.stderr.ifBlank { result.stdout }}"
            }
            return AgentToolResult(
                isSuccess = result.exitCode == 0,
                output = output,
                rawData = mapOf("exitCode" to result.exitCode, "durationMs" to result.durationMs),
                executionTimeMs = result.durationMs
            )
        }
    }

    inner class LinuxDistroStatusTool : AgentTool {
        override val id = "linux_distro_status"
        override val name = "Linux PRoot Environment Status"
        override val description = "Checks whether Debian/Alpine rootfs is provisioned and inspects disk space."
        override val category = AgentToolCategory.LINUX_WORKSPACE
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val status = linuxManager.getStatus()
            val text = if (status.isInstalled) {
                "Linux Workspace: Installed (${status.distro}). Sandbox path: ${status.rootFsPath} (${status.diskUsageMb} MB used)."
            } else {
                "Linux Workspace: Not provisioned yet. Ready to initialize Debian/Alpine rootfs."
            }
            return AgentToolResult(
                isSuccess = true,
                output = text,
                rawData = mapOf("isInstalled" to status.isInstalled, "diskUsageMb" to status.diskUsageMb),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    inner class TelegramSendNotificationTool : AgentTool {
        override val id = "telegram_send_alert"
        override val name = "Send Telegram Alert"
        override val description = "Pushes a message or inference alert to the configured Telegram chat via bot."
        override val category = AgentToolCategory.TELEGRAM_BOT
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("message", "string", "Message to broadcast to Telegram chat", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val config = _telegramConfig.value
            if (config.botToken.isBlank() || config.authorizedChatId.isBlank()) {
                return AgentToolResult(
                    isSuccess = false,
                    output = "Telegram Bot credentials not configured. Please set Bot Token and Chat ID in Agent settings.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
            val msg = arguments["message"] ?: "Alert from EdgeLLM Assistant"
            val result = telegramManager.sendMessage(config.botToken, config.authorizedChatId, msg)
            return AgentToolResult(
                isSuccess = result.isSuccess,
                output = if (result.isSuccess) "Alert delivered to Telegram chat ${config.authorizedChatId}." else "Telegram send failed: ${result.exceptionOrNull()?.message}",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    inner class TelegramCheckStatusTool : AgentTool {
        override val id = "telegram_check_status"
        override val name = "Telegram Bot Status"
        override val description = "Checks whether the Telegram Bot webhook/polling bridge is connected."
        override val category = AgentToolCategory.TELEGRAM_BOT
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val config = _telegramConfig.value
            return if (config.botToken.isNotBlank()) {
                val res = telegramManager.testConnection(config.botToken)
                AgentToolResult(
                    isSuccess = res.isSuccess,
                    output = if (res.isSuccess) res.getOrNull() ?: "Connected" else "Connection error: ${res.exceptionOrNull()?.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } else {
                AgentToolResult(
                    isSuccess = false,
                    output = "No Telegram Bot token configured yet.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    inner class SshPingHostTool : AgentTool {
        override val id = "ssh_ping_host"
        override val name = "Ping SSH Server"
        override val description = "Tests TCP reachability and grabs the SSH identification banner of a remote server."
        override val category = AgentToolCategory.SSH_REMOTE
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = listOf(
            AgentToolParameter("host", "string", "IP or hostname of the remote SSH server", required = true),
            AgentToolParameter("port", "number", "SSH port (default 22)", required = false, defaultValue = "22")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val host = arguments["host"] ?: "127.0.0.1"
            val port = arguments["port"]?.toIntOrNull() ?: 22

            val bannerRes = sshManager.testSshBanner(host, port)
            return if (bannerRes.isSuccess) {
                AgentToolResult(
                    isSuccess = true,
                    output = "SSH Host $host:$port is REACHABLE. Banner: \"${bannerRes.getOrNull()}\"",
                    rawData = mapOf("host" to host, "port" to port, "banner" to (bannerRes.getOrNull() ?: "")),
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } else {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to reach SSH host $host:$port: ${bannerRes.exceptionOrNull()?.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }
}
