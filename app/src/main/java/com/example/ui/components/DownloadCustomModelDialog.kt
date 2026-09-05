package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ModelCategory
import com.example.data.model.ModelFormat
import com.example.data.model.ModelSpec
import com.example.engine.MemorySafetyManager

@Composable
fun DownloadCustomModelDialog(
    onDismiss: () -> Unit,
    onDownloadCustomModel: (
        name: String,
        downloadUrl: String,
        format: ModelFormat,
        parameterCount: String,
        quantization: String,
        fileSizeMb: Long,
        category: ModelCategory
    ) -> Unit,
    memorySafetyManager: MemorySafetyManager,
    modifier: Modifier = Modifier
) {
    var modelName by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(ModelFormat.GGUF) }
    var parameterCount by remember { mutableStateOf("1.5B") }
    var quantization by remember { mutableStateOf("Q4_K_M") }
    var fileSizeMbText by remember { mutableStateOf("850") }

    val fileSizeMb = fileSizeMbText.toLongOrNull() ?: 500L
    val sizeBytes = fileSizeMb * 1024L * 1024L
    val requiredRamBytes = (sizeBytes * 1.35).toLong()

    val tempModelSpec = remember(modelName, selectedFormat, parameterCount, quantization, sizeBytes, requiredRamBytes) {
        ModelSpec(
            id = "preview-temp",
            name = modelName.ifBlank { "Custom Model" },
            parameterCount = parameterCount,
            format = selectedFormat,
            quantization = quantization,
            fileSizeBytes = sizeBytes,
            requiredRamBytes = requiredRamBytes,
            contextLength = 4096,
            description = "Preview",
            category = ModelCategory.CHAT_REASONING
        )
    }

    val safetyReport = remember(tempModelSpec) {
        memorySafetyManager.evaluateModelSafety(tempModelSpec)
    }

    val isFormValid by remember {
        derivedStateOf {
            modelName.isNotBlank() &&
                    downloadUrl.isNotBlank() &&
                    (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) &&
                    fileSizeMb > 0
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("download_custom_model_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Model to Room DB", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Specify model weights to download via foreground service and store metadata in persistent Room database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("e.g., Llama-3.2-1B-Instruct") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_model_name_input")
                )

                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("Direct Download URL") },
                    placeholder = { Text("https://huggingface.co/.../model.gguf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_model_url_input")
                )

                Text(
                    text = "Model Format:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelFormat.entries.forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = { Text(format.displayName) },
                            modifier = Modifier.testTag("format_chip_${format.name}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = parameterCount,
                        onValueChange = { parameterCount = it },
                        label = { Text("Params") },
                        placeholder = { Text("1.5B") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = quantization,
                        onValueChange = { quantization = it },
                        label = { Text("Quantization") },
                        placeholder = { Text("Q4_K_M") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = fileSizeMbText,
                    onValueChange = { fileSizeMbText = it.filter { char -> char.isDigit() } },
                    label = { Text("Estimated Size (MB)") },
                    placeholder = { Text("850") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_model_size_input")
                )

                // Memory Constraint & Safety Report Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) {
                        Color(0xFF10B981).copy(alpha = 0.12f)
                    } else if (!safetyReport.isStorageSufficient) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        Color(0xFFF59E0B).copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) Color(0xFF10B981)
                        else if (!safetyReport.isStorageSufficient) MaterialTheme.colorScheme.error
                        else Color(0xFFF59E0B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) Color(0xFF10B981)
                            else if (!safetyReport.isStorageSufficient) MaterialTheme.colorScheme.error
                            else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) {
                                    "Memory Safe • Fits comfortably"
                                } else if (!safetyReport.isStorageSufficient) {
                                    "Storage Warning: Insufficient disk space"
                                } else {
                                    "RAM Alert: Device memory constrained"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (safetyReport.isSafeToRun && safetyReport.isStorageSufficient) Color(0xFF10B981)
                                else if (!safetyReport.isStorageSufficient) MaterialTheme.colorScheme.error
                                else Color(0xFFF59E0B)
                            )
                            Text(
                                text = safetyReport.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDownloadCustomModel(
                        modelName,
                        downloadUrl,
                        selectedFormat,
                        parameterCount,
                        quantization,
                        fileSizeMb,
                        ModelCategory.CHAT_REASONING
                    )
                    onDismiss()
                },
                enabled = isFormValid && safetyReport.isStorageSufficient,
                modifier = Modifier.testTag("confirm_download_custom_model_btn")
            ) {
                Text("Start Download")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
