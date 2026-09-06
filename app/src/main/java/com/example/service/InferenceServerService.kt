package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.api.ApiServerConfig
import com.example.api.ApiServerStats
import com.example.api.OllamaInferenceServer
import com.example.data.local.AppDatabase
import com.example.data.model.ComputeBackend
import com.example.data.model.HardwareAccelerationSettings
import com.example.data.model.ModelSpec
import com.example.data.model.PowerProfile
import com.example.data.repository.EdgeLLMRepository
import com.example.engine.LocalInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class InferenceServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    companion object {
        private const val TAG = "InferenceServerService"
        const val CHANNEL_ID = "edgellm_inference_server_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START_SERVER = "com.example.action.START_API_SERVER"
        const val ACTION_STOP_SERVER = "com.example.action.STOP_API_SERVER"
        const val ACTION_TOGGLE_SERVER = "com.example.action.TOGGLE_API_SERVER"

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_BIND_LAN = "extra_bind_lan"

        @Volatile
        var isServiceActive = false
            private set

        private val _serviceServer = MutableStateFlow<OllamaInferenceServer?>(null)
        val serviceServer: StateFlow<OllamaInferenceServer?> = _serviceServer.asStateFlow()

        fun startService(context: Context, port: Int = 11434, bindToLan: Boolean = true) {
            val intent = Intent(context, InferenceServerService::class.java).apply {
                action = ACTION_START_SERVER
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_BIND_LAN, bindToLan)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, InferenceServerService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        setupThermalAndBatteryWatchdog()
        isServiceActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVER
        when (action) {
            ACTION_START_SERVER -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 11434) ?: 11434
                val bindToLan = intent?.getBooleanExtra(EXTRA_BIND_LAN, true) ?: true
                startInferenceServer(port, bindToLan)
            }
            ACTION_STOP_SERVER -> {
                stopInferenceServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_SERVER -> {
                val current = _serviceServer.value
                if (current != null && current.serverStats.value.isRunning) {
                    stopInferenceServer()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    startInferenceServer(11434, true)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInferenceServer(port: Int, bindToLan: Boolean) {
        val db = AppDatabase.getInstance(applicationContext)
        val repository = EdgeLLMRepository(db)
        val inferenceEngine = LocalInferenceEngine()

        var server = _serviceServer.value
        if (server == null) {
            server = OllamaInferenceServer(
                context = applicationContext,
                inferenceEngine = inferenceEngine,
                modelProvider = {
                    runBlocking(Dispatchers.IO) {
                        repository.localModels.firstOrNull() ?: emptyList()
                    }
                },
                activeModelProvider = {
                    runBlocking(Dispatchers.IO) {
                        repository.localModels.firstOrNull()?.firstOrNull { it.isActive }
                            ?: repository.localModels.firstOrNull()?.firstOrNull { it.isDownloaded }
                    }
                },
                accelerationSettingsProvider = {
                    HardwareAccelerationSettings(
                        computeBackend = ComputeBackend.NPU_NNAPI,
                        powerProfile = PowerProfile.BALANCED,
                        threadCount = 4
                    )
                }
            )
            _serviceServer.value = server
        }

        val success = server.start(port, bindToLan)
        if (success) {
            val notification = buildForegroundNotification(server.serverStats.value)
            startForeground(NOTIFICATION_ID, notification)
            monitorServerStats(server)
        } else {
            Log.e(TAG, "Could not start server on port $port")
            stopSelf()
        }
    }

    private fun stopInferenceServer() {
        _serviceServer.value?.stop()
        isServiceActive = false
    }

    private fun monitorServerStats(server: OllamaInferenceServer) {
        serviceScope.launch {
            server.serverStats.collect { stats ->
                if (stats.isRunning) {
                    val notification = buildForegroundNotification(stats)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeLLM:InferenceServerWakeLock").apply {
                acquire(24 * 60 * 60 * 1000L) // 24h max timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EdgeLLM:InferenceServerWifiLock")?.apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring wakelock/wifilock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed releasing locks: ${e.message}")
        }
    }

    private fun setupThermalAndBatteryWatchdog() {
        // Battery level listener
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (level >= 0 && scale > 0) {
                        val pct = (level * 100) / scale
                        val isLow = pct <= 15 && !isCharging
                        _serviceServer.value?.serverStats?.let { statsFlow ->
                            // Update internal stats
                        }
                    }
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Thermal status listener on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                val isSevere = status >= PowerManager.THERMAL_STATUS_SEVERE
                if (isSevere) {
                    Log.w(TAG, "Thermal throttle detected! Status level: $status")
                }
            }
            try {
                powerManager.addThermalStatusListener(thermalListener!!)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add thermal listener: ${e.message}")
            }
        }
    }

    private fun buildForegroundNotification(stats: ApiServerStats): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, InferenceServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endpointText = if (stats.lanIp != "127.0.0.1") "http://${stats.lanIp}:${stats.port}" else "http://localhost:${stats.port}"
        val content = "Listening on $endpointText • Served ${stats.totalRequestsServed} reqs (${stats.totalTokensGenerated} tokens)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EdgeLLM Ollama API Server Running")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EdgeLLM API Inference Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background notifications while serving local LLM inference API to other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopInferenceServer()
        releaseLocks()

        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
        }

        serviceScope.cancel()
        isServiceActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
