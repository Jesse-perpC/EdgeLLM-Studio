package com.example.data.model

enum class ImageTaskMode(val title: String, val description: String) {
    TEXT_TO_IMAGE("Text to Image", "Generate new images from descriptive text prompts"),
    IMAGE_TO_IMAGE("Image to Image", "Transform or style an existing image with text guidance"),
    INPAINTING("Inpaint & Mask", "Brush over unwanted areas and inpaint new elements"),
    SUPER_RESOLUTION("4× Upscale", "Enhance image resolution 4× using neural super-resolution")
}

enum class SdModel(
    val id: String,
    val displayName: String,
    val format: String,
    val speedRating: String,
    val resolution: Int
) {
    SD_1_5("sd_1_5", "Stable Diffusion 1.5 (Q4)", "MNN / QNN", "3.2 it/s", 512),
    SD_TURBO("sd_turbo", "SD Turbo (1-Step Realtime)", "NPU / QNN", "12.4 it/s", 512),
    SD_XS("sd_xs", "Stable Diffusion XS (Edge)", "Vulkan GPU", "7.1 it/s", 512),
    LCM_DREAM("lcm_dream", "LCM Latent Consistency 4-Step", "NPU / CPU", "8.6 it/s", 512)
}

enum class AspectRatioOption(val label: String, val width: Int, val height: Int, val ratioFloat: Float) {
    SQUARE("1:1 Square", 512, 512, 1.0f),
    LANDSCAPE("16:9 Cinema", 768, 432, 1.77f),
    PORTRAIT("9:16 Story", 432, 768, 0.56f),
    CLASSIC_PHOTO("4:3 Photo", 640, 480, 1.33f)
}

data class GeneratedImageItem(
    val id: String,
    val prompt: String,
    val negativePrompt: String = "",
    val mode: ImageTaskMode,
    val model: SdModel,
    val steps: Int,
    val cfgScale: Float,
    val seed: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val generationTimeMs: Long,
    val base64Thumbnail: String? = null,
    val sampleDrawableColorHex: Long = 0xFF1E293B,
    val labelText: String = "Edge Gen",
    val isUpscaled: Boolean = false
)
