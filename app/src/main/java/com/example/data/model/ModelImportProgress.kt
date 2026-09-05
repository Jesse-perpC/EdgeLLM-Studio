package com.example.data.model

data class ModelImportProgress(
    val isImporting: Boolean = false,
    val folderName: String = "",
    val currentFileName: String = "",
    val currentFileSizeFormatted: String = "",
    val processedCount: Int = 0,
    val totalDiscovered: Int = 0,
    val progressPercent: Int = 0,
    val stageStatusText: String = "",
    val importedCount: Int = 0,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val importedModelNames: List<String> = emptyList(),
    val autoActivatedModelName: String? = null
)
