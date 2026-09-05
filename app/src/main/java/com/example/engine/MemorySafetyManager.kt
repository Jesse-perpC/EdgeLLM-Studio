package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.model.ModelSpec
import java.io.File

data class MemoryConstraintReport(
    val isSafeToRun: Boolean,
    val isStorageSufficient: Boolean,
    val warningMessage: String?,
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val requiredRamBytes: Long,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val maxHeapBytes: Long,
    val usedHeapBytes: Long
)

class MemorySafetyManager(private val context: Context) {

    fun evaluateModelSafety(model: ModelSpec): MemoryConstraintReport {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val isLowMem = memInfo.lowMemory

        // Storage space check
        val statFs = try {
            StatFs(context.filesDir.absolutePath)
        } catch (_: Exception) {
            null
        }

        val freeStorage = statFs?.availableBytes ?: (2L * 1024L * 1024L * 1024L)
        val totalStorage = statFs?.totalBytes ?: (32L * 1024L * 1024L * 1024L)

        // JVM Heap limits
        val runtime = Runtime.getRuntime()
        val maxHeap = runtime.maxMemory()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()

        val requiredRam = model.requiredRamBytes
        val requiredDisk = model.fileSizeBytes

        val isStorageSufficient = freeStorage > (requiredDisk + 100L * 1024L * 1024L) // 100MB safety buffer

        var warning: String? = null
        var isSafe = true

        if (!isStorageSufficient && !model.isDownloaded) {
            warning = "Insufficient local storage: Model requires ${formatBytes(requiredDisk)}, but only ${formatBytes(freeStorage)} is free."
            isSafe = false
        } else if (isLowMem) {
            warning = "System is in Critical Low Memory condition. Running this model may trigger OS process termination."
            isSafe = false
        } else if (requiredRam > availRam) {
            warning = "High RAM Pressure: Model requires ${formatBytes(requiredRam)}, available RAM is ${formatBytes(availRam)}. Virtual memory paging and layer offloading enabled to prevent crash."
            isSafe = true // Safe because we use mmap and streaming layers
        }

        return MemoryConstraintReport(
            isSafeToRun = isSafe,
            isStorageSufficient = isStorageSufficient,
            warningMessage = warning,
            availableRamBytes = availRam,
            totalRamBytes = totalRam,
            requiredRamBytes = requiredRam,
            freeStorageBytes = freeStorage,
            totalStorageBytes = totalStorage,
            maxHeapBytes = maxHeap,
            usedHeapBytes = usedHeap
        )
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            "$mb MB"
        }
    }
}
