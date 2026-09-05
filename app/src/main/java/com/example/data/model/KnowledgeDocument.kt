package com.example.data.model

data class KnowledgeDocument(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val sizeBytes: Long,
    val tokenCountEstimate: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isPreloaded: Boolean = false
)

object SampleKnowledgeDocuments {
    val NPU_HARDWARE_SPEC = KnowledgeDocument(
        id = "doc_npu_spec",
        title = "Mobile Neural Engine Architecture.txt",
        summary = "Hardware specifications for Qualcomm Hexagon NPU, Apple Neural Engine, and Google Tensor TPU.",
        content = """
# Mobile Neural Processing Units (NPU) Architecture Specification

1. Qualcomm Hexagon NPU (Snapdragon 8 Gen 3 / Gen 4)
- Peak INT4 compute: up to 45 TOPS (Tera Operations Per Second)
- Dedicated tensor accelerator with 48KB L1 cache per execution lane
- Hardware support for dynamic activation sparsity and speculative decoding
- Shared system memory bus width: 64 GB/s LPDDR5X

2. Apple Neural Engine (ANE - A17 Pro / M3 / M4)
- 16-core dedicated systolic array architecture
- Peak compute: 35–38 TOPS (FP16/INT8 mixed precision)
- Direct unified memory access (UMA) bypassing CPU cache evictions
- Zero-copy tensor dispatch via CoreML engine

3. Google Tensor TPU v3 (Pixel 8 / Pixel 9)
- Custom Google DeepMind co-designed matrix multiplication units (MXU)
- Native Bfloat16 and INT8 hardware dot-product acceleration
- Embedded thermal governor with 3-phase thermal throttle mitigation
- Sub-5ms context prefill latency for 1K token prompts

4. Best Practices for On-Device Deployment:
- Prefer Q4_K_M quantization for 1B-3B models on devices with 6-8 GB RAM.
- Use Q8_0 KV Cache to prevent context expansion memory overflow.
- Keep thread count <= physical performance core count to prevent thermal throttling.
""".trimIndent(),
        sizeBytes = 1240L,
        tokenCountEstimate = 295,
        isPreloaded = true
    )

    val PRIVATE_PROJECT_PHOENIX = KnowledgeDocument(
        id = "doc_project_phoenix",
        title = "Confidential_Project_Phoenix_Brief.md",
        summary = "Internal private project requirements, milestone dates, and security policies.",
        content = """
# Project Phoenix: Autonomous Local Machine Intelligence

## Confidential Executive Briefing (Do Not Egress to Cloud)

- Objective: Build a standalone, 100% air-gapped on-device neural operating layer for mission-critical offline devices.
- Target Release: Q4 2026 Milestone Beta.
- Core Pillars:
  1. Zero Data Egress: Absolute prohibition of cloud telemetry, analytics pings, or remote token logging.
  2. Sub-50ms First Token Latency (TTFT): Prefill acceleration using vectorized SIMD kernels.
  3. Sovereign Key Management: All user conversation sessions encrypted at rest via AES-256-GCM with keys derived using 100,000 PBKDF2 iterations.
  4. Dynamic Plug-in Sandboxing: PII redactors and regex filters must run in under 2ms prior to LLM tokenization.

- Assigned Leads:
  - Lead Systems Architect: Dr. Jesse Lepota
  - Security Compliance Lead: Vault Security Team
  - Hardware Acceleration Lead: Edge Compute Group
""".trimIndent(),
        sizeBytes = 940L,
        tokenCountEstimate = 210,
        isPreloaded = true
    )

    val KOTLIN_VECTOR_MATH = KnowledgeDocument(
        id = "doc_kotlin_vector",
        title = "Vectorized_Matrix_Math_Cheatsheet.kt",
        summary = "High performance array math patterns for Android SIMD and memory locality.",
        content = """
// EdgeLLM Vector Math Guidelines for Android
package com.example.math

// Cache-friendly row-major matrix multiplication with zero object allocations
fun matrixVectorDot(matrix: FloatArray, vector: FloatArray, rows: Int, cols: Int, out: FloatArray) {
    var mIdx = 0
    for (r in 0 until rows) {
        var acc = 0f
        for (c in 0 until cols) {
            acc += matrix[mIdx++] * vector[c]
        }
        out[r] = acc
    }
}

// Fast Softmax with numerical stability (subtract max)
fun stableSoftmax(logits: FloatArray, length: Int) {
    var maxVal = Float.NEGATIVE_INFINITY
    for (i in 0 until length) {
        if (logits[i] > maxVal) maxVal = logits[i]
    }
    var sum = 0f
    for (i in 0 until length) {
        logits[i] = kotlin.math.exp(logits[i] - maxVal)
        sum += logits[i]
    }
    val invSum = 1f / sum
    for (i in 0 until length) {
        logits[i] *= invSum
    }
}
""".trimIndent(),
        sizeBytes = 870L,
        tokenCountEstimate = 195,
        isPreloaded = true
    )

    val ALL_SAMPLES = listOf(
        NPU_HARDWARE_SPEC,
        PRIVATE_PROJECT_PHOENIX,
        KOTLIN_VECTOR_MATH
    )
}
