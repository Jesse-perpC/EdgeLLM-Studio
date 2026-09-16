package com.example.data.model

data class LoraAdapter(
    val id: String,
    val name: String,
    val targetArchitecture: String,
    val rank: Int,
    val alpha: Int,
    val description: String,
    val category: String,
    val accuracyBoost: String,
    val sizeMb: Float,
    val colorHex: Long = 0xFF6366F1
)

object BuiltInLoraAdapters {
    val ALL: List<LoraAdapter> = listOf(
        LoraAdapter(
            id = "lora_code_specialist",
            name = "DeepCoder-Pro LoRA",
            targetArchitecture = "Universal (GGUF / ONNX)",
            rank = 16,
            alpha = 32,
            description = "Specialized low-rank weights fine-tuned on clean Kotlin, Python, and C++ algorithmic repositories. Reduces syntax errors.",
            category = "Coding",
            accuracyBoost = "+18.4% HumanEval",
            sizeMb = 14.2f,
            colorHex = 0xFF0EA5E9
        ),
        LoraAdapter(
            id = "lora_clinical_medical",
            name = "MedSovereign Clinical LoRA",
            targetArchitecture = "Universal (GGUF / ONNX)",
            rank = 16,
            alpha = 32,
            description = "Trained on de-identified clinical vignettes, differential diagnosis trees, and pharmacology interactions with strict triage protocols.",
            category = "Healthcare",
            accuracyBoost = "+14.1% MedQA",
            sizeMb = 18.6f,
            colorHex = 0xFF10B981
        ),
        LoraAdapter(
            id = "lora_sql_database",
            name = "SQL-Schema Architect LoRA",
            targetArchitecture = "Universal (GGUF / ONNX)",
            rank = 8,
            alpha = 16,
            description = "Optimized for ANSI SQL, SQLite schemas, CTE queries, and relational entity extraction without syntax hallucinations.",
            category = "Data & SQL",
            accuracyBoost = "+22.3% Spider SQL",
            sizeMb = 8.5f,
            colorHex = 0xFFF59E0B
        ),
        LoraAdapter(
            id = "lora_concise_analyst",
            name = "Executive Briefing LoRA",
            targetArchitecture = "Universal (GGUF / ONNX)",
            rank = 8,
            alpha = 16,
            description = "Prunes excessive conversational filler tokens, enforcing high-density bulleted insights and direct executive action items.",
            category = "Productivity",
            accuracyBoost = "-45% Token Verbosity",
            sizeMb = 7.1f,
            colorHex = 0xFF8B5CF6
        ),
        LoraAdapter(
            id = "lora_security_auditor",
            name = "CyberShield Security LoRA",
            targetArchitecture = "Universal (GGUF / ONNX)",
            rank = 16,
            alpha = 32,
            description = "Fine-tuned to flag OWASP Top 10 vulnerabilities, API key leaks, unencrypted sockets, and Android manifest permission risks.",
            category = "Security",
            accuracyBoost = "+19.8% CVE Recall",
            sizeMb = 16.0f,
            colorHex = 0xFFEC4899
        )
    )
}
