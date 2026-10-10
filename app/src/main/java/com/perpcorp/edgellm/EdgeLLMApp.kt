package com.perpcorp.edgellm

import android.app.Application
import android.util.Log
import com.perpcorp.edgellm.cloudhub.DeviceAutoConfig
import com.perpcorp.edgellm.cloudhub.EncryptedCloudHubStore

/**
 * Application entry point. Runs the Cloud Hub first-launch device
 * auto-configuration once (RAM-tier → default context/max-tokens seed).
 * Purely local, idempotent, and a no-op on every subsequent start.
 */
class EdgeLLMApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val store = EncryptedCloudHubStore.get(this)
            DeviceAutoConfig.applyOnce(this, store) { _, _ ->
                // MainViewModel picks the seeded values up from the same store
                // on init; nothing to push here (no ViewModel at this layer).
            }
        } catch (e: Exception) {
            Log.w(TAG, "Device autoconfig skipped", e)
        }
    }

    companion object {
        private const val TAG = "EdgeLLMApp"
    }
}
