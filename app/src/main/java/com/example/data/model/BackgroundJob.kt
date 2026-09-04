package com.example.data.model

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    PAUSED_BATTERY
}

enum class JobType(val label: String) {
    LOG_ANOMALY_SCAN("Log Anomaly Scan"),
    TEXT_SUMMARIZATION("Batch Summarization"),
    PII_REDACTION("Privacy Sanitization"),
    CODE_AUDIT("Security Code Audit"),
    CUSTOM_PIPELINE("Custom Pipeline")
}

data class BackgroundJob(
    val id: String,
    val title: String,
    val jobType: JobType,
    val inputData: String,
    val status: JobStatus = JobStatus.QUEUED,
    val progressPercent: Int = 0,
    val resultSummary: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val tokensProcessed: Int = 0,
    val executionTimeMs: Long = 0L
)
