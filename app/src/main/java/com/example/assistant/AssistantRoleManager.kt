package com.example.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AssistantRoleManager(private val context: Context) {

    private val _isDefaultAssistant = MutableStateFlow(false)
    val isDefaultAssistant: StateFlow<Boolean> = _isDefaultAssistant.asStateFlow()

    init {
        checkAssistantStatus()
    }

    fun checkAssistantStatus(): Boolean {
        val packageName = context.packageName

        // 1. Check VoiceInteractionService active state
        val isVoiceServiceActive = try {
            val component = ComponentName(context, EdgeVoiceInteractionService::class.java)
            android.service.voice.VoiceInteractionService.isActiveService(context, component)
        } catch (e: Exception) {
            false
        }

        // 2. Check Settings.Secure "voice_interaction_service"
        val voiceServiceSetting = try {
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        } catch (e: Exception) {
            null
        }

        // 3. Check Settings.Secure "assistant"
        val assistantSetting = try {
            Settings.Secure.getString(context.contentResolver, "assistant")
        } catch (e: Exception) {
            null
        }

        // 4. Check RoleManager if supported (Android 10+)
        val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            try {
                roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val isDefault = isVoiceServiceActive ||
                isRoleHeld ||
                (voiceServiceSetting?.contains(packageName) == true) ||
                (assistantSetting?.contains(packageName) == true)

        _isDefaultAssistant.value = isDefault
        return isDefault
    }

    /**
     * Attempts to open the system assistant configuration screen.
     * Tries specialized Assist / Voice Input settings first, then Default Apps settings,
     * with graceful fallbacks and user feedback.
     */
    fun openSystemAssistantSettings(targetContext: Context = context) {
        val candidates = mutableListOf<Intent>()

        // 1. Primary: Voice Input & Assist settings (stock Android / Pixel / Motorola)
        candidates.add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        // 2. Secondary: Default apps settings (Samsung One UI, Xiaomi HyperOS, OnePlus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            candidates.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // 3. Fallback: Application details settings
        candidates.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${targetContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        // 4. Ultimate Fallback: System Settings root
        candidates.add(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        var launched = false
        for (intent in candidates) {
            try {
                targetContext.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
                // Try next candidate
            }
        }

        if (launched) {
            Toast.makeText(
                targetContext,
                "Select EdgeLLM under 'Default digital assistant app'",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                targetContext,
                "Please navigate to Android Settings > Apps > Default apps > Digital assistant app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Opens the device's Default Applications settings screen directly
     * (especially useful for Samsung Galaxy and Xiaomi MIUI/HyperOS devices).
     */
    fun openDefaultAppsSettings(targetContext: Context = context) {
        val candidates = mutableListOf<Intent>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            candidates.add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        candidates.add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        candidates.add(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        for (intent in candidates) {
            try {
                targetContext.startActivity(intent)
                Toast.makeText(
                    targetContext,
                    "Tap 'Digital assistant app' and choose EdgeLLM",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Direct test launch for the Assistant Overlay UI.
     */
    fun launchAssistantOverlay(targetContext: Context = context, query: String? = null) {
        val intent = Intent(targetContext, AssistantOverlayActivity::class.java).apply {
            query?.let { putExtra("query", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            targetContext.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(targetContext, "Unable to launch overlay: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
