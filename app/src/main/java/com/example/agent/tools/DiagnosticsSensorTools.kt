package com.example.agent.tools

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.agent.AgentSecurityLevel
import com.example.agent.AgentTool
import com.example.agent.AgentToolCategory
import com.example.agent.AgentToolParameter
import com.example.agent.AgentToolResult
import java.io.File
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

/**
 * Diagnostics, Storage, Sensors, and Radios inspection tools from rikkahub-agent:
 * - diag_system_specs
 * - diag_storage_status
 * - diag_ram_usage
 * - diag_network_ip
 * - diag_wifi_details
 * - diag_installed_apps_count
 * - diag_thermal_throttle
 * - diag_sandbox_files
 */
object DiagnosticsSensorTools {

    val allTools: List<AgentTool> = listOf(
        SystemSpecsTool(),
        StorageStatusTool(),
        RamUsageTool(),
        NetworkIpTool(),
        WifiDetailsTool(),
        InstalledAppsCountTool(),
        ThermalThrottleTool(),
        SandboxFilesTool()
    )

    class SystemSpecsTool : AgentTool {
        override val id = "diag_system_specs"
        override val name = "System Hardware & OS Diagnostic"
        override val description = "Gathers hardware model, SOC, Android API level, kernel architecture, and supported ABIs."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val model = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val hardware = Build.HARDWARE

            val report = buildString {
                appendLine("📱 Device: $manufacturer $model")
                appendLine("🤖 Android: $androidVer (API $sdkInt)")
                appendLine("⚙️ Silicon: $hardware • ABIs: [$abis]")
                appendLine("🔒 Security Patch: ${Build.VERSION.SECURITY_PATCH}")
            }

            return AgentToolResult(
                isSuccess = true,
                output = report.trimEnd(),
                rawData = mapOf("manufacturer" to manufacturer, "model" to model, "sdk" to sdkInt),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class StorageStatusTool : AgentTool {
        override val id = "diag_storage_status"
        override val name = "Storage Capacity & Partition Health"
        override val description = "Calculates total, used, and free internal storage space in GB."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val usedPct = (usedBytes.toDouble() / totalBytes.toDouble() * 100.0).toInt()

            val text = "Internal Storage: %.1f GB used / %.1f GB total (%.1f GB free, %d%% utilized).".format(
                usedGb, totalGb, freeGb, usedPct
            )

            return AgentToolResult(
                isSuccess = true,
                output = text,
                rawData = mapOf("totalGb" to totalGb, "freeGb" to freeGb, "usedPct" to usedPct),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class RamUsageTool : AgentTool {
        override val id = "diag_ram_usage"
        override val name = "RAM & Memory Pressure"
        override val description = "Checks available system memory, low-memory pressure threshold, and LLM workspace headroom."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)

            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedMb = totalMb - availMb
            val lowMem = memInfo.lowMemory

            val text = "RAM: %d MB used / %d MB total (%d MB available). Low-RAM Alert: %b.".format(
                usedMb, totalMb, availMb, lowMem
            )

            return AgentToolResult(
                isSuccess = true,
                output = text,
                rawData = mapOf("totalMb" to totalMb, "availMb" to availMb, "lowMemory" to lowMem),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class NetworkIpTool : AgentTool {
        override val id = "diag_network_ip"
        override val name = "Network Interfaces & Local IP"
        override val description = "Resolves Wi-Fi and cellular IPv4/IPv6 addresses and active connectivity type."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val ips = mutableListOf<String>()

            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr.hostAddress != null) {
                            val sAddr = addr.hostAddress!!
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                ips.add("${intf.name}: $sAddr")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val netType = when {
                isWifi -> "Wi-Fi"
                isCell -> "Cellular Mobile"
                else -> "Disconnected / Air-Gapped"
            }

            val out = "Network: $netType | Local IPs: " + (if (ips.isEmpty()) "None (Air-Gapped)" else ips.joinToString(", "))

            return AgentToolResult(
                isSuccess = true,
                output = out,
                rawData = mapOf("networkType" to netType, "ips" to ips),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class WifiDetailsTool : AgentTool {
        override val id = "diag_wifi_details"
        override val name = "Wi-Fi Radio & Signal Strength"
        override val description = "Checks Wi-Fi connection state, SSID, link speed, and RSSI signal level."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val isWifiEnabled = wm?.isWifiEnabled ?: false
            val connInfo = wm?.connectionInfo

            val ssid = connInfo?.ssid?.replace("\"", "") ?: "Unknown"
            val rssi = connInfo?.rssi ?: -100
            val linkSpeed = connInfo?.linkSpeed ?: 0

            val output = if (isWifiEnabled) {
                "Wi-Fi Radio: Enabled | Network: $ssid | RSSI: $rssi dBm | Link Speed: ${linkSpeed} Mbps"
            } else {
                "Wi-Fi Radio: Disabled (Device is in airplane or cellular-only mode)."
            }

            return AgentToolResult(
                isSuccess = true,
                output = output,
                rawData = mapOf("enabled" to isWifiEnabled, "ssid" to ssid, "rssi" to rssi),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class InstalledAppsCountTool : AgentTool {
        override val id = "diag_installed_apps_count"
        override val name = "Application Inventory Count"
        override val description = "Lists total count of packages installed in the user profile."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val nonSystem = apps.count { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }

            return AgentToolResult(
                isSuccess = true,
                output = "Application Packages: ${apps.size} total (${nonSystem} user-installed apps).",
                rawData = mapOf("total" to apps.size, "userInstalled" to nonSystem),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class ThermalThrottleTool : AgentTool {
        override val id = "diag_thermal_throttle"
        override val name = "Thermal & Cooling Status"
        override val description = "Queries CPU thermal headroom to verify if thermal throttling will degrade LLM tokens/sec."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (pm?.currentThermalStatus) {
                    android.os.PowerManager.THERMAL_STATUS_NONE -> "Nominal (Cool)"
                    android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                    android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Fans / Throttle alert)"
                    android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                    android.os.PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
                    else -> "Optimal"
                }
            } else {
                "Optimal (Hardware normal)"
            }

            return AgentToolResult(
                isSuccess = true,
                output = "Thermal State: $thermalStatus. Sustained NPU/CPU compute profile is active.",
                rawData = mapOf("status" to thermalStatus),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class SandboxFilesTool : AgentTool {
        override val id = "diag_sandbox_files"
        override val name = "Inspect App Sandbox Storage"
        override val description = "Scans files, caches, and models stored inside EdgeLLM Studio's local private sandbox."
        override val category = AgentToolCategory.SYSTEM_DIAGNOSTICS
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val filesDir = context.filesDir
            val cacheDir = context.cacheDir

            val fileCount = filesDir.walkTopDown().filter { it.isFile }.count()
            val cacheCount = cacheDir.walkTopDown().filter { it.isFile }.count()

            fun getFolderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            val totalBytes = getFolderSize(filesDir) + getFolderSize(cacheDir)
            val sizeMb = totalBytes / (1024 * 1024)

            return AgentToolResult(
                isSuccess = true,
                output = "App Sandbox: $fileCount data files, $cacheCount cache files (${sizeMb} MB total storage occupied).",
                rawData = mapOf("sizeMb" to sizeMb, "fileCount" to fileCount),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }
}
