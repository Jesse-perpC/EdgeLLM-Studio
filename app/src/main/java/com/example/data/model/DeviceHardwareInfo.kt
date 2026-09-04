package com.example.data.model

data class DeviceHardwareInfo(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowMemory: Boolean,
    val cpuCores: Int,
    val cpuArchitecture: String,
    val socModel: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalStatus: String,
    val hasVulkanCompute: Boolean,
    val hasNpuSupport: Boolean,
    val hasOpenCl: Boolean,
    val is64Bit: Boolean
) {
    val totalRamGb: Float
        get() = totalRamBytes / (1024f * 1024f * 1024f)

    val availableRamGb: Float
        get() = availableRamBytes / (1024f * 1024f * 1024f)

    val ramUsagePercent: Int
        get() {
            if (totalRamBytes <= 0) return 0
            val used = totalRamBytes - availableRamBytes
            return ((used.toDouble() / totalRamBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
        }
}

enum class ComputeBackend(val displayName: String, val shortName: String, val description: String) {
    CPU_NEON("CPU (ARM NEON SIMD)", "CPU-NEON", "Reliable multi-threaded execution on CPU cores"),
    GPU_VULKAN("GPU (Vulkan Shaders)", "Vulkan", "Offload matrix multiplication to mobile GPU"),
    NPU_NNAPI("NPU (Android NNAPI / HTP)", "NPU-HTP", "Fastest on-device tensor acceleration, lowest battery drain"),
    OPENCL("GPU (OpenCL Compute)", "OpenCL", "Alternative direct GPU compute pipeline")
}

enum class PowerProfile(val displayName: String, val description: String, val iconName: String) {
    BATTERY_SAVER("Battery Saver", "2 threads, throttled token frequency, low heat", "Eco"),
    BALANCED("Balanced", "4 threads, adaptive clocking, standard inference rate", "Tune"),
    HIGH_PERFORMANCE("High Performance", "All big CPU cores + GPU offload for peak tokens/sec", "Bolt")
}

enum class KvCacheQuant(val displayName: String, val memorySavings: String) {
    FP16("FP16 (High Precision)", "Standard memory footprint"),
    Q8_0("Q8_0 (8-Bit Quantized)", "~45% KV RAM reduction"),
    Q4_0("Q4_0 (4-Bit Quantized)", "~70% KV RAM reduction")
}

data class HardwareAccelerationSettings(
    val threadCount: Int = 4,
    val maxThreads: Int = 8,
    val computeBackend: ComputeBackend = ComputeBackend.GPU_VULKAN,
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    val kvCacheQuantization: KvCacheQuant = KvCacheQuant.Q8_0,
    val useMmap: Boolean = true,
    val useFlashAttention: Boolean = true,
    val batteryCutoffPercent: Int = 15,
    val pauseOnThermalThrottling: Boolean = true,
    val allowScreenLockedProcessing: Boolean = true
)
