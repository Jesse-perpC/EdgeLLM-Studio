package com.perpcorp.edgellm.cloudhub

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed [CloudHubStore]: keys encrypted with AES-256-GCM, key
 * itself in the Android Keystore (StrongBox/TEE when available).
 *
 * This is where Cloud Hub API keys live — never in plain SharedPreferences,
 * never in logs, never in Room. If the Keystore is unavailable (broken
 * firmware, Robolectric unit tests), it degrades to a plain private prefs
 * file and logs a warning instead of crashing.
 */
class EncryptedCloudHubStore private constructor(
    private val prefs: SharedPreferences,
    private val encrypted: Boolean,
) : CloudHubStore {

    companion object {
        private const val TAG = "CloudHubStore"
        private const val FILE = "edgellm_cloudhub"

        @Volatile
        private var instance: EncryptedCloudHubStore? = null

        fun get(context: Context): EncryptedCloudHubStore {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(context: Context): EncryptedCloudHubStore {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                Log.i(TAG, "Cloud Hub store: encrypted (Keystore-backed)")
                EncryptedCloudHubStore(prefs, encrypted = true)
            } catch (e: Exception) {
                Log.w(TAG, "Keystore unavailable, using private prefs fallback: ${e.message}")
                val prefs = context.getSharedPreferences(
                    "${FILE}_fallback",
                    Context.MODE_PRIVATE,
                )
                EncryptedCloudHubStore(prefs, encrypted = false)
            }
        }

        /** Test/fallback entry point with an explicit prefs file. */
        fun wrap(prefs: SharedPreferences): EncryptedCloudHubStore =
            EncryptedCloudHubStore(prefs, encrypted = false)
    }

    /** False only on the degraded fallback path — surface in UI. */
    fun isEncrypted(): Boolean = encrypted

    override fun getString(key: String, default: String?): String? =
        try {
            prefs.getString(key, default)
        } catch (_: Exception) {
            default
        }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getFloat(key: String, default: Float): Float =
        try {
            prefs.getFloat(key, default)
        } catch (_: Exception) {
            default
        }

    override fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int =
        try {
            prefs.getInt(key, default)
        } catch (_: Exception) {
            default
        }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        try {
            prefs.getBoolean(key, default)
        } catch (_: Exception) {
            default
        }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
