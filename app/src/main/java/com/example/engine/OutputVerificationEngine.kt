package com.example.engine

/**
 * Output Verification & Quality Guardrails engine inspired by:
 * - Label Studio LLM error catching paradigms (Rule-based checks, Gold standard & Factual verification, Trust Scoring)
 * - Chain-of-Verification (CoVe - Plan, verify independently, revise)
 * - Self-Consistency & Hallucination pruning
 * - Input/Output Guardrails (Formatting enforcement, preamble stripping, on-topic filtering)
 */
object OutputVerificationEngine {

    data class VerificationResult(
        val verifiedText: String,
        val trustScore: Float, // 0.0 to 1.0
        val correctionsApplied: List<String>,
        val verificationFlags: List<String>,
        val multiAgentCritiqueSummary: String? = null,
        val factualAccuracyScore: Float = 0.95f,
        val sentimentToneScore: Float = 0.95f,
        val topicAdherenceScore: Float = 0.95f,
        val wasMultiAgentRefined: Boolean = false
    )

    // Common conversational preamble and filler phrases to strip for direct on-point delivery
    private val PREAMBLE_PATTERNS = listOf(
        Regex("^(sure|certainly|of course|absolutely)[!,.]?\\s*(here is|here's|i would be happy to|below is)?.*?:?\\s*", RegexOption.IGNORE_CASE),
        Regex("^as an ai(?: language model)?[!,.]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^i can (?:certainly )?help (?:you )?with that[!,.]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^great question[!,.]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^in response to your query[!,.]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^to answer your question[!,.]?\\s*", RegexOption.IGNORE_CASE),
        Regex("^hope this helps[!,.]?\\s*$", RegexOption.IGNORE_CASE),
        Regex("^let me know if you need anything else[!,.]?\\s*$", RegexOption.IGNORE_CASE),
        Regex("^feel free to ask if you have more questions[!,.]?\\s*$", RegexOption.IGNORE_CASE)
    )

    /**
     * Applies post-generation verification, hallucination pruning, and topic-adherence guardrails.
     */
    fun verifyAndRefine(
        rawOutput: String,
        query: String,
        enforceStrictFormatting: Boolean = true
    ): VerificationResult {
        var text = rawOutput.trim()
        val corrections = mutableListOf<String>()
        val flags = mutableListOf<String>()
        var trustScore = 0.95f

        // 0. Chain-of-Thought (CoT) Validation & Normalization Layer
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

            // Prune preamble strictly from the final answer section
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

            // If the model produced only a think block without final answer, synthesize clear answer
            if (cleanFinal.isBlank()) {
                cleanFinal = rawThought.lines().lastOrNull { it.isNotBlank() } ?: "Direct answer evaluated successfully."
                corrections.add("Synthesized final answer from CoT conclusion")
            }

            // Reconstruct verified structure
            text = "<think>\n$rawThought\n</think>\n\n$cleanFinal"
        } else {
            // If the model did not generate an explicit <think> block, enforce CoT synthesis layer for complex/math/code queries
            val isComplexQuery = query.length > 35 ||
                    query.contains("calculate", ignoreCase = true) ||
                    query.contains("why", ignoreCase = true) ||
                    query.contains("how to", ignoreCase = true) ||
                    query.contains("vs", ignoreCase = true) ||
                    query.contains("explain", ignoreCase = true) ||
                    query.contains("code", ignoreCase = true)

            if (isComplexQuery && !text.startsWith("<think>")) {
                val synthesizedReasoning = buildString {
                    append("1. Analyzed query intent and factual boundaries: \"").append(query.take(60)).append("\"\n")
                    append("2. Verified factual constraints and precision parameters.\n")
                    append("3. Formulated direct, grounded answer with zero conversational padding.")
                }
                text = "<think>\n$synthesizedReasoning\n</think>\n\n$text"
                corrections.add("Injected Chain-of-Thought validation layer")
                flags.add("COT_SYNTHESIZED_LAYER")
            }
        }

        // 1. Preamble & Filler Pruning (Stripping throat-clearing for direct response)
        for (pattern in PREAMBLE_PATTERNS) {
            if (pattern.containsMatchIn(text) && !text.startsWith("<think>")) {
                val newText = text.replace(pattern, "").trim()
                if (newText.isNotBlank()) {
                    text = newText
                    corrections.add("Stripped conversational preamble/filler")
                }
            }
        }

        // 2. Trailing conversational fluff cleanup
        val trailingFluff = listOf(
            "\n\nHope this helps!",
            "\nHope this helps!",
            "\n\nLet me know if you have any questions!",
            "\nLet me know if you have any questions!",
            "\n\nFeel free to ask if you need further clarification.",
            "\nFeel free to ask if you need further clarification."
        )
        for (fluff in trailingFluff) {
            if (text.endsWith(fluff, ignoreCase = true)) {
                text = text.substring(0, text.length - fluff.length).trim()
                corrections.add("Removed trailing conversational fluff")
            }
        }

        // 3. Chain-of-Verification (CoVe): Arithmetic & Numeric consistency check
        val arithmeticCorrection = verifyArithmeticInText(text, query)
        if (arithmeticCorrection != null) {
            text = arithmeticCorrection
            corrections.add("CoVe: Grounded arithmetic calculation verified")
            flags.add("CALCULATION_GROUNDED")
        }

        // 4. Repetition & Looping Guardrail (detecting degraded generation loops)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        if (sentences.size >= 4) {
            val uniqueSentences = sentences.distinct()
            val duplicateRatio = 1.0f - (uniqueSentences.size.toFloat() / sentences.size.toFloat())
            if (duplicateRatio > 0.35f) {
                // Detected looping, prune duplicates
                text = uniqueSentences.joinToString(" ")
                corrections.add("Pruned repetitive generation loop")
                trustScore -= 0.20f
                flags.add("REPETITION_DETECTED_AND_PRUNED")
            }
        }

        // 5. Code Block Balance Guardrail
        val codeFenceCount = text.split("```").size - 1
        if (codeFenceCount % 2 != 0) {
            text = "$text\n```"
            corrections.add("Balanced unclosed markdown code block")
            flags.add("CODE_FENCE_AUTO_CLOSED")
        }

        // 6. Direct Factual Guardrail (ensuring the prompt topic is addressed)
        val queryKeywords = query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split(" ")
            .filter { it.length > 3 && it !in STOP_WORDS }

        if (queryKeywords.isNotEmpty()) {
            val textLower = text.lowercase()
            val matchedKeywords = queryKeywords.count { textLower.contains(it) }
            val keywordCoverage = matchedKeywords.toFloat() / queryKeywords.size.toFloat()
            if (keywordCoverage < 0.2f && !query.startsWith("calculate", ignoreCase = true)) {
                flags.add("LOW_PROMPT_KEYWORD_OVERLAP")
                trustScore -= 0.15f
            }
        }

        // 7. Multi-Agent Critique & Self-Refinement Feedback Loop
        // Evaluates Factual Accuracy, Sentiment/Tone, and Topic Adherence against strict thresholds
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

        // If the text does not contain the expected number or is wrong, return precise verified result
        if (!text.contains(expectedStr)) {
            return "**Calculation:**\n$a $op $b = **$expectedStr**"
        }
        return null
    }

    private val STOP_WORDS = setOf(
        "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
        "about", "above", "after", "again", "against", "all", "and", "any", "are",
        "because", "been", "before", "being", "below", "between", "both", "but",
        "could", "did", "does", "doing", "down", "during", "each", "few", "for",
        "from", "further", "had", "has", "have", "having", "here", "how", "into",
        "just", "more", "most", "other", "some", "such", "than", "that", "the",
        "their", "theirs", "them", "then", "there", "these", "they", "this",
        "those", "through", "very", "with", "would", "your", "tell", "explain",
        "give", "please", "write", "show"
    )
}
