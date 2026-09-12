package com.example.data.model

enum class SubscriptionTier(
    val id: String,
    val displayName: String,
    val maxAllocations: Int,
    val priceMonthly: String,
    val badgeLabel: String,
    val description: String,
    val featureList: List<String>
) {
    FREE(
        id = "free",
        displayName = "Free Starter",
        maxAllocations = 5_000,
        priceMonthly = "$0/mo",
        badgeLabel = "Starter",
        description = "Standard local on-device inference with starter allocation pool.",
        featureList = listOf(
            "5,000 monthly inference tokens",
            "On-device GGUF & ONNX execution",
            "Standard 4-thread CPU inference",
            "Local chat history storage"
        )
    ),
    PRO_CREATOR(
        id = "pro_creator",
        displayName = "Pro Creator",
        maxAllocations = 200_000,
        priceMonthly = "$19/mo",
        badgeLabel = "PRO CREATOR",
        description = "High-volume allocation tier with Cloud Assist and full silicon acceleration.",
        featureList = listOf(
            "200,000 monthly allocation tokens",
            "Cloud Assist (Gemini 3.5 Flash) reasoning",
            "Grounded Knowledge Base (RAG docs)",
            "Hardware acceleration (Vulkan GPU + NPU)",
            "Local REST API background server daemon",
            "Custom AI Persona studio editor"
        )
    ),
    ENTERPRISE(
        id = "enterprise",
        displayName = "Enterprise Studio",
        maxAllocations = 1_000_000,
        priceMonthly = "$49/mo",
        badgeLabel = "ENTERPRISE",
        description = "Uncapped multi-model pipeline execution for teams and power creators.",
        featureList = listOf(
            "1,000,000 monthly allocation tokens",
            "Everything in Pro Creator",
            "Unlimited multi-step agent plugin pipelines",
            "Dedicated batch background queue processing",
            "Exportable encrypted audit logs"
        )
    );

    companion object {
        fun fromId(id: String): SubscriptionTier {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FREE
        }
    }
}

data class UserSubscriptionProfile(
    val userId: String = "usr_creator_8921",
    val email: String = "jesselepota.com@gmail.com",
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val usedAllocations: Int = 1_420,
    val maxAllocations: Int = SubscriptionTier.FREE.maxAllocations,
    val currentPeriodEnd: String = "Oct 01, 2026",
    val isAutoRenew: Boolean = true
) {
    val isPro: Boolean get() = tier == SubscriptionTier.PRO_CREATOR || tier == SubscriptionTier.ENTERPRISE
    val remainingAllocations: Int get() = maxOf(0, maxAllocations - usedAllocations)
    val usageRatio: Float get() = if (maxAllocations > 0) (usedAllocations.toFloat() / maxAllocations.toFloat()).coerceIn(0f, 1f) else 0f
    val usagePercentage: Int get() = (usageRatio * 100).toInt()
}

enum class SupabaseStatus {
    UNCONFIGURED,
    CONNECTING,
    CONNECTED,
    LOCAL_CACHE,
    ERROR
}

data class SupabaseConnectionConfig(
    val url: String = "",
    val anonKey: String = "",
    val status: SupabaseStatus = SupabaseStatus.LOCAL_CACHE,
    val lastSyncMessage: String = "Using local-first allocation cache (air-gapped ready)",
    val lastSyncTimestamp: Long = System.currentTimeMillis()
) {
    val isConfigured: Boolean get() = url.isNotBlank() && !url.contains("your-project") && anonKey.isNotBlank() && !anonKey.contains("your-anon-key")
}

sealed class AllocationCheckResult {
    data class Allowed(val remaining: Int) : AllocationCheckResult()
    data class QuotaExceeded(val used: Int, val max: Int) : AllocationCheckResult()
}

enum class StripeAccountMode {
    TEST_MODE,
    LIVE_MODE,
    UNCONFIGURED
}

data class StripeBillingConfig(
    val publishableKey: String = "pk_test_LTsnLXAZKhKJqVczf2JjYTLk00g98H1Nl1",
    val paymentLinkUrl: String = "",
    val mode: StripeAccountMode = StripeAccountMode.TEST_MODE,
    val currency: String = "USD",
    val proMonthlyPriceId: String = "price_pro_creator_monthly",
    val statusMessage: String = "Stripe Test Mode active (pk_test_...1Nl1). Ready for client checkout."
) {
    val isConfigured: Boolean get() = publishableKey.isNotBlank() && publishableKey.startsWith("pk_")
}
