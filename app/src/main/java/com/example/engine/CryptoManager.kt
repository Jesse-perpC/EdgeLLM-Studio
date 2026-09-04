package com.example.engine

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptionResult(
    val ciphertextBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val sha256Hash: String,
    val plainSizeBytes: Long,
    val cipherSizeBytes: Long
)

class CryptoManager {

    private val random = SecureRandom()

    fun encryptText(plaintext: String, passphrase: String): EncryptionResult {
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)

        // Generate 16-byte random salt
        val salt = ByteArray(16)
        random.nextBytes(salt)

        // Derive 256-bit AES key using PBKDF2
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Generate 12-byte IV for AES-GCM
        val iv = ByteArray(12)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainBytes)

        // Compute SHA-256 hash of plaintext
        val sha256 = MessageDigest.getInstance("SHA-256").digest(plainBytes)
        val hashHex = sha256.joinToString("") { "%02x".format(it) }

        return EncryptionResult(
            ciphertextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            sha256Hash = hashHex,
            plainSizeBytes = plainBytes.size.toLong(),
            cipherSizeBytes = cipherBytes.size.toLong()
        )
    }

    fun decryptText(
        ciphertextBase64: String,
        ivBase64: String,
        saltBase64: String,
        passphrase: String
    ): Result<String> {
        return try {
            val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            Result.success(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateStrongPassphrase(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*"
        return (1..18).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
