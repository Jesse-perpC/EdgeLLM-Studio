package com.perpcorp.edgellm.service

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
import com.perpcorp.edgellm.MainActivity
import com.perpcorp.edgellm.data.local.AppDatabase
import com.perpcorp.edgellm.data.repository.EdgeLLMRepository
import com.perpcorp.edgellm.engine.MemorySafetyManager
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
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: EdgeLLMRepository
    private lateinit var memorySafetyManager: MemorySafetyManager
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val pausedModels = ConcurrentHashMap.newKeySet<String>()

    companion object {
        const val CHANNEL_ID = "edgellm_model_download_channel"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START_DOWNLOAD = "com.perpcorp.edgellm.action.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.perpcorp.edgellm.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.perpcorp.edgellm.action.CANCEL_DOWNLOAD"
        const val ACTION_DELETE_MODEL = "com.perpcorp.edgellm.action.DELETE_MODEL"

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
                val downloadUrl = currentModel?.downloadUrl ?: ""

                if (downloadUrl.isBlank() || !downloadUrl.startsWith("http")) {
                    throw java.io.IOException("No valid HTTP download URL found for $modelName")
                }

                updateNotification("Connecting: $modelName", "Opening connection to Hugging Face / Model Hub...")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "EdgeLLM-Studio-Android/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP Error ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw java.io.IOException("Empty server response body")
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else fileSize
                var downloadedBytes = 0L
                var lastUpdate = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                destFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(32768)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (pausedModels.contains(modelId)) {
                                val currentPct = ((downloadedBytes * 100) / totalBytes.coerceAtLeast(1L)).toInt()
                                repository.updateModelDownloadStatus(
                                    id = modelId,
                                    isDownloaded = false,
                                    progress = currentPct,
                                    path = destFile.absolutePath,
                                    fileSize = totalBytes
                                )
                                updateNotification("Paused: $modelName", "Download paused at $currentPct%")
                                return@launch
                            }

                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            bytesSinceLastUpdate += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 500) {
                                val elapsedSec = (now - lastUpdate) / 1000.0
                                val mbps = if (elapsedSec > 0) (bytesSinceLastUpdate / (1024.0 * 1024.0)) / elapsedSec else 0.0
                                val currentProgress = ((downloadedBytes * 100) / totalBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
                                val speedStr = String.format(java.util.Locale.US, "%.1f MB/s", mbps)
                                val downloadedMb = downloadedBytes / (1024 * 1024)
                                val totalMb = totalBytes / (1024 * 1024)

                                updateNotification(
                                    title = "Downloading $modelName",
                                    text = "$currentProgress% ($downloadedMb/$totalMb MB) • $speedStr"
                                )

                                repository.updateModelDownloadStatus(
                                    id = modelId,
                                    isDownloaded = false,
                                    progress = currentProgress,
                                    path = destFile.absolutePath,
                                    fileSize = totalBytes
                                )

                                lastUpdate = now
                                bytesSinceLastUpdate = 0L
                            }
                        }
                    }
                }

                repository.updateModelDownloadStatus(
                    id = modelId,
                    isDownloaded = true,
                    progress = 100,
                    path = destFile.absolutePath,
                    fileSize = destFile.length()
                )
                updateNotification("Download Complete", "$modelName (${destFile.length() / (1024 * 1024)} MB) is ready for local inference.")

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
