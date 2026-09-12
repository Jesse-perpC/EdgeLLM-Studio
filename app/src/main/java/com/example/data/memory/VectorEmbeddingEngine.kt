package com.example.data.memory

import com.example.data.model.MemoryType
import com.example.data.model.SemanticMemoryRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object VectorEmbeddingEngine {

    const val DEFAULT_EMBEDDING_DIMENSION = 128

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
        "this", "that", "it", "they", "them", "their", "we", "us", "our"
    )

    /**
     * Generates a 128-dimensional dense unit-normalized semantic embedding on-device.
     * Uses subword n-gram hashing with positional phase weighting and L2 normalization.
     * Completely local, private, and deterministic.
     */
    fun generateEmbedding(text: String, dimensions: Int = DEFAULT_EMBEDDING_DIMENSION): List<Float> {
        val vector = FloatArray(dimensions)
        if (text.isBlank()) {
            return vector.toList()
        }

        val cleaned = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s_\\-]"), " ")
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }

        if (tokens.isEmpty()) {
            return vector.toList()
        }

        tokens.forEachIndexed { tokenIndex, token ->
            val isStopWord = STOP_WORDS.contains(token)
            val weight = if (isStopWord) 0.15f else 1.0f

            // 1. Full token hash mapping
            val tokenHash = token.hashCode()
            val dim1 = Math.abs(tokenHash) % dimensions
            val dim2 = Math.abs(tokenHash * 31 + 17) % dimensions
            val dim3 = Math.abs(tokenHash * 59 + 101) % dimensions

            vector[dim1] += 1.2f * weight
            vector[dim2] += 0.8f * weight * (if (tokenHash % 2 == 0) 1f else -1f)
            vector[dim3] += 0.5f * weight

            // 2. Character 3-grams for subword semantic capture (handles plurals, conjugations, typos)
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val sub = token.substring(i, i + 3)
                    val subHash = sub.hashCode()
                    val subDim = Math.abs(subHash * 43) % dimensions
                    val sign = if (subHash % 2 == 0) 1f else -1f
                    vector[subDim] += 0.45f * weight * sign
                }
            }

            // 3. Positional phase rotation
            val posAngle = (tokenIndex.toFloat() / tokens.size) * Math.PI.toFloat()
            val posDim = (dim1 + (tokenIndex % 7)) % dimensions
            vector[posDim] += 0.3f * weight * cos(posAngle)
        }

        // 4. L2 Normalization so that Dot Product == Cosine Similarity
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 1e-6f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector.toList()
    }

    /**
     * Computes the Cosine Similarity between two unit-normalized embedding vectors.
     * Returns a float value in the range [0.0f, 1.0f].
     */
    fun computeCosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0.0f

        var dotProduct = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
        }

        // Clamp to 0.0 .. 1.0 range
        return ((dotProduct + 1.0f) / 2.0f).coerceIn(0.0f, 1.0f)
    }

    /**
     * Converts a List<Float> to a compact comma-delimited String for Room persistence.
     */
    fun vectorToString(vector: List<Float>): String {
        return vector.joinToString(separator = ",") { String.format(Locale.US, "%.5f", it) }
    }

    /**
     * Parses a comma-delimited String back into a List<Float>.
     */
    fun stringToVector(str: String?): List<Float> {
        if (str.isNullOrBlank()) return emptyList()
        return try {
            str.split(",").mapNotNull { it.trim().toFloatOrNull() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Compact binary byte array conversion using FloatBuffer for low-footprint caching.
     */
    fun vectorToByteArray(vector: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun byteArrayToVector(bytes: ByteArray?): List<Float> {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val list = ArrayList<Float>(bytes.size / 4)
        while (buffer.hasRemaining() && buffer.remaining() >= 4) {
            list.add(buffer.float)
        }
        return list
    }

    /**
     * Automatically extracts key semantic memory nodes (User preferences, key facts, project parameters)
     * from a user/assistant turn so that the AI exhibits realistic human-like memory.
     */
    fun extractSemanticMemoriesFromTurn(
        userText: String,
        assistantText: String,
        sessionId: String,
        sourceMessageId: String? = null
    ): List<SemanticMemoryRecord> {
        val memories = mutableListOf<SemanticMemoryRecord>()
        val lowerUser = userText.trim().lowercase(Locale.ROOT)

        // 1. Detect User Preferences
        val preferencePatterns = listOf(
            Regex("(?:i prefer|i like|i love|my preference is)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE),
            Regex("(?:always use|never use|don't use|do not use)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE),
            Regex("(?:format my code in|write in|explain like)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in preferencePatterns) {
            val match = pattern.find(userText)
            if (match != null && match.groupValues.size > 1) {
                val excerpt = match.groupValues[1].trim()
                if (excerpt.length in 3..120) {
                    val content = "User preference: \"${match.value.trim()}\""
                    memories.add(
                        SemanticMemoryRecord(
                            id = "mem_${UUID.randomUUID()}",
                            sessionId = sessionId,
                            sourceMessageId = sourceMessageId,
                            memoryType = MemoryType.USER_PREFERENCE,
                            subject = "User Preference",
                            content = content,
                            embeddingVector = generateEmbedding(content),
                            importanceScore = 0.90f
                        )
                    )
                }
            }
        }

        // 2. Detect Identity & Factual Disclosures
        val factPatterns = listOf(
            Regex("(?:my name is|i am a|i'm an|i work as)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE),
            Regex("(?:i am building|our project is|we are working on)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE),
            Regex("(?:my hardware is|my device is|i have a)\\s+([^.?!,]+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in factPatterns) {
            val match = pattern.find(userText)
            if (match != null && match.groupValues.size > 1) {
                val content = "User context: \"${match.value.trim()}\""
                memories.add(
                    SemanticMemoryRecord(
                        id = "mem_${UUID.randomUUID()}",
                        sessionId = sessionId,
                        sourceMessageId = sourceMessageId,
                        memoryType = MemoryType.FACT,
                        subject = "User Identity & Stack",
                        content = content,
                        embeddingVector = generateEmbedding(content),
                        importanceScore = 0.85f
                    )
                )
            }
        }

        // 3. Detect Explicit "Remember" Commands
        val rememberMatch = Regex("(?:remember that|please remember|keep in mind that)\\s+([^.?!]+)", RegexOption.IGNORE_CASE).find(userText)
        if (rememberMatch != null && rememberMatch.groupValues.size > 1) {
            val content = "Explicit fact: \"${rememberMatch.groupValues[1].trim()}\""
            memories.add(
                SemanticMemoryRecord(
                    id = "mem_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    sourceMessageId = sourceMessageId,
                    memoryType = MemoryType.FACT,
                    subject = "Explicit Instruction",
                    content = content,
                    embeddingVector = generateEmbedding(content),
                    importanceScore = 0.98f
                )
            )
        }

        // 4. Summarize High-Value Query Turns as Episodic Memory
        if (userText.length > 25 && !userText.startsWith("/")) {
            val episodicExcerpt = userText.take(160).replace("\n", " ")
            val episodicContent = "Discussed: $episodicExcerpt"
            memories.add(
                SemanticMemoryRecord(
                    id = "mem_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    sourceMessageId = sourceMessageId,
                    memoryType = MemoryType.EPISODIC,
                    subject = "Recent Conversation Topic",
                    content = episodicContent,
                    embeddingVector = generateEmbedding(episodicContent),
                    importanceScore = 0.65f
                )
            )
        }

        return memories
    }
}
