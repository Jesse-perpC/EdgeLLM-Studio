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
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.api.ApiServerStats
import com.example.api.InferenceServerManager
import com.example.api.OllamaInferenceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InferenceServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var monitorJob: Job? = null

    private lateinit var server: OllamaInferenceServer

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

        fun startService(context: Context, port: Int = 11434, bindToLan: Boolean = true) {
            try {
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
            } catch (e: Throwable) {
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, InferenceServerService::class.java).apply {
                    action = ACTION_STOP_SERVER
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not stop foreground service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        server = InferenceServerManager.getInstance(applicationContext)
        createNotificationChannel()
        acquireLocks()
        setupThermalAndBatteryWatchdog()
        isServiceActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVER

        // Always satisfy Android foreground service requirement immediately
        try {
            val initialStats = server.serverStats.value
            val notification = buildForegroundNotification(initialStats)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed calling startForeground: ${e.message}")
        }

        when (action) {
            ACTION_STOP_SERVER -> {
                serviceScope.launch {
                    server.stop()
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Throwable) {}
                    stopSelf()
                }
            }
            ACTION_TOGGLE_SERVER -> {
                serviceScope.launch {
                    if (server.serverStats.value.isRunning) {
                        server.stop()
                        try {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } catch (_: Throwable) {}
                        stopSelf()
                    } else {
                        server.start(11434, true)
                        monitorServerStats()
                    }
                }
            }
            ACTION_START_SERVER -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 11434) ?: 11434
                val bindToLan = intent?.getBooleanExtra(EXTRA_BIND_LAN, true) ?: true
                serviceScope.launch {
                    if (!server.serverStats.value.isRunning) {
                        server.start(port, bindToLan)
                    }
                    monitorServerStats()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun monitorServerStats() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            server.serverStats.collect { stats ->
                if (stats.isRunning) {
                    try {
                        val notification = buildForegroundNotification(stats)
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        manager?.notify(NOTIFICATION_ID, notification)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed updating notification: ${e.message}")
                    }
                }
            }
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeLLM:InferenceServerWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24h max timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL, "EdgeLLM:InferenceServerWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed acquiring wakelock/wifilock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Throwable) {
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
                        if (isLow && server.serverStats.value.isRunning) {
                            Log.w(TAG, "Battery level low ($pct%). Preserving device power.")
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Throwable) {
            Log.w(TAG, "Could not register battery receiver: ${e.message}")
        }

        // Thermal status listener on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                val isSevere = status >= PowerManager.THERMAL_STATUS_SEVERE
                if (isSevere) {
                    Log.w(TAG, "Thermal throttle detected! Status level: $status")
                }
            }
            try {
                if (powerManager != null && thermalListener != null) {
                    powerManager.addThermalStatusListener(thermalListener!!)
                }
            } catch (e: Throwable) {
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
        val content = if (stats.isRunning) {
            "Listening on $endpointText • Served ${stats.totalRequestsServed} reqs (${stats.totalTokensGenerated} tokens)"
        } else {
            "Server initialized on port ${stats.port}"
        }

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
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "EdgeLLM API Inference Server",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background notifications while serving local LLM inference API to other apps"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create notification channel: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()

        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Throwable) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            try {
                powerManager?.removeThermalStatusListener(thermalListener!!)
            } catch (_: Throwable) {}
        }

        monitorJob?.cancel()
        serviceScope.cancel()
        isServiceActive = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
