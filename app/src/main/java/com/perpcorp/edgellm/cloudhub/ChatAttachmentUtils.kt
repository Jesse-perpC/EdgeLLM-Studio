package com.perpcorp.edgellm.cloudhub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/** Attachment kinds mirrored from PrivateLM's chat attach flow. */
enum class AttachmentKind {
    IMAGE,
    PDF,
    DOCUMENT,
    AUDIO,
    OTHER,
}

/**
 * Attachment helpers ported from PrivateLM `chat_controller.dart`:
 * vision downscale (768px / JPEG q72, picker-side equivalent), extension→kind
 * mapping, and empty-box doc prompt prefill.
 */
object ChatAttachmentUtils {

    private const val TAG = "ChatAttachmentUtils"

    /** Mirrors `_visionImageMaxSide` in PrivateLM. */
    const val VISION_MAX_SIDE = 768

    /** Mirrors `_visionImageJpegQuality` in PrivateLM. */
    const val VISION_JPEG_QUALITY = 72

    fun attachmentKindForExtension(extension: String): AttachmentKind {
        return when (extension.lowercase().removePrefix(".")) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> AttachmentKind.IMAGE
            "pdf" -> AttachmentKind.PDF
            "doc", "docx", "txt", "md", "rtf", "epub", "csv", "json", "html", "htm" ->
                AttachmentKind.DOCUMENT
            "mp3", "wav", "m4a", "ogg", "opus", "flac", "aac" -> AttachmentKind.AUDIO
            else -> AttachmentKind.OTHER
        }
    }

    /**
     * Empty-input prefill per attachment kind (mirrors PrivateLM strings).
     * Returns null when no prefill applies (caller keeps the user's text).
     */
    fun defaultPromptForAttachment(kind: AttachmentKind, userText: String): String? {
        if (userText.isNotBlank()) return null
        return when (kind) {
            AttachmentKind.IMAGE -> "Describe this image."
            AttachmentKind.PDF -> "Summarize this PDF."
            AttachmentKind.DOCUMENT -> "Summarize this document."
            AttachmentKind.AUDIO -> "Transcribe or analyze this audio."
            AttachmentKind.OTHER -> null
        }
    }

    /**
     * Downscale raw image bytes so the longest side is [VISION_MAX_SIDE],
     * re-encode JPEG q72, return base64 (no data: prefix — callers add it).
     * Returns null when the bytes cannot be decoded.
     */
    fun downscaleForVision(
        imageBytes: ByteArray,
        maxSide: Int = VISION_MAX_SIDE,
        quality: Int = VISION_JPEG_QUALITY,
    ): String? {
        return try {
            val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return null
            try {
                val longest = maxOf(decoded.width, decoded.height)
                val scaled = if (longest <= maxSide) {
                    decoded
                } else {
                    val scale = maxSide.toFloat() / longest
                    val w = (decoded.width * scale).toInt().coerceAtLeast(1)
                    val h = (decoded.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, w, h, true).also {
                        if (it != decoded) decoded.recycle()
                    }
                }
                try {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                } finally {
                    if (scaled != decoded) scaled.recycle() else decoded.recycle()
                }
            } catch (e: Exception) {
                try {
                    decoded.recycle()
                } catch (_: Exception) {
                }
                Log.w(TAG, "Vision downscale failed", e)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vision decode failed", e)
            null
        }
    }

    /**
     * Heuristic from PrivateLM `_checkVisionSupport`: warn when the model name
     * carries no vision signal. Never blocks — some fine-tunes omit keywords.
     */
    fun looksLikeVisionModel(modelName: String): Boolean {
        val lower = modelName.lowercase()
        return lower.contains("vision") ||
            lower.contains("vl") ||
            lower.contains("gpt-4o") ||
            lower.contains("gemini") ||
            lower.contains("claude") ||
            lower.contains("qwen") && (lower.contains("2-vl") || lower.contains("2.5-vl"))
    }
}
