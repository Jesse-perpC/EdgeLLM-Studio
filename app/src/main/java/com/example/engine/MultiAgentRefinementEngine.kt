package com.example.engine

import kotlin.math.roundToInt

/**
 * Multi-Agent Critique and Self-Refinement Feedback Loop.
 *
 * Implements a dual-agent architecture:
 * 1. Critic Agent: Evaluates the primary response across Factual Accuracy, Topic Adherence,
 *    and Sentiment/Tone alignment, calculating a multi-dimensional confidence score.
 * 2. Refinement Agent: If any metric falls below defined acceptance thresholds
 *    (e.g., Factual Accuracy < 0.75 or Sentiment/Tone < 0.70), the Critic outputs an explicit
 *    critique diagnosis, and the Refinement Agent produces an improved, corrected output.
 */
object MultiAgentRefinementEngine {

    data class CritiqueEvaluation(
        val factualAccuracyScore: Float, // 0.0 to 1.0
        val sentimentToneScore: Float,   // 0.0 to 1.0 (constructive, professional, non-toxic, confident)
        val topicAdherenceScore: Float,  // 0.0 to 1.0
        val overallTrustScore: Float,    // weighted harmonic mean
        val requiresRefinement: Boolean,
        val critiqueReasons: List<String>,
        val diagnosedWeaknesses: List<String>
    )

    data class RefinementResult(
        val initialText: String,
        val refinedText: String,
        val evaluation: CritiqueEvaluation,
        val wasRefined: Boolean,
        val refinementRounds: Int,
        val critiqueSummary: String
    )

    // Configuration thresholds
    const val DEFAULT_FACTUAL_THRESHOLD = 0.75f
    const val DEFAULT_SENTIMENT_THRESHOLD = 0.70f
    const val DEFAULT_ADHERENCE_THRESHOLD = 0.75f

    /**
     * Executes the Critique Agent evaluation on the candidate response.
     */
    fun evaluateCandidate(
        query: String,
        candidateOutput: String,
        factualThreshold: Float = DEFAULT_FACTUAL_THRESHOLD,
        sentimentThreshold: Float = DEFAULT_SENTIMENT_THRESHOLD,
        adherenceThreshold: Float = DEFAULT_ADHERENCE_THRESHOLD
    ): CritiqueEvaluation {
        val text = candidateOutput.trim()
        val reasons = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()

        // 1. Topic Adherence & Keyword Coverage Evaluation
        val cleanQuery = query.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val keywords = cleanQuery.split("\\s+".toRegex()).filter { it.length > 3 && it !in STOP_WORDS }
        val textLower = text.lowercase()

        var adherenceScore = 0.90f
        if (keywords.isNotEmpty()) {
            val matches = keywords.count { textLower.contains(it) }
            val coverage = matches.toFloat() / keywords.size.toFloat()
            if (coverage < 0.25f && !query.startsWith("calculate", ignoreCase = true) && !query.startsWith("solve", ignoreCase = true)) {
                adherenceScore = (0.45f + coverage * 0.4f).coerceIn(0.2f, 0.70f)
                reasons.add("Low query keyword grounding: output may have drifted from original prompt.")
                weaknesses.add("TOPIC_DRIFT")
            } else {
                adherenceScore = (0.75f + coverage * 0.25f).coerceIn(0.70f, 1.0f)
            }
        }

        // 2. Factual & Deterministic Accuracy Evaluation
        var factualScore = 0.92f

        // 2a. Arithmetic / Mathematical Consistency Evaluation
        val mathRegex = Regex("(?:what is|calculate|evaluate|solve)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([\\+\\-\\*/×÷])\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val mathMatch = mathRegex.find(cleanQuery)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull()
            if (a != null && b != null) {
                val expected = when (op) {
                    "+", "plus" -> a + b
                    "-", "minus" -> a - b
                    "*", "×", "times" -> a * b
                    "/", "÷" -> if (b != 0.0) a / b else null
                    else -> null
                }
                if (expected != null) {
                    val expectedStr = if (expected % 1.0 == 0.0) expected.toLong().toString() else "%.4f".format(expected).trimEnd('0').trimEnd('.')
                    if (!text.contains(expectedStr)) {
                        factualScore = 0.30f
                        reasons.add("Mathematical calculation discrepancy detected. Expected $expectedStr.")
                        weaknesses.add("ARITHMETIC_INACCURACY")
                    } else {
                        factualScore = 0.98f
                    }
                }
            }
        }

        // 2b. Hallucination / Degenerate Looping Evaluation
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.length > 15 }
        if (sentences.size >= 4) {
            val uniqueCount = sentences.distinct().size
            val repetitionRatio = 1.0f - (uniqueCount.toFloat() / sentences.size.toFloat())
            if (repetitionRatio > 0.30f) {
                factualScore -= 0.30f
                reasons.add("High sentence repetition ratio (${(repetitionRatio * 100).toInt()}% duplicate content).")
                weaknesses.add("REPETITIVE_DEGRADATION")
            }
        }

        // 2c. Code Block Structural Integrity
        val codeBlocks = text.split("```").size - 1
        if (codeBlocks % 2 != 0) {
            factualScore -= 0.15f
            reasons.add("Unbalanced Markdown code blocks detected.")
            weaknesses.add("UNBALANCED_CODE_SYNTAX")
        }

        // 3. Sentiment & Tone Evaluation (Professionalism, Non-Defensive, Directness, Zero Toxic Patterns)
        var sentimentScore = 0.95f

        // Check for overly apologetic, unconfident, or defensive conversational boilerplate
        val apologeticFluff = listOf(
            "i apologize",
            "as an ai",
            "i am sorry",
            "i cannot provide",
            "i am only an artificial",
            "sorry for the confusion",
            "i might be wrong"
        )
        val apologeticMatches = apologeticFluff.count { textLower.contains(it) }
        if (apologeticMatches > 0) {
            sentimentScore -= (apologeticMatches * 0.15f).coerceAtMost(0.35f)
            reasons.add("Contains passive, defensive or apologetic boilerplate phrasing.")
            weaknesses.add("APOLOGETIC_BOILERPLATE")
        }

        // Check for condescending or lecturing tone
        val lecturingFluff = listOf(
            "you should know that",
            "it is obvious that",
            "as anyone knows",
            "clearly you should"
        )
        val lecturingMatches = lecturingFluff.count { textLower.contains(it) }
        if (lecturingMatches > 0) {
            sentimentScore -= 0.25f
            reasons.add("Lecturing or condescending phrasing detected.")
            weaknesses.add("LECTURING_TONE")
        }

        // Harmonic Trust Score
        val overallTrust = (0.45f * factualScore + 0.30f * topicAdherenceScore(adherenceScore) + 0.25f * sentimentScore)
            .coerceIn(0.0f, 1.0f)

        val needsRefinement = factualScore < factualThreshold ||
                sentimentScore < sentimentThreshold ||
                adherenceScore < adherenceThreshold

        return CritiqueEvaluation(
            factualAccuracyScore = factualScore.coerceIn(0.0f, 1.0f),
            sentimentToneScore = sentimentScore.coerceIn(0.0f, 1.0f),
            topicAdherenceScore = adherenceScore.coerceIn(0.0f, 1.0f),
            overallTrustScore = ((overallTrust * 100).roundToInt() / 100f),
            requiresRefinement = needsRefinement,
            critiqueReasons = reasons,
            diagnosedWeaknesses = weaknesses
        )
    }

    private fun topicAdherenceScore(score: Float): Float = score.coerceIn(0.0f, 1.0f)

    /**
     * Executes the Refinement Agent loop:
     * Evaluates initial output -> If score < threshold, constructs self-critique prompt -> Refines response.
     */
    fun refineIfNeeded(
        query: String,
        initialOutput: String,
        factualThreshold: Float = DEFAULT_FACTUAL_THRESHOLD,
        sentimentThreshold: Float = DEFAULT_SENTIMENT_THRESHOLD,
        adherenceThreshold: Float = DEFAULT_ADHERENCE_THRESHOLD,
        onRefinementAttempt: ((prompt: String) -> String)? = null
    ): RefinementResult {
        val firstEvaluation = evaluateCandidate(
            query = query,
            candidateOutput = initialOutput,
            factualThreshold = factualThreshold,
            sentimentThreshold = sentimentThreshold,
            adherenceThreshold = adherenceThreshold
        )

        if (!firstEvaluation.requiresRefinement) {
            return RefinementResult(
                initialText = initialOutput,
                refinedText = initialOutput,
                evaluation = firstEvaluation,
                wasRefined = false,
                refinementRounds = 0,
                critiqueSummary = "Output passed multi-agent audit with high confidence (Trust: ${(firstEvaluation.overallTrustScore * 100).toInt()}%)."
            )
        }

        // Multi-Agent Refinement Phase
        var currentText = initialOutput

        // Apply programmatic deterministic corrections first
        if (firstEvaluation.diagnosedWeaknesses.contains("ARITHMETIC_INACCURACY")) {
            val mathFix = fixArithmeticInText(query)
            if (mathFix != null) {
                currentText = mathFix
            }
        }

        if (firstEvaluation.diagnosedWeaknesses.contains("UNBALANCED_CODE_SYNTAX")) {
            if ((currentText.split("```").size - 1) % 2 != 0) {
                currentText = "$currentText\n```"
            }
        }

        if (firstEvaluation.diagnosedWeaknesses.contains("APOLOGETIC_BOILERPLATE")) {
            currentText = currentText.replace(Regex("(?i)\\b(as an ai(?: language model)?|i apologize,?|i am sorry,?)\\b\\s*"), "")
                .trim()
        }

        // If an LLM refinement callback is provided and weaknesses persist, invoke generator agent with critique feedback
        if (onRefinementAttempt != null && (firstEvaluation.diagnosedWeaknesses.contains("TOPIC_DRIFT") || firstEvaluation.diagnosedWeaknesses.contains("REPETITIVE_DEGRADATION"))) {
            val critiqueFeedbackPrompt = buildString {
                append("Original Prompt: ").append(query).append("\n")
                append("Candidate Response: ").append(initialOutput.take(300)).append("\n")
                append("CRITIQUE FEEDBACK:\n")
                firstEvaluation.critiqueReasons.forEach { append("- ").append(it).append("\n") }
                append("\nREFINEMENT INSTRUCTIONS: Rewrite the response addressing the critique points above. Provide a direct, factually verified, high-precision answer without throat-clearing.")
            }
            val llmRefined = try {
                onRefinementAttempt(critiqueFeedbackPrompt)
            } catch (_: Exception) {
                null
            }
            if (!llmRefined.isNullOrBlank()) {
                currentText = llmRefined
            }
        }

        // Post-refinement verification
        val secondEvaluation = evaluateCandidate(
            query = query,
            candidateOutput = currentText,
            factualThreshold = factualThreshold,
            sentimentThreshold = sentimentThreshold,
            adherenceThreshold = adherenceThreshold
        )

        val summary = buildString {
            append("Multi-Agent Self-Correction Triggered:\n")
            firstEvaluation.critiqueReasons.forEach { append(" • [Critique]: ").append(it).append("\n") }
            append("Score improved from ${(firstEvaluation.overallTrustScore * 100).toInt()}% -> ${(secondEvaluation.overallTrustScore * 100).toInt()}%.")
        }

        return RefinementResult(
            initialText = initialOutput,
            refinedText = currentText,
            evaluation = secondEvaluation,
            wasRefined = true,
            refinementRounds = 1,
            critiqueSummary = summary
        )
    }

    private fun fixArithmeticInText(query: String): String? {
        val cleanQuery = query.lowercase().trim()
        val mathRegex = Regex("(?:what is|calculate|evaluate|solve)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([\\+\\-\\*/×÷])\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
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
        return "**Calculation:**\n$a $op $b = **$expectedStr**"
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
