package com.example.data.model

data class PromptTemplate(
    val id: String,
    val title: String,
    val category: String,
    val iconEmoji: String,
    val description: String,
    val prefix: String,
    val placeholderText: String
)

object BuiltInPromptTemplates {
    val ALL: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "template_summarize",
            title = "Key Takeaways",
            category = "Productivity",
            iconEmoji = "⚡",
            description = "Distill any text or article into 3 clear, actionable bullets.",
            prefix = "Summarize the following content into 3 concise bullet points highlighting key insights and action items:\n\n",
            placeholderText = "Paste text or article to summarize..."
        ),
        PromptTemplate(
            id = "template_eli5",
            title = "Explain Simply (ELI5)",
            category = "Education",
            iconEmoji = "🔍",
            description = "Explain complex technical concepts using plain language and analogies.",
            prefix = "Explain this concept in simple terms that a high school student or non-technical user can easily understand, using an intuitive everyday analogy:\n\n",
            placeholderText = "Enter technical topic or question..."
        ),
        PromptTemplate(
            id = "template_unit_test",
            title = "Generate Unit Tests",
            category = "Coding",
            iconEmoji = "🧪",
            description = "Write JUnit / Robolectric tests covering edge cases and exceptions.",
            prefix = "Write comprehensive unit tests in Kotlin (using JUnit and assertions) covering nominal behavior, edge cases, and error boundaries for the following code:\n\n",
            placeholderText = "Paste code snippet to generate tests for..."
        ),
        PromptTemplate(
            id = "template_json_convert",
            title = "Convert to JSON",
            category = "Data",
            iconEmoji = "📊",
            description = "Transform unstructured text or tables into clean, validated JSON format.",
            prefix = "Convert the following raw text or table into clean, strictly valid JSON format with semantic field names:\n\n",
            placeholderText = "Paste unstructured text or list..."
        ),
        PromptTemplate(
            id = "template_sec_audit",
            title = "Security & Privacy Audit",
            category = "Security",
            iconEmoji = "🛡️",
            description = "Inspect text or code for confidential leaks, PII, and security vulnerabilities.",
            prefix = "Perform a thorough privacy and security audit on the following text or code. Identify potential data leakage, PII exposure, unencrypted data risks, and security improvements:\n\n",
            placeholderText = "Paste text, configuration, or code to audit..."
        ),
        PromptTemplate(
            id = "template_translate",
            title = "Offline Translation",
            category = "Language",
            iconEmoji = "🌐",
            description = "Translate text accurately preserving nuances without cloud services.",
            prefix = "Translate the following text into fluent, natural French, Spanish, German, and Japanese, maintaining professional context:\n\n",
            placeholderText = "Enter text to translate..."
        )
    )
}
