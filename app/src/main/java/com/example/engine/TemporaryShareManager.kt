package com.example.engine

import java.security.SecureRandom
import java.util.UUID

data class TemporaryShareBundle(
    val token: String,
    val shareUrl: String,
    val accessPasscode: String,
    val expiresAt: Long,
    val formattedExpiry: String
)

enum class ShareDuration(val label: String, val durationMs: Long) {
    ONE_HOUR("1 Hour", 3600L * 1000L),
    TWENTY_FOUR_HOURS("24 Hours", 24L * 3600L * 1000L),
    SEVEN_DAYS("7 Days", 7L * 24L * 3600L * 1000L)
}

class TemporaryShareManager {

    private val random = SecureRandom()

    fun createTemporaryShare(duration: ShareDuration): TemporaryShareBundle {
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        val passcode = (100000 + random.nextInt(900000)).toString()
        val expiresAt = System.currentTimeMillis() + duration.durationMs
        val shareUrl = "https://edgellm.local/vault/r/$token"

        return TemporaryShareBundle(
            token = token,
            shareUrl = shareUrl,
            accessPasscode = passcode,
            expiresAt = expiresAt,
            formattedExpiry = duration.label
        )
    }
}
