package com.example.data.model

enum class ModelFormat(val displayName: String, val badgeColor: Long) {
    GGUF("GGUF", 0xFF06B6D4),
    TFLITE("TensorFlow Lite", 0xFFF59E0B),
    ONNX("ONNX Runtime", 0xFF8B5CF6)
}

enum class CompatibilityRating(val label: String, val description: String, val color: Long) {
    OPTIMAL("Optimal Run", "Runs lightning-fast entirely in RAM with full hardware acceleration", 0xFF10B981),
    BALANCED("Balanced", "Fits comfortably in available memory with standard quantization", 0xFF06B6D4),
    OFFLOAD_RECOMMENDED("NPU / GPU Needed", "Heavy compute; recommend Vulkan or NPU backend", 0xFFF59E0B),
    LOW_RAM_WARNING("Memory Constrained", "High RAM usage; close other apps to prevent out-of-memory", 0xFFEF4444)
}

enum class ModelCategory(val title: String) {
    CHAT_REASONING("Chat & Reasoning"),
    CODE_ANALYSIS("Code & Security"),
    EMBEDDINGS_CLASSIFICATION("Classification & Fast Tasks"),
    VISION_MULTIMODAL("Multimodal / Vision")
}

data class ModelSpec(
    val id: String,
    val name: String,
    val parameterCount: String,
    val format: ModelFormat,
    val quantization: String,
    val fileSizeBytes: Long,
    val requiredRamBytes: Long,
    val contextLength: Int,
    val description: String,
    val category: ModelCategory,
    val downloadUrl: String,
    val sha256Checksum: String,
    val isDownloaded: Boolean = false,
    val downloadProgressPercent: Int = 0,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val downloadSpeedFormatted: String = "",
    val downloadedBytes: Long = 0L,
    val downloadStatusText: String = "",
    val isActive: Boolean = false
) {
    val downloadedBytesFormatted: String
        get() {
            val mb = downloadedBytes / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                "$mb MB"
            }
        }
    val fileSizeFormatted: String
        get() {
            val mb = fileSizeBytes / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                "$mb MB"
            }
        }

    val ramRequiredFormatted: String
        get() {
            val mb = requiredRamBytes / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.1f GB RAM", mb / 1024.0)
            } else {
                "$mb MB RAM"
            }
        }
}
