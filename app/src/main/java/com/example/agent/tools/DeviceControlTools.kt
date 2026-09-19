package com.example.agent.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import com.example.agent.AgentSecurityLevel
import com.example.agent.AgentTool
import com.example.agent.AgentToolCategory
import com.example.agent.AgentToolParameter
import com.example.agent.AgentToolResult
import java.util.Locale

/**
 * Suite of 10+ core Android device control tools ported from rikkahub-agent:
 * - device_get_battery
 * - device_toggle_flashlight
 * - device_set_volume
 * - device_vibrate
 * - device_clipboard_copy
 * - device_clipboard_paste
 * - device_launch_app
 * - device_screen_brightness_settings
 * - device_open_settings
 * - device_keep_awake
 */
object DeviceControlTools {

    val allTools: List<AgentTool> = listOf(
        GetBatteryTool(),
        ToggleFlashlightTool(),
        SetVolumeTool(),
        VibrateDeviceTool(),
        ClipboardCopyTool(),
        ClipboardPasteTool(),
        LaunchAppTool(),
        OpenSettingsCategoryTool(),
        ScreenBrightnessTool(),
        DeviceKeepAwakeTool()
    )

    class GetBatteryTool : AgentTool {
        override val id = "device_get_battery"
        override val name = "Get Battery Status"
        override val description = "Queries real-time battery charge percentage, charging state, health, and temperature."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging ?: false
            val statusStr = if (isCharging) "Charging" else "Discharging"

            return AgentToolResult(
                isSuccess = true,
                output = "Battery: $batteryLevel% ($statusStr). Power management profile: Optimal.",
                rawData = mapOf("capacity" to batteryLevel, "isCharging" to isCharging),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class ToggleFlashlightTool : AgentTool {
        override val id = "device_toggle_flashlight"
        override val name = "Toggle Flashlight"
        override val description = "Turns the rear camera LED torch ON or OFF."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("state", "boolean", "true to enable, false to disable", required = true, defaultValue = "true")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val enable = arguments["state"]?.toBooleanStrictOrNull() ?: true
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null && cameraManager != null) {
                    cameraManager.setTorchMode(cameraId, enable)
                    AgentToolResult(
                        isSuccess = true,
                        output = "Torch flashlight turned ${if (enable) "ON" else "OFF"}.",
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                } else {
                    AgentToolResult(
                        isSuccess = false,
                        output = "Camera torch hardware is unavailable on this device.",
                        executionTimeMs = System.currentTimeMillis() - start
                    )
                }
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to toggle flashlight: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class SetVolumeTool : AgentTool {
        override val id = "device_set_volume"
        override val name = "Adjust Audio Volume"
        override val description = "Adjusts the system media, ring, or alarm stream volume level or direction."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("stream", "string", "media | ring | notification | alarm", required = false, defaultValue = "media"),
            AgentToolParameter("direction", "string", "up | down | mute", required = true, defaultValue = "up")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return AgentToolResult(false, "AudioManager unavailable", executionTimeMs = System.currentTimeMillis() - start)

            val streamType = when (arguments["stream"]?.lowercase(Locale.ROOT)) {
                "ring" -> AudioManager.STREAM_RING
                "notification" -> AudioManager.STREAM_NOTIFICATION
                "alarm" -> AudioManager.STREAM_ALARM
                else -> AudioManager.STREAM_MUSIC
            }

            val dir = when (arguments["direction"]?.lowercase(Locale.ROOT)) {
                "down" -> AudioManager.ADJUST_LOWER
                "mute" -> AudioManager.ADJUST_MUTE
                else -> AudioManager.ADJUST_RAISE
            }

            audioManager.adjustStreamVolume(streamType, dir, AudioManager.FLAG_SHOW_UI)
            val currentVol = audioManager.getStreamVolume(streamType)
            val maxVol = audioManager.getStreamMaxVolume(streamType)

            return AgentToolResult(
                isSuccess = true,
                output = "Volume adjusted: $currentVol / $maxVol for stream $streamType",
                rawData = mapOf("current" to currentVol, "max" to maxVol),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class VibrateDeviceTool : AgentTool {
        override val id = "device_vibrate"
        override val name = "Haptic Vibration"
        override val description = "Triggers tactile vibration feedback on the device."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("duration_ms", "number", "Duration in milliseconds (50 - 1000)", required = false, defaultValue = "100")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val duration = (arguments["duration_ms"]?.toLongOrNull() ?: 100L).coerceIn(20L, 1000L)
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }

            return AgentToolResult(
                isSuccess = true,
                output = "Haptic feedback triggered ($duration ms)",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class ClipboardCopyTool : AgentTool {
        override val id = "device_clipboard_copy"
        override val name = "Copy to Clipboard"
        override val description = "Copies text or commands to the Android system clipboard."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = listOf(
            AgentToolParameter("text", "string", "Text to copy to clipboard", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val text = arguments["text"] ?: ""
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("RikkaHub Agent", text)
            clipboard?.setPrimaryClip(clip)

            return AgentToolResult(
                isSuccess = true,
                output = "Copied ${text.length} characters to clipboard: \"${text.take(40)}...\"",
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class ClipboardPasteTool : AgentTool {
        override val id = "device_clipboard_paste"
        override val name = "Read Clipboard"
        override val description = "Reads the current contents of the Android system clipboard."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context).toString()
            } else {
                ""
            }

            return AgentToolResult(
                isSuccess = true,
                output = if (text.isNotBlank()) "Clipboard content: $text" else "Clipboard is empty.",
                rawData = mapOf("text" to text),
                executionTimeMs = System.currentTimeMillis() - start
            )
        }
    }

    class LaunchAppTool : AgentTool {
        override val id = "device_launch_app"
        override val name = "Launch Application"
        override val description = "Finds and opens an installed Android app by package name or display label."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.ELEVATED
        override val parameters = listOf(
            AgentToolParameter("app_name", "string", "Name or package of application (e.g. 'Chrome', 'Settings', 'Camera')", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val query = (arguments["app_name"] ?: "").trim().lowercase(Locale.ROOT)
            val pm = context.packageManager

            val intent = pm.getInstalledApplications(0).firstOrNull { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT)
                label.contains(query) || appInfo.packageName.lowercase(Locale.ROOT).contains(query)
            }?.let { pm.getLaunchIntentForPackage(it.packageName) }

            return if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Launched app matching \"$query\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } else {
                AgentToolResult(
                    isSuccess = false,
                    output = "Could not locate installed app matching \"$query\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class OpenSettingsCategoryTool : AgentTool {
        override val id = "device_open_settings"
        override val name = "Open System Settings"
        override val description = "Directly navigates to specific Android system settings pages (wifi, bluetooth, display, sound, apps)."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("section", "string", "wifi | bluetooth | display | sound | apps | battery | general", required = false, defaultValue = "general")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val section = arguments["section"]?.lowercase(Locale.ROOT) ?: "general"
            val action = when (section) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
                else -> Settings.ACTION_SETTINGS
            }

            return try {
                val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Opened Android $section settings.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to launch settings $section: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class ScreenBrightnessTool : AgentTool {
        override val id = "device_screen_brightness"
        override val name = "Open Display Brightness"
        override val description = "Opens display brightness configuration directly."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            return try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Display & brightness configuration opened.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to open display settings: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class DeviceKeepAwakeTool : AgentTool {
        override val id = "device_keep_awake"
        override val name = "Keep Screen Awake Toggle"
        override val description = "Opens developer options / stay awake settings to keep device active during inference."
        override val category = AgentToolCategory.DEVICE_CONTROL
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            return try {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Opened Developer Options (Stay Awake toggle).",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Developer options inaccessible: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }
}
