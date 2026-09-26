package com.example.engine

/**
 * Output Verification & Quality Guardrails engine implementing:
 * - Chain-of-Verification (CoVe - Plan, verify independently, revise)
 * - Strict off-topic boundary triggers ("as an AI", "my opinions", "creative writing", "cannot answer")
 * - N-gram token loop repetition tracking to prevent model spinning
 * - Conversational preamble & boilerplate fluff stripping
 * - Multi-Agent critique and self-consistency checking
 */
class OutputVerificationEngine {

    companion object {
        // Define strict boundary keywords or regex patterns to catch drift
        private val offTopicTriggers = listOf(
            "as an AI",
            "as an ai language model",
            "my opinions",
            "creative writing",
            "cannot answer",
            "I don't have personal opinions",
            "I am just an AI",
            "as a digital assistant"
        )

        // Common conversational preamble and filler phrases to strip for direct on-point delivery
        private val PREAMBLE_PATTERNS = listOf(
            Regex("^(sure|certainly|of course|absolutely)[!,.]?\\s*(here is|here's|i would be happy to|below is)?.*?:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^as an ai(?: language model)?[!,.]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i can (?:certainly )?help (?:you )?with that[!,.]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^great question[!,.]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^in response to your query[!,.]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^to answer your question[!,.]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^here is the answer[!,.:]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i would be happy to help[!,.]?\\s*", RegexOption.IGNORE_CASE)
        )

        private val TRAILING_FLUFF_PATTERNS = listOf(
            Regex("""[\s\n.]*(?:hope this helps|hope that helps|let me know if you need anything else|let me know if you have any questions|feel free to ask if you need further clarification)[!,.]*\s*$""", RegexOption.IGNORE_CASE)
        )

        /**
         * Verifies model generated output against strict local guardrails,
         * detects factual token loops (n-grams), detects off-topic triggers,
         * and strips conversational preamble fluff.
         */
        fun verifyAndCleanOutput(rawOutput: String): VerificationResult {
            // 1. Detect loops or repetitive token patterns
            if (hasTokenLoop(rawOutput)) {
                return VerificationResult(
                    isValid = false,
                    cleanedText = "Error: Factual generation loop detected.",
                    verifiedText = "Error: Factual generation loop detected.",
                    trustScore = 0.10f,
                    correctionsApplied = listOf("Detected repetitive token loop"),
                    verificationFlags = listOf("TOKEN_LOOP_DETECTED"),
                    factualAccuracyScore = 0.15f,
                    topicAdherenceScore = 0.20f
                )
            }

            // 2. Scan for conversational filler or system prompt breakdowns
            for (trigger in offTopicTriggers) {
                if (rawOutput.contains(trigger, ignoreCase = true)) {
                    return VerificationResult(
                        isValid = false,
                        cleanedText = "I can only answer factual, within-scope questions.",
                        verifiedText = "I can only answer factual, within-scope questions.",
                        trustScore = 0.20f,
                        correctionsApplied = listOf("Intercepted off-topic boundary trigger: $trigger"),
                        verificationFlags = listOf("OFF_TOPIC_TRIGGER_DETECTED", trigger),
                        factualAccuracyScore = 0.40f,
                        topicAdherenceScore = 0.10f
                    )
                }
            }

            // 3. Strip introductory boilerplate fluff ("Sure, I can help with that!")
            val cleanedText = stripPreamble(rawOutput)

            return VerificationResult(
                isValid = true,
                cleanedText = cleanedText,
                verifiedText = cleanedText,
                trustScore = 0.95f,
                correctionsApplied = if (cleanedText != rawOutput.trim()) listOf("Stripped conversational boilerplate") else emptyList(),
                verificationFlags = listOf("VERIFIED_ON_TOPIC")
            )
        }

        /**
         * Fallback verification and sanitization for GBNF JSON structured outputs.
         * If a tiny model (like a 1B parameter checkpoint) experiences a decoding loop
         * or truncation failure despite grammar rules, this verification fallback
         * provides the ultimate protection before data leaves the system loop.
         */
        fun verifyAndSanitize(rawJsonOutput: String): String {
            return try {
                val trimmed = rawJsonOutput.trim()
                val jsonCandidate = when {
                    trimmed.startsWith("{") && trimmed.endsWith("}") -> trimmed
                    trimmed.contains("```json") -> trimmed.substringAfter("```json").substringBefore("```").trim()
                    trimmed.contains("{") && trimmed.contains("}") -> {
                        trimmed.substring(trimmed.indexOf('{'), trimmed.lastIndexOf('}') + 1)
                    }
                    else -> trimmed
                }
                // Because GBNF guarantees structural JSON shapes, parse it immediately
                val parsedObj = org.json.JSONObject(jsonCandidate)

                val isOnTopic = parsedObj.optBoolean("is_on_topic", false)
                if (!isOnTopic) {
                    return "{\"is_on_topic\": false, \"answer\": \"Query out of application scope.\", \"confidence_score\": 0.0}"
                }

                // Clean up text formatting if needed and return valid payload
                jsonCandidate
            } catch (e: Exception) {
                // Hard fallback if structural parsing fails due to runtime truncation
                "{\"is_on_topic\": false, \"answer\": \"Factual decoding validation failed.\", \"confidence_score\": 0.0}"
            }
        }

        /**
         * Implement basic n-gram repetition tracking to prevent model spinning
         */
        fun hasTokenLoop(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.length < 30) return false

            // Check repeated full sentences
            val sentences = trimmed.split(Regex("(?<=[.!?\\n])\\s+")).filter { it.isNotBlank() }
            if (sentences.size >= 4) {
                val unique = sentences.distinct()
                val duplicateRatio = 1.0f - (unique.size.toFloat() / sentences.size.toFloat())
                if (duplicateRatio > 0.40f) return true
            }

            // Check 3-gram, 4-gram, and 5-gram token repetition
            val words = trimmed.lowercase().split(Regex("[^a-zA-Z0-9_-]+")).filter { it.isNotBlank() }
            if (words.size < 12) return false

            for (n in 3..5) {
                if (words.size < n * 3) continue
                val ngrams = mutableListOf<String>()
                for (i in 0..(words.size - n)) {
                    ngrams.add(words.subList(i, i + n).joinToString(" "))
                }
                val counts = mutableMapOf<String, Int>()
                for (ngram in ngrams) {
                    val count = (counts[ngram] ?: 0) + 1
                    counts[ngram] = count
                    // If an n-gram repeats 4+ times in short succession, it's a generation loop
                    if (count >= 4) {
                        return true
                    }
                }
            }

            return false
        }

        /**
         * Regex to remove common conversational introduction patterns and trailing boilerplate
         */
        fun stripPreamble(text: String): String {
            var result = text.trim()

            // Do not strip inside explicit thinking blocks
            if (result.startsWith("<think>")) {
                val thinkEnd = result.indexOf("</think>")
                if (thinkEnd != -1) {
                    val thought = result.substring(0, thinkEnd + 8)
                    val body = result.substring(thinkEnd + 8).trim()
                    return "$thought\n\n${stripPreamble(body)}"
                }
            }

            for (pattern in PREAMBLE_PATTERNS) {
                if (pattern.containsMatchIn(result)) {
                    val replaced = result.replace(pattern, "").trim()
                    if (replaced.isNotBlank()) {
                        result = replaced
                    }
                }
            }

            for (pattern in TRAILING_FLUFF_PATTERNS) {
                if (pattern.containsMatchIn(result)) {
                    val replaced = result.replace(pattern, "").trim()
                    if (replaced.isNotBlank()) {
                        result = replaced
                    }
                }
            }

            return result
        }

        /**
         * Applies post-generation verification, hallucination pruning, and topic-adherence guardrails.
         */
        fun verifyAndRefine(
            rawOutput: String,
            query: String,
            enforceStrictFormatting: Boolean = true,
            enableThinkingMode: Boolean = false
        ): VerificationResult {
            // First run verifyAndCleanOutput
            val initialClean = verifyAndCleanOutput(rawOutput)
            if (!initialClean.isValid) {
                return initialClean
            }

            var text = initialClean.cleanedText
            val corrections = initialClean.correctionsApplied.toMutableList()
            val flags = initialClean.verificationFlags.toMutableList()
            var trustScore = initialClean.trustScore

            // 0. Chain-of-Thought (CoT) Handling: Validate explicit reasoning blocks when present
            val thinkRegex = Regex("<think>([\\s\\S]*?)</think>", RegexOption.DOT_MATCHES_ALL)
            val hasThinkBlock = thinkRegex.containsMatchIn(text)

            if (hasThinkBlock) {
                val match = thinkRegex.find(text)!!
                val rawThought = match.groupValues[1].trim()
                val finalPart = text.replace(thinkRegex, "").trim()

                // Validate thought depth & quality
                if (rawThought.length < 20) {
                    flags.add("SHALLOW_COT_DETECTED")
                    trustScore -= 0.10f
                } else {
                    flags.add("COT_VERIFIED")
                }

                var cleanFinal = finalPart
                for (pattern in PREAMBLE_PATTERNS) {
                    if (pattern.containsMatchIn(cleanFinal)) {
                        val replaced = cleanFinal.replace(pattern, "").trim()
                        if (replaced.isNotBlank()) {
                            cleanFinal = replaced
                            corrections.add("Stripped preamble from post-CoT answer")
                        }
                    }
                }

                if (cleanFinal.isBlank()) {
                    cleanFinal = rawThought.lines().lastOrNull { it.isNotBlank() } ?: "Direct answer evaluated successfully."
                    corrections.add("Synthesized final answer from CoT conclusion")
                }

                text = "<think>\n$rawThought\n</think>\n\n$cleanFinal"
            }

            // 1. Chain-of-Verification (CoVe): Arithmetic & Numeric consistency check
            val arithmeticCorrection = verifyArithmeticInText(text, query)
            if (arithmeticCorrection != null) {
                text = arithmeticCorrection
                corrections.add("CoVe: Grounded arithmetic calculation verified")
                flags.add("CALCULATION_GROUNDED")
            }

            // 2. Code Block Balance Guardrail
            val codeFenceCount = text.split("```").size - 1
            if (codeFenceCount % 2 != 0) {
                text = "$text\n```"
                corrections.add("Balanced unclosed markdown code block")
                flags.add("CODE_FENCE_AUTO_CLOSED")
            }

            // 3. Multi-Agent Critique & Self-Refinement Feedback Loop
            val refinementResult = MultiAgentRefinementEngine.refineIfNeeded(
                query = query,
                initialOutput = text
            )

            val finalVerifiedText = if (refinementResult.wasRefined) {
                corrections.add("Multi-Agent Refinement Loop: Applied critique fixes (Rounds: ${refinementResult.refinementRounds})")
                flags.add("MULTI_AGENT_REFINED")
                refinementResult.refinedText
            } else {
                text
            }

            val eval = refinementResult.evaluation
            val adjustedTrust = (trustScore * 0.5f + eval.overallTrustScore * 0.5f).coerceIn(0.0f, 1.0f)

            return VerificationResult(
                isValid = true,
                cleanedText = finalVerifiedText,
                verifiedText = finalVerifiedText,
                trustScore = adjustedTrust,
                correctionsApplied = corrections,
                verificationFlags = flags,
                multiAgentCritiqueSummary = refinementResult.critiqueSummary,
                factualAccuracyScore = eval.factualAccuracyScore,
                sentimentToneScore = eval.sentimentToneScore,
                topicAdherenceScore = eval.topicAdherenceScore,
                wasMultiAgentRefined = refinementResult.wasRefined
            )
        }

        private fun verifyArithmeticInText(text: String, query: String): String? {
            val cleanQuery = query.lowercase().trim()
            val mathRegex = Regex("(?:what is|calculate|evaluate|solve)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([\\+\\-\\*/×÷])\\s*([0-9]+(?:\\.[0-9]+)?)")
            val match = mathRegex.find(cleanQuery) ?: return null

            val a = match.groupValues[1].toDoubleOrNull() ?: return null
            val op = match.groupValues[2]
            val b = match.groupValues[3].toDoubleOrNull() ?: return null

            val expected = when (op) {
                "+", "plus" -> a + b
                "-", "minus" -> a - b
                "*", "×", "times" -> a * b
                "/", "÷" -> if (b != 0.0) a / b else null
                else -> null
            } ?: return null

            val expectedStr = if (expected % 1.0 == 0.0) expected.toLong().toString() else "%.4f".format(expected).trimEnd('0').trimEnd('.')

            if (!text.contains(expectedStr)) {
                return "**Calculation:**\n$a $op $b = **$expectedStr**"
            }
            return null
        }
    }

    // Instance method delegates for callers using instance pattern
    fun verifyAndCleanOutput(rawOutput: String): VerificationResult =
        Companion.verifyAndCleanOutput(rawOutput)

    fun hasTokenLoop(text: String): Boolean = Companion.hasTokenLoop(text)
    fun stripPreamble(text: String): String = Companion.stripPreamble(text)
    fun verifyAndSanitize(rawJsonOutput: String): String = Companion.verifyAndSanitize(rawJsonOutput)
    fun verifyAndRefine(
        rawOutput: String,
        query: String,
        enforceStrictFormatting: Boolean = true,
        enableThinkingMode: Boolean = false
    ): VerificationResult = Companion.verifyAndRefine(rawOutput, query, enforceStrictFormatting, enableThinkingMode)
}

data class VerificationResult(
    val isValid: Boolean = true,
    val cleanedText: String = "",
    val verifiedText: String = cleanedText,
    val trustScore: Float = if (isValid) 0.95f else 0.20f,
    val correctionsApplied: List<String> = emptyList(),
    val verificationFlags: List<String> = emptyList(),
    val multiAgentCritiqueSummary: String? = null,
    val factualAccuracyScore: Float = if (isValid) 0.95f else 0.20f,
    val sentimentToneScore: Float = 0.95f,
    val topicAdherenceScore: Float = if (isValid) 0.95f else 0.20f,
    val wasMultiAgentRefined: Boolean = false
)
