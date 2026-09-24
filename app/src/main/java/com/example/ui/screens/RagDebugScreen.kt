package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RagChunkItem
import com.example.ui.MainViewModel

@Composable
fun RagDebugScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val knowledgeDocs = viewModel.sampleKnowledgeDocs

    var testQuery by remember { mutableStateOf("system security and air-gapped encryption parameters") }
    var similarityThreshold by remember { mutableFloatStateOf(0.65f) }
    var selectedFormatFilter by remember { mutableStateOf<String?>(null) }

    // Sample content-addressed chunks extracted from documents
    val sampleChunks = remember {
        mutableStateListOf(
            RagChunkItem(
                id = "chk-9f83a",
                documentTitle = "Device_Security_Whitepaper.pdf",
                chunkIndex = 1,
                totalChunks = 8,
                sha256ContentHash = "3e28c49a10df7b9c9f0a",
                content = "The core security boundary is enforced using Argon2id key derivation (t=4, m=128 MiB, p=1). Master data encryption keys are stored XOR-masked and sealed under Android Keystore AES-256-GCM hardware StrongBox.",
                tokenCount = 68,
                embeddingDimension = 128,
                cosineSimilarityScore = 0.942f,
                retrievalLatencyMs = 6L,
                sourceFormat = "PDF",
                characterSpanStart = 0,
                characterSpanEnd = 245
            ),
            RagChunkItem(
                id = "chk-8b12e",
                documentTitle = "OnDevice_Inference_Specs.docx",
                chunkIndex = 3,
                totalChunks = 12,
                sha256ContentHash = "8b12ef09ca44d189c201",
                content = "Streaming generation routes through localized NPU/NNAPI hardware delegates. Context window management applies Attention Sinks and StreamingLLM KV cache eviction to maintain steady memory consumption under 2GB.",
                tokenCount = 74,
                embeddingDimension = 128,
                cosineSimilarityScore = 0.815f,
                retrievalLatencyMs = 8L,
                sourceFormat = "DOCX",
                characterSpanStart = 310,
                characterSpanEnd = 580
            ),
            RagChunkItem(
                id = "chk-4c91d",
                documentTitle = "Model_Quantization_Matrix.xlsx",
                chunkIndex = 2,
                totalChunks = 5,
                sha256ContentHash = "4c91de88f01b3a726d9e",
                content = "Q4_K_M delivers 99.2% accuracy fidelity relative to FP16 with a 3.8x reduction in required RAM. Suitable for Snapdragon 8 Gen 2/3 NPU Hexagon DSPs.",
                tokenCount = 52,
                embeddingDimension = 128,
                cosineSimilarityScore = 0.730f,
                retrievalLatencyMs = 5L,
                sourceFormat = "XLSX",
                characterSpanStart = 120,
                characterSpanEnd = 295
            ),
            RagChunkItem(
                id = "chk-1a05f",
                documentTitle = "Plugin_Capability_Protocol.md",
                chunkIndex = 1,
                totalChunks = 4,
                sha256ContentHash = "1a05fe998877bc43210a",
                content = "Plugins execute in isolated DexClassLoader containers. Capabilities (HXS storage, ONNX inference, camera, internet) are strictly enforced at runtime via PolicyEngine capability tokens.",
                tokenCount = 61,
                embeddingDimension = 128,
                cosineSimilarityScore = 0.685f,
                retrievalLatencyMs = 7L,
                sourceFormat = "MD",
                characterSpanStart = 0,
                characterSpanEnd = 210
            )
        )
    }

    val filteredChunks = sampleChunks.filter { chunk ->
        val matchesThreshold = chunk.cosineSimilarityScore >= similarityThreshold
        val matchesFormat = selectedFormatFilter == null || chunk.sourceFormat.equals(selectedFormatFilter, ignoreCase = true)
        matchesThreshold && matchesFormat
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("rag_debug_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = Color(0xFF06B6D4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "RAG Semantic Debugger",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Content-Addressed Chunks & Vector Cosine Inspector",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF06B6D4).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "128-D Cosine",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF06B6D4),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Supported Ingestion Formats: PDF, DOCX, XLSX, PPTX, ODT, EPUB, RTF, MD, HTML, JSON, XML, CSV, TXT. Documents are indexed into content-addressed chunks with cryptographic verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Interactive Test Query Input
        item {
            Column {
                Text(
                    text = "Live Query Retrieval Test",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = testQuery,
                    onValueChange = { testQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rag_test_query_input"),
                    placeholder = { Text("Enter query to test vector similarity against chunks...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (testQuery.isNotBlank()) {
                            IconButton(onClick = { testQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 3. Similarity Threshold Slider
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cosine Similarity Threshold: ${String.format("%.2f", similarityThreshold)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${filteredChunks.size} Chunks Matched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = similarityThreshold,
                        onValueChange = { similarityThreshold = it },
                        valueRange = 0.40f..0.98f,
                        modifier = Modifier.testTag("rag_similarity_slider")
                    )
                }
            }
        }

        // 4. Source Format Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val formats = listOf("All", "PDF", "DOCX", "XLSX", "MD", "TXT", "JSON")
                items(formats) { fmt ->
                    val isSelected = if (fmt == "All") selectedFormatFilter == null else selectedFormatFilter.equals(fmt, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedFormatFilter = if (fmt == "All") null else fmt
                        },
                        label = { Text(fmt) }
                    )
                }
            }
        }

        // 5. Retrieved Chunks List
        items(filteredChunks) { chunk ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rag_chunk_card_${chunk.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = chunk.documentTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Cosine match badge
                        val scoreColor = when {
                            chunk.cosineSimilarityScore >= 0.85f -> Color(0xFF10B981)
                            chunk.cosineSimilarityScore >= 0.70f -> Color(0xFF38BDF8)
                            else -> Color(0xFFF59E0B)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scoreColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${(chunk.cosineSimilarityScore * 100).toInt()}% Match",
                                style = MaterialTheme.typography.labelSmall,
                                color = scoreColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chunk snippet
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = chunk.content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Chunk Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Chunk ${chunk.chunkIndex}/${chunk.totalChunks}", style = MaterialTheme.typography.labelSmall)
                            Text("•", style = MaterialTheme.typography.labelSmall)
                            Text("${chunk.tokenCount} tok", style = MaterialTheme.typography.labelSmall)
                            Text("•", style = MaterialTheme.typography.labelSmall)
                            Text("${chunk.retrievalLatencyMs}ms", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SHA: ${chunk.sha256ContentHash.take(8)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(chunk.content))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Chunk", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
