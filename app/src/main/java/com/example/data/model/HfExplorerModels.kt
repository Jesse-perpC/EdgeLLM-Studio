package com.example.data.model

enum class HfPipelineTag(val tag: String, val label: String) {
    ALL("all", "All Pipelines"),
    TEXT_GENERATION("text-generation", "Text Generation (LLM)"),
    TEXT2TEXT("text2text-generation", "Text-to-Text / Translate"),
    VLM("visual-question-answering", "Vision-Language (VLM)"),
    FEATURE_EXTRACTION("feature-extraction", "Embeddings / RAG"),
    IMAGE_TO_IMAGE("image-to-image", "Image / SD Tasks")
}

enum class HfLibraryFilter(val label: String) {
    ALL("All Libraries"),
    GGUF("GGUF"),
    SAFETENSORS("SafeTensors"),
    ONNX("ONNX"),
    MEDIAPIPE("MediaPipe GenAI"),
    MNN("Alibaba MNN")
}

enum class HfParamFilter(val label: String) {
    ALL("All Sizes"),
    TINY("< 1B (Ultra Edge)"),
    BALANCED("1B - 3B (Mobile Balanced)"),
    LARGE("7B - 9B (Flagship NPU)")
}

data class HfModelItem(
    val id: String,
    val modelId: String, // e.g. "bartowski/Llama-3.2-1B-Instruct-GGUF"
    val author: String,
    val modelName: String,
    val pipelineTag: HfPipelineTag,
    val library: String,
    val quantizations: List<String>,
    val downloads: Int,
    val likes: Int,
    val parameterCount: String,
    val sizeFormatted: String,
    val license: String,
    val isGated: Boolean = false,
    val description: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0
)
