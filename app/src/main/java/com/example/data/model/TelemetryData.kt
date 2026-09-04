package com.example.data.model

data class HardwareUsagePoint(
    val timestamp: Long,
    val gpuPercent: Float,
    val npuPercent: Float,
    val cpuPercent: Float,
    val memoryUsedGb: Float,
    val tokensPerSec: Float
)

data class MemoryConsumptionBreakdown(
    val totalRamBytes: Long,
    val modelWeightsBytes: Long,
    val kvCacheBytes: Long,
    val systemOsBytes: Long,
    val availableHeadroomBytes: Long
) {
    val totalRamGb: Float get() = totalRamBytes / (1024f * 1024f * 1024f)
    val modelWeightsGb: Float get() = modelWeightsBytes / (1024f * 1024f * 1024f)
    val kvCacheGb: Float get() = kvCacheBytes / (1024f * 1024f * 1024f)
    val systemOsGb: Float get() = systemOsBytes / (1024f * 1024f * 1024f)
    val availableHeadroomGb: Float get() = availableHeadroomBytes / (1024f * 1024f * 1024f)
    val usedRamGb: Float get() = modelWeightsGb + kvCacheGb + systemOsGb
    val usedPercentage: Float get() = (usedRamGb / totalRamGb.coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)
}

data class TelemetryDashboardState(
    val isLivePolling: Boolean = true,
    val pollingIntervalMs: Long = 1000L,
    val currentGpuPercent: Float = 42.5f,
    val currentNpuPercent: Float = 68.0f,
    val currentCpuPercent: Float = 28.0f,
    val currentTokensPerSec: Float = 24.8f,
    val timeToFirstTokenMs: Long = 135L,
    val powerDrawWatts: Float = 3.4f,
    val deviceTemperatureCelsius: Float = 36.2f,
    val activeContextTokens: Int = 512,
    val maxContextTokens: Int = 2048,
    val history: List<HardwareUsagePoint> = emptyList(),
    val memoryBreakdown: MemoryConsumptionBreakdown = MemoryConsumptionBreakdown(
        totalRamBytes = 8L * 1024L * 1024L * 1024L,
        modelWeightsBytes = 669L * 1024L * 1024L,
        kvCacheBytes = 256L * 1024L * 1024L,
        systemOsBytes = 2100L * 1024L * 1024L,
        availableHeadroomBytes = 5023L * 1024L * 1024L
    )
)
