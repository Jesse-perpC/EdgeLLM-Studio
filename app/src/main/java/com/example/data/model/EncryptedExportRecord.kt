package com.example.data.model

enum class CloudStorageTarget(val displayName: String, val iconRes: String) {
    GOOGLE_DRIVE("Google Drive (E2EE Vault)", "CloudUpload"),
    AWS_S3("AWS S3 Encrypted Bucket", "Storage"),
    NEXTCLOUD("Nextcloud Private Cloud", "Security"),
    LOCAL_ENCRYPTED_VAULT("Local Encrypted Vault", "Lock")
}

data class EncryptedExportRecord(
    val id: String,
    val title: String,
    val sourceType: String,
    val plainSizeBytes: Long,
    val cipherSizeBytes: Long,
    val algorithm: String = "AES-256-GCM / PBKDF2",
    val sha256Hash: String,
    val ivBase64: String,
    val ciphertextPreview: String,
    val cloudTarget: CloudStorageTarget,
    val temporaryShareUrl: String? = null,
    val temporaryShareToken: String? = null,
    val temporaryShareExpiresAt: Long? = null,
    val isUploaded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = temporaryShareExpiresAt != null && System.currentTimeMillis() > temporaryShareExpiresAt
}
