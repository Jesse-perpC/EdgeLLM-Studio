package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.model.CompatibilityRating
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec

class HardwareCapabilityDetector(private val context: Context) {

    fun detectHardware(): DeviceHardwareInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val isLowMem = memInfo.lowMemory

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            "${Build.HARDWARE} (${Build.BOARD})"
        }

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 85
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Optimal (Cool)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Warmth"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Emergency"
                else -> "Normal (Cool)"
            }
        } else {
            "Normal (Cool)"
        }

        val packageManager = context.packageManager
        val hasVulkan = packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasNpu = is64Bit && cpuCores >= 4 // Modern 64-bit multi-core SoCs include NNAPI HTP/NPU acceleration

        return DeviceHardwareInfo(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            isLowMemory = isLowMem,
            cpuCores = cpuCores,
            cpuArchitecture = abis,
            socModel = socModel.ifBlank { "Octa-Core Kryo/Cortex" },
            batteryLevel = batteryPct,
            isCharging = isCharging,
            thermalStatus = thermalStatus,
            hasVulkanCompute = hasVulkan,
            hasNpuSupport = hasNpu,
            hasOpenCl = is64Bit,
            is64Bit = is64Bit
        )
    }

    fun evaluateCompatibility(model: ModelSpec, hardware: DeviceHardwareInfo): CompatibilityRating {
        val requiredBytes = model.requiredRamBytes
        val availableBytes = hardware.availableRamBytes

        return when {
            requiredBytes > hardware.totalRamBytes * 0.85f -> {
                CompatibilityRating.LOW_RAM_WARNING
            }
            requiredBytes > availableBytes -> {
                CompatibilityRating.OFFLOAD_RECOMMENDED
            }
            requiredBytes <= availableBytes * 0.5f && (hardware.hasVulkanCompute || hardware.hasNpuSupport) -> {
                CompatibilityRating.OPTIMAL
            }
            else -> {
                CompatibilityRating.BALANCED
            }
        }
    }
}
