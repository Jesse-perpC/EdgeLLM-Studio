package com.perpcorp.edgellm.agent.linux

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Execution result of a local shell command.
 */
data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)

/**
 * Status and configuration of the local shell workspace.
 *
 * This is an app-private directory for user scripts. It is NOT a PRoot or
 * chroot filesystem, and no Linux distribution is bundled or downloaded.
 */
data class LinuxWorkspaceStatus(
    val isInstalled: Boolean,
    val distro: String = "Local shell workspace",
    val rootFsPath: String = "",
    val totalPackagesInstalled: Int = 0,
    val diskUsageMb: Long = 0L,
    val isDaemonRunning: Boolean = false
)

/**
 * Manages the app-private shell workspace.
 *
 * Commands run through Android's `sh` inside the app sandbox and always
 * require explicit user approval at the tool/UI layer. The blocklist below is
 * defense-in-depth only; it is not a substitute for approval or OS isolation.
 */
class LinuxWorkspaceManager internal constructor(private val workspaceDir: File) {

    constructor(context: Context) : this(File(context.filesDir, "linux_workspace"))

    private val scriptsDir: File = File(workspaceDir, "scripts").apply {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
        if (!exists()) mkdirs()
    }

    fun getStatus(): LinuxWorkspaceStatus {
        val rootfsMarker = File(workspaceDir, ".installed")
        val isInstalled = rootfsMarker.exists()
        val diskMb = workspaceDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024)

        return LinuxWorkspaceStatus(
            isInstalled = isInstalled,
            distro = "Local shell workspace (no bundled distro)",
            rootFsPath = workspaceDir.absolutePath,
            totalPackagesInstalled = 0,
            diskUsageMb = diskMb,
            isDaemonRunning = false
        )
    }

    fun installMinimalWorkspace(): Boolean {
        return try {
            val marker = File(workspaceDir, ".installed")
            marker.writeText("workspace=local_shell\ninstalled_at=${System.currentTimeMillis()}")

            // Create sample user scripts
            val sampleScript = File(scriptsDir, "system_info.sh")
            sampleScript.writeText(
                """#!/bin/sh
echo "=== EdgeLLM Linux Userland Environment ==="
echo "Kernel: $(uname -a 2>/dev/null || echo 'Linux aarch64 Android')"
echo "Date: $(date)"
echo "Uptime: $(uptime 2>/dev/null || echo 'Active')"
echo "Workspace: ${workspaceDir.absolutePath}"
"""
            )
            sampleScript.setExecutable(true)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCommand(command: String, timeoutMs: Long = 8000L): ShellCommandResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        // Defense-in-depth only. Approval at the tool/UI layer is the primary
        // control; this list reduces the blast radius of accidental paste-ins.
        val normalized = command.trim().lowercase().replace(Regex("\\s+"), " ")
        val blockedPatterns = listOf(
            Regex("""(^|[;&|]\s*)rm\s+(-[a-z]*[rf][a-z]*\s+)+/(\s|$)"""),
            Regex("""(^|[;&|]\s*)(mkfs|mkswap|wipefs|fdisk|parted)\b"""),
            Regex("""(^|[;&|]\s*)(reboot|shutdown)\b"""),
            Regex("""dd\s+if=/dev/zero"""),
            Regex("""dd\s+of=/dev/block"""),
            Regex(""":\(\)\s*\{\s*:\|:&\s*\}\s*;:"""),
            Regex(""">\s*/dev/block"""),
            Regex("""mount\s+-o\s+remount,rw\s+/system"""),
            Regex("""chmod\s+-r\s+777\s+/"""),
        )
        for (pattern in blockedPatterns) {
            if (pattern.containsMatchIn(normalized)) {
                return@withContext ShellCommandResult(
                    exitCode = 126,
                    stdout = "",
                    stderr = "BLOCKED: Safety Sandbox prevented execution of dangerous destructive command pattern: \"${pattern.pattern}\"",
                    durationMs = System.currentTimeMillis() - start
                )
            }
        }

        try {
            // Execute command using Android's native sh shell
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workspaceDir)
                .redirectErrorStream(false)
                .start()

            // Drain stdout and stderr concurrently so a full stderr pipe can
            // never deadlock the process while we block on stdout.
            val stdoutDeferred = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            if (length < 50_000) appendLine(line)
                            line = reader.readLine()
                        }
                    }
                }
            }
            val stderrDeferred = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { reader ->
                    buildString {
                        var line = reader.readLine()
                        while (line != null) {
                            if (length < 20_000) appendLine(line)
                            line = reader.readLine()
                        }
                    }
                }
            }

            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                awaitExitCompat(process, timeoutMs)
            }
            if (!finished) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                } else {
                    process.destroy()
                }
                return@withContext ShellCommandResult(
                    exitCode = 124,
                    stdout = runCatching { stdoutDeferred.await() }.getOrDefault("").trimEnd(),
                    stderr = "TIMEOUT: Command exceeded ${timeoutMs}ms and was terminated.",
                    durationMs = System.currentTimeMillis() - start
                )
            }

            ShellCommandResult(
                exitCode = process.exitValue(),
                stdout = stdoutDeferred.await().trimEnd(),
                stderr = stderrDeferred.await().trimEnd(),
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            ShellCommandResult(
                exitCode = 1,
                stdout = "",
                stderr = "Execution error: ${e.message}",
                durationMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun awaitExitCompat(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }

    fun listScripts(): List<File> {
        return scriptsDir.listFiles { file -> file.isFile }?.toList() ?: emptyList()
    }

    fun saveScript(name: String, content: String): File {
        val cleanName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val file = File(scriptsDir, cleanName)
        file.writeText(content)
        file.setExecutable(true)
        return file
    }
}
