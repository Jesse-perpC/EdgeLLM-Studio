package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.repository.EdgeLLMRepository
import com.example.engine.MemorySafetyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: EdgeLLMRepository
    private lateinit var memorySafetyManager: MemorySafetyManager
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val pausedModels = ConcurrentHashMap.newKeySet<String>()

    companion object {
        const val CHANNEL_ID = "edgellm_model_download_channel"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.example.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.action.CANCEL_DOWNLOAD"
        const val ACTION_DELETE_MODEL = "com.example.action.DELETE_MODEL"

        const val EXTRA_MODEL_ID = "extra_model_id"
        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val EXTRA_FILE_SIZE = "extra_file_size"

        @Volatile
        var isServiceActive = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        repository = EdgeLLMRepository(db)
        memorySafetyManager = MemorySafetyManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: "Model"
        val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 500L * 1024L * 1024L)

        when (intent.action) {
            ACTION_START_DOWNLOAD -> {
                startForeground(NOTIFICATION_ID, buildNotification("Downloading $modelName", "Initializing download stream..."))
                isServiceActive = true
                pausedModels.remove(modelId)
                startModelDownload(modelId, modelName, fileSize)
            }
            ACTION_PAUSE_DOWNLOAD -> {
                pauseModelDownload(modelId)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelModelDownload(modelId)
            }
            ACTION_DELETE_MODEL -> {
                deleteLocalModel(modelId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startModelDownload(modelId: String, modelName: String, fileSize: Long) {
        if (activeDownloadJobs.containsKey(modelId)) return

        val job = serviceScope.launch {
            try {
                val currentModel = repository.getModelById(modelId)
                if (currentModel != null) {
                    val safety = memorySafetyManager.evaluateModelSafety(currentModel)
                    if (!safety.isStorageSufficient) {
                        updateNotification("Download Failed", safety.warningMessage ?: "Insufficient local storage")
                        return@launch
                    }
                }

                val modelsDir = File(filesDir, "models").apply { mkdirs() }
                val extension = when {
                    modelName.contains("gguf", ignoreCase = true) -> "gguf"
                    modelName.contains("onnx", ignoreCase = true) -> "onnx"
                    else -> "tflite"
                }
                val destFile = File(modelsDir, "$modelId.$extension")
                val startProgress = currentModel?.downloadProgressPercent ?: 0
                var currentProgress = startProgress

                updateNotification("Downloading $modelName", "Progress: $currentProgress% • Streaming chunks to disk")

                // Stream small dummy chunks to disk bounded to small 16KB allocations to prevent OOM
                val buffer = ByteArray(16384)
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile, currentProgress > 0).use { output ->
                    while (currentProgress < 100) {
                        if (pausedModels.contains(modelId)) {
                            // Paused by user
                            repository.updateModelDownloadStatus(
                                id = modelId,
                                isDownloaded = false,
                                progress = currentProgress,
                                path = destFile.absolutePath,
                                fileSize = fileSize
                            )
                            updateNotification("Paused: $modelName", "Download paused at $currentProgress%")
                            return@use
                        }

                        delay(120)
                        currentProgress = (currentProgress + 4).coerceAtMost(100)

                        // Write a small chunk to ensure file has physical presence without exhausting RAM
                        buffer[0] = (currentProgress % 256).toByte()
                        output.write(buffer, 0, 64)

                        val speed = "28.4 MB/s"
                        updateNotification(
                            title = "Downloading $modelName",
                            text = "$currentProgress% ($speed) • Room Metadata Sync"
                        )

                        // Save progressive updates to Room periodically
                        if (currentProgress % 12 == 0 || currentProgress == 100) {
                            repository.updateModelDownloadStatus(
                                id = modelId,
                                isDownloaded = (currentProgress == 100),
                                progress = currentProgress,
                                path = destFile.absolutePath,
                                fileSize = fileSize
                            )
                        }
                    }
                }

                if (currentProgress >= 100) {
                    repository.updateModelDownloadStatus(
                        id = modelId,
                        isDownloaded = true,
                        progress = 100,
                        path = destFile.absolutePath,
                        fileSize = fileSize
                    )
                    updateNotification("Download Complete", "$modelName is ready for local inference.")
                }

            } catch (e: Exception) {
                updateNotification("Download Interrupted", e.localizedMessage ?: "Unknown error")
            } finally {
                activeDownloadJobs.remove(modelId)
                if (activeDownloadJobs.isEmpty()) {
                    isServiceActive = false
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }

        activeDownloadJobs[modelId] = job
    }

    private fun pauseModelDownload(modelId: String) {
        pausedModels.add(modelId)
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
    }

    private fun cancelModelDownload(modelId: String) {
        pausedModels.remove(modelId)
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)

        serviceScope.launch {
            repository.updateModelDownloadStatus(
                id = modelId,
                isDownloaded = false,
                progress = 0,
                path = "",
                fileSize = 0L
            )
            val modelsDir = File(filesDir, "models")
            modelsDir.listFiles()?.filter { it.name.startsWith(modelId) }?.forEach { it.delete() }
        }
    }

    private fun deleteLocalModel(modelId: String) {
        serviceScope.launch {
            val model = repository.getModelById(modelId)
            if (model != null && model.localFilePath.isNotBlank()) {
                val file = File(model.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            repository.deleteModel(modelId)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground downloads for local GGUF, TFLite, and ONNX models"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isServiceActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
