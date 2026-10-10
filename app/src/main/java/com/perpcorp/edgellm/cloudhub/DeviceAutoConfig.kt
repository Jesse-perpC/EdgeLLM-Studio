package com.perpcorp.edgellm.cloudhub

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * First-launch RAM→context/token seeder ported from PrivateLM
 * (`device_info_service.dart` tiers + `main.dart` `_autoConfigureForDevice`).
 *
 * EdgeLLM's `SiliconGovernorManager` already owns threads/GPU policy — this
 * only seeds the *generation defaults* (context window + max tokens) once, so
 * low-RAM phones don't OOM on first run and flagships aren't artificially
 * capped. Never overwrites user-chosen values: runs once ever
 * ([CloudHubKeys.AUTOCONFIG_DONE]).
 */
object DeviceAutoConfig {

    private const val TAG = "DeviceAutoConfig"

    data class Tier(
        val name: String,
        val contextSize: Int,
        val maxTokens: Int,
        val maxSafeContext: Int,
        val maxSafeTokens: Int,
    )

    fun tierFor(totalRamBytes: Long): Tier {
        val gb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        return when {
            gb <= 4.0 -> Tier("low", 1024, 256, 2048, 512)
            gb <= 6.0 -> Tier("mid", 2048, 512, 4096, 1024)
            gb <= 8.0 -> Tier("high", 4096, 1024, 8192, 2048)
            gb <= 12.0 -> Tier("ultra", 4096, 2048, 8192, 4096)
            else -> Tier("flagship", 8192, 4096, 16384, 4096)
        }
    }

    data class Applied(val tier: Tier, val totalRamGb: Double, val firstRun: Boolean)

    /**
     * Seed [store] + [apply] (e.g. MainViewModel generation params) once.
     * Subsequent launches are no-ops. Safe to call on every start.
     */
    fun applyOnce(
        context: Context,
        store: CloudHubStore,
        apply: (contextSize: Int, maxTokens: Int) -> Unit,
    ): Applied {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(mem)
        val tier = tierFor(mem.totalMem)
        val gb = mem.totalMem / (1024.0 * 1024.0 * 1024.0)

        if (store.getBoolean(CloudHubKeys.AUTOCONFIG_DONE, false)) {
            return Applied(tier, gb, firstRun = false)
        }
        store.putInt(CloudHubKeys.CONTEXT_SIZE, tier.contextSize)
        store.putInt(CloudHubKeys.MAX_TOKENS, tier.maxTokens)
        store.putBoolean(CloudHubKeys.AUTOCONFIG_DONE, true)
        runCatching { apply(tier.contextSize, tier.maxTokens) }
        Log.i(
            TAG,
            "First launch: context=${tier.contextSize}, maxTokens=${tier.maxTokens} " +
                "for ${"%.1f".format(gb)}GB RAM (tier=${tier.name})",
        )
        return Applied(tier, gb, firstRun = true)
    }
}
