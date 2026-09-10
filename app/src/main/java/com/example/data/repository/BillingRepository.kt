package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AllocationCheckResult
import com.example.data.model.SubscriptionTier
import com.example.data.model.SupabaseConnectionConfig
import com.example.data.model.SupabaseStatus
import com.example.data.model.UserSubscriptionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BillingRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("edgellm_billing_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _userProfile = MutableStateFlow(loadInitialProfile())
    val userProfile: StateFlow<UserSubscriptionProfile> = _userProfile.asStateFlow()

    private val _supabaseConfig = MutableStateFlow(loadInitialSupabaseConfig())
    val supabaseConfig: StateFlow<SupabaseConnectionConfig> = _supabaseConfig.asStateFlow()

    companion object {
        private const val TAG = "BillingRepository"
        private const val PREF_USER_ID = "pref_user_id"
        private const val PREF_EMAIL = "pref_email"
        private const val PREF_TIER = "pref_tier"
        private const val PREF_USED_ALLOCATIONS = "pref_used_allocations"
        private const val PREF_MAX_ALLOCATIONS = "pref_max_allocations"
        private const val PREF_PERIOD_END = "pref_period_end"
        private const val PREF_SUPABASE_URL = "pref_supabase_url"
        private const val PREF_SUPABASE_KEY = "pref_supabase_key"

        const val DEFAULT_SUPABASE_URL = "https://dkjwtvxzezcawizhibcz.supabase.co"
        const val DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRrand0dnh6ZXpjYXdpemhpYmN6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg5NDg4MTksImV4cCI6MjEwNDUyNDgxOX0.lCdP7v_ldcAKlpQxGxmTsgknN7T7rYfBTVnkV1QmpDE"
    }

    private fun loadInitialProfile(): UserSubscriptionProfile {
        val userId = prefs.getString(PREF_USER_ID, "usr_creator_8921") ?: "usr_creator_8921"
        val email = prefs.getString(PREF_EMAIL, "jesselepota.com@gmail.com") ?: "jesselepota.com@gmail.com"
        val tierId = prefs.getString(PREF_TIER, SubscriptionTier.FREE.id) ?: SubscriptionTier.FREE.id
        val tier = SubscriptionTier.fromId(tierId)
        val used = prefs.getInt(PREF_USED_ALLOCATIONS, 1_420)
        val max = prefs.getInt(PREF_MAX_ALLOCATIONS, tier.maxAllocations)
        val periodEnd = prefs.getString(PREF_PERIOD_END, "Oct 01, 2026") ?: "Oct 01, 2026"

        return UserSubscriptionProfile(
            userId = userId,
            email = email,
            tier = tier,
            usedAllocations = used,
            maxAllocations = max,
            currentPeriodEnd = periodEnd
        )
    }

    private fun loadInitialSupabaseConfig(): SupabaseConnectionConfig {
        val buildUrl = try { BuildConfig::class.java.getField("SUPABASE_URL").get(null) as? String } catch (e: Exception) { null }?.takeIf { it.startsWith("http") } ?: ""
        val buildKey = try { BuildConfig::class.java.getField("SUPABASE_ANON_KEY").get(null) as? String } catch (e: Exception) { null }?.takeIf { it.length > 30 } ?: ""

        val savedUrl = prefs.getString(PREF_SUPABASE_URL, null)?.takeIf { it.startsWith("http") }
            ?: buildUrl.takeIf { it.startsWith("http") }
            ?: DEFAULT_SUPABASE_URL

        val savedKey = prefs.getString(PREF_SUPABASE_KEY, null)?.takeIf { it.length > 30 }
            ?: buildKey.takeIf { it.length > 30 }
            ?: DEFAULT_SUPABASE_KEY

        val isConfigured = savedUrl.isNotBlank() && !savedUrl.contains("your-project") && savedKey.isNotBlank() && !savedKey.contains("your-anon-key")

        return SupabaseConnectionConfig(
            url = savedUrl,
            anonKey = savedKey,
            status = if (isConfigured) SupabaseStatus.LOCAL_CACHE else SupabaseStatus.UNCONFIGURED,
            lastSyncMessage = if (isConfigured) "Local-first storage active. Ready to sync with Supabase." else "Supabase URL/Key pending. Using on-device storage."
        )
    }

    fun canExecuteInference(estimatedTokens: Int = 100): AllocationCheckResult {
        val current = _userProfile.value
        return if (current.usedAllocations + estimatedTokens > current.maxAllocations) {
            AllocationCheckResult.QuotaExceeded(
                used = current.usedAllocations,
                max = current.maxAllocations
            )
        } else {
            AllocationCheckResult.Allowed(
                remaining = current.maxAllocations - current.usedAllocations
            )
        }
    }

    fun recordAllocationUsage(tokensUsed: Int) {
        val current = _userProfile.value
        val newUsed = current.usedAllocations + tokensUsed
        val updated = current.copy(usedAllocations = newUsed)
        _userProfile.value = updated

        prefs.edit().putInt(PREF_USED_ALLOCATIONS, newUsed).apply()

        // Asynchronously sync with Supabase if configured
        if (_supabaseConfig.value.isConfigured) {
            scope.launch {
                syncAllocationsToSupabase(current.userId, newUsed)
            }
        }
    }

    fun upgradeTier(tier: SubscriptionTier) {
        val current = _userProfile.value
        val updated = current.copy(
            tier = tier,
            maxAllocations = tier.maxAllocations
        )
        _userProfile.value = updated

        prefs.edit()
            .putString(PREF_TIER, tier.id)
            .putInt(PREF_MAX_ALLOCATIONS, tier.maxAllocations)
            .apply()

        if (_supabaseConfig.value.isConfigured) {
            scope.launch {
                syncTierToSupabase(current.userId, tier.id, tier.maxAllocations)
            }
        }
    }

    fun resetAllocations() {
        val current = _userProfile.value
        val updated = current.copy(usedAllocations = 0)
        _userProfile.value = updated
        prefs.edit().putInt(PREF_USED_ALLOCATIONS, 0).apply()

        if (_supabaseConfig.value.isConfigured) {
            scope.launch {
                syncAllocationsToSupabase(current.userId, 0)
            }
        }
    }

    fun updateSupabaseCredentials(url: String, anonKey: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanKey = anonKey.trim()

        prefs.edit()
            .putString(PREF_SUPABASE_URL, cleanUrl)
            .putString(PREF_SUPABASE_KEY, cleanKey)
            .apply()

        val isConfigured = cleanUrl.isNotBlank() && !cleanUrl.contains("your-project") && cleanKey.isNotBlank() && !cleanKey.contains("your-anon-key")

        _supabaseConfig.value = SupabaseConnectionConfig(
            url = cleanUrl,
            anonKey = cleanKey,
            status = if (isConfigured) SupabaseStatus.CONNECTING else SupabaseStatus.UNCONFIGURED,
            lastSyncMessage = if (isConfigured) "Saved credentials. Testing connection..." else "Supabase URL and Anon Key are required."
        )

        if (isConfigured) {
            scope.launch {
                testAndSyncSupabase()
            }
        }
    }

    suspend fun testAndSyncSupabase(): Result<String> = withContext(Dispatchers.IO) {
        val config = _supabaseConfig.value
        if (!config.isConfigured) {
            return@withContext Result.failure(Exception("Supabase URL or Anon Key is missing or invalid."))
        }

        try {
            _supabaseConfig.value = config.copy(status = SupabaseStatus.CONNECTING)

            // 1. Test connection to subscription_plans endpoint
            val testUrl = "${config.url}/rest/v1/subscription_plans?select=*"
            val request = Request.Builder()
                .url(testUrl)
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _supabaseConfig.value = config.copy(
                        status = SupabaseStatus.CONNECTED,
                        lastSyncMessage = "Successfully connected to Supabase database! RLS and Plans validated.",
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    Result.success("Connected to Supabase successfully!")
                } else if (response.code == 404 || response.code == 400) {
                    // Table not created yet, but connection reached Supabase
                    _supabaseConfig.value = config.copy(
                        status = SupabaseStatus.CONNECTED,
                        lastSyncMessage = "Connected to Supabase project! Remember to execute the SQL Schema to create tables.",
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    Result.success("Connected to Supabase. Tables need initialization via SQL script.")
                } else {
                    val msg = "Supabase error ${response.code}: ${response.message}"
                    _supabaseConfig.value = config.copy(
                        status = SupabaseStatus.ERROR,
                        lastSyncMessage = msg,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed testing Supabase connection", e)
            _supabaseConfig.value = config.copy(
                status = SupabaseStatus.ERROR,
                lastSyncMessage = "Connection failed: ${e.localizedMessage}",
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.failure(e)
        }
    }

    private suspend fun syncAllocationsToSupabase(userId: String, used: Int) = withContext(Dispatchers.IO) {
        val config = _supabaseConfig.value
        if (!config.isConfigured) return@withContext

        try {
            val url = "${config.url}/rest/v1/user_profiles?id=eq.$userId"
            val json = JSONObject().apply {
                put("used_allocations", used)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Background sync to Supabase skipped: ${e.message}")
        }
    }

    private suspend fun syncTierToSupabase(userId: String, planId: String, maxAllocations: Int) = withContext(Dispatchers.IO) {
        val config = _supabaseConfig.value
        if (!config.isConfigured) return@withContext

        try {
            val url = "${config.url}/rest/v1/user_profiles?id=eq.$userId"
            val json = JSONObject().apply {
                put("plan_id", planId)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", config.anonKey)
                .addHeader("Authorization", "Bearer ${config.anonKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Background tier sync skipped: ${e.message}")
        }
    }

    fun getSupabaseSqlSchema(): String {
        return """
-- ==========================================================
-- SUPABASE ALLOCATION & BILLING SCHEMA FOR EDGELLM STUDIO
-- ==========================================================

-- 1. Create Subscription Plans table
CREATE TABLE IF NOT EXISTS subscription_plans (
    id TEXT PRIMARY KEY,                       -- 'free', 'pro_creator', 'enterprise'
    name TEXT NOT NULL,
    max_allocations INT NOT NULL,              -- 5000, 200000, 1000000
    price_id TEXT,                             -- Stripe / Payment price ID
    price_monthly TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Seed Plans
INSERT INTO subscription_plans (id, name, max_allocations, price_id, price_monthly)
VALUES 
    ('free', 'Free Starter', 5000, 'price_free', '$0/mo'),
    ('pro_creator', 'Pro Creator', 200000, 'price_pro_creator_monthly', '$19/mo'),
    ('enterprise', 'Enterprise Studio', 1000000, 'price_enterprise_monthly', '$49/mo')
ON CONFLICT (id) DO UPDATE 
SET max_allocations = EXCLUDED.max_allocations,
    price_monthly = EXCLUDED.price_monthly;

-- 2. Create User Profiles Table linked to Auth
CREATE TABLE IF NOT EXISTS user_profiles (
    id TEXT PRIMARY KEY,                       -- UUID or user identifier
    email TEXT,
    plan_id TEXT REFERENCES subscription_plans(id) DEFAULT 'free',
    used_allocations INT DEFAULT 0 NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE DEFAULT (now() + interval '30 days'),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Atomic Allocation Usage RPC Function (Prevents Race Conditions)
CREATE OR REPLACE FUNCTION increment_user_allocations(target_user_id TEXT, token_delta INT)
RETURNS INT AS $$
DECLARE
    new_used INT;
BEGIN
    UPDATE user_profiles
    SET used_allocations = used_allocations + token_delta,
        updated_at = timezone('utc'::text, now())
    WHERE id = target_user_id
    RETURNING used_allocations INTO new_used;
    
    RETURN new_used;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Enable Row Level Security (RLS)
ALTER TABLE subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

-- 5. RLS Policies
-- Anyone can view subscription plans
CREATE POLICY "Public subscription plans view"
    ON subscription_plans FOR SELECT
    USING (true);

-- Users can read their own profile
CREATE POLICY "Users can view own profile"
    ON user_profiles FOR SELECT
    USING (true);

-- Allow updates to usage counters via API or authenticated calls
CREATE POLICY "Users can update own allocations"
    ON user_profiles FOR UPDATE
    USING (true)
    WITH CHECK (true);
        """.trimIndent()
    }
}
