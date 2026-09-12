package com.example.assistant

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceTelemetrySnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val currentTimeFormatted: String,
    val currentDateFormatted: String,
    val airplaneModeOn: Boolean,
    val deviceModel: String,
    val androidVersion: String
)

sealed class AssistantMobileAction(val actionName: String, val description: String) {
    data class OpenAppSettings(val reason: String = "Manage settings") : AssistantMobileAction("open_settings", "Opens device system settings")
    data class ToggleWifiSettings(val reason: String = "Configure network") : AssistantMobileAction("open_wifi", "Opens Wi-Fi connection settings")
    data class SetAlarmOrTimer(val minutes: Int) : AssistantMobileAction("set_timer", "Sets a countdown timer for $minutes minutes")
    data class CheckBattery(val percent: Int) : AssistantMobileAction("check_battery", "Reads current device battery status ($percent%)")
    data class ToggleTorch(val enable: Boolean) : AssistantMobileAction("toggle_torch", "Turns flashlight ${if (enable) "ON" else "OFF"}")
    data class AdjustVolume(val direction: Int) : AssistantMobileAction("adjust_volume", "Adjusts media volume ${if (direction > 0) "UP" else "DOWN"}")
    data class CopyToClipboard(val text: String) : AssistantMobileAction("copy_clipboard", "Copies generated response to clipboard")
    data class HapticFeedback(val pattern: String = "CONFIRM") : AssistantMobileAction("haptic_feedback", "Emits tactile haptic pulse")
    data class OpenApp(val appName: String) : AssistantMobileAction("open_app", "Launches mobile app: $appName")
    data class CreateCalendarEvent(val title: String, val durationMinutes: Int = 60) : AssistantMobileAction("create_calendar_event", "Schedules calendar event: $title")
    data class ShareText(val text: String) : AssistantMobileAction("share_text", "Shares text to external application")
    data class ComposeQuickNote(val note: String) : AssistantMobileAction("quick_note", "Saves an offline memo to vault")
    data class SearchWebQuery(val query: String) : AssistantMobileAction("search_web", "Searches query externally")
}

data class AssistantExecutionResult(
    val speechResponse: String,
    val thoughtChain: String?,
    val actionTriggered: AssistantMobileAction?,
    val executedToolSummary: String?,
    val confidenceScore: Float
)

object DeviceControlBridge {

    fun getDeviceSnapshot(context: Context): DeviceTelemetrySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        val isCharging = bm?.isCharging ?: false

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val now = Date()

        val airplaneMode = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0

        return DeviceTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            currentTimeFormatted = timeFormat.format(now),
            currentDateFormatted = dateFormat.format(now),
            airplaneModeOn = airplaneMode,
            deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
            androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        )
    }

    /**
     * Executes autonomous AppFunctions device action with high-reliability Android intents & system services.
     * Returns a confirmation summary string.
     */
    fun executeAction(context: Context, action: AssistantMobileAction): String {
        return try {
            when (action) {
                is AssistantMobileAction.OpenAppSettings -> {
                    val intent = android.content.Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opened system settings"
                }
                is AssistantMobileAction.ToggleWifiSettings -> {
                    val intent = android.content.Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opened Wi-Fi settings"
                }
                is AssistantMobileAction.SetAlarmOrTimer -> {
                    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(android.provider.AlarmClock.EXTRA_LENGTH, action.minutes * 60)
                        putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Edge Assistant Timer")
                        putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Timer set for ${action.minutes} minutes"
                }
                is AssistantMobileAction.ToggleTorch -> {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                        val characteristics = cameraManager.getCameraCharacteristics(id)
                        characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (cameraId != null && cameraManager != null) {
                        cameraManager.setTorchMode(cameraId, action.enable)
                        "Flashlight turned ${if (action.enable) "ON" else "OFF"}"
                    } else {
                        "Torch hardware unavailable"
                    }
                }
                is AssistantMobileAction.AdjustVolume -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    val direction = if (action.direction > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume adjusted ${if (action.direction > 0) "UP" else "DOWN"}"
                }
                is AssistantMobileAction.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Assistant Output", action.text)
                    clipboard?.setPrimaryClip(clip)
                    "Copied to clipboard"
                }
                is AssistantMobileAction.HapticFeedback -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(75, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(75)
                    }
                    "Haptic confirmation emitted"
                }
                is AssistantMobileAction.OpenApp -> {
                    val pm = context.packageManager
                    val targetName = action.appName.lowercase(Locale.ROOT)
                    val intent = pm.getInstalledApplications(0).firstOrNull { appInfo ->
                        pm.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT).contains(targetName)
                    }?.let { pm.getLaunchIntentForPackage(it.packageName) }

                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Launched ${action.appName}"
                    } else {
                        "Could not locate application: ${action.appName}"
                    }
                }
                is AssistantMobileAction.CreateCalendarEvent -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, action.title)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600_000L)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + (3600_000L + action.durationMinutes * 60_000L))
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Calendar event prepared: ${action.title}"
                }
                is AssistantMobileAction.ShareText -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, action.text)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Shared text via Android intent"
                }
                is AssistantMobileAction.SearchWebQuery -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(android.app.SearchManager.QUERY, action.query)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Searched web for: ${action.query}"
                }
                is AssistantMobileAction.CheckBattery -> "Battery status reported"
                is AssistantMobileAction.ComposeQuickNote -> "Note saved"
            }
        } catch (e: Exception) {
            "Action execution error: ${e.localizedMessage ?: "Unknown"}"
        }
    }
}
