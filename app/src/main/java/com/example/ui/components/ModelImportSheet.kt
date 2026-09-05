package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelImportSheet(
    onSelectFolder: () -> Unit,
    onSelectFiles: () -> Unit,
    onImportDemoPack: () -> Unit,
    onDownloadCustomUrl: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("model_import_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Add & Import Local Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a folder or enter a direct GGUF/TFLite/ONNX URL to download. Metadata is stored persistently in Room database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Custom URL / HuggingFace Download
            ImportActionCard(
                title = "Download from Custom URL / HuggingFace",
                subtitle = "Paste direct link to .gguf, .tflite, or .onnx weights. Stored with name, format, path, and size in Room.",
                icon = Icons.Default.AutoAwesome,
                accentColor = Color(0xFF3B82F6),
                testTag = "import_download_custom_url_btn",
                onClick = {
                    onDismiss()
                    onDownloadCustomUrl()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Primary: Select Folder
            ImportActionCard(
                title = "Select Folder Containing Models",
                subtitle = "Select any local folder (e.g., Downloads, Models, SD card). All .gguf, .tflite, .onnx, and .bin files will be discovered and auto-loaded.",
                icon = Icons.Default.Folder,
                accentColor = MaterialTheme.colorScheme.primary,
                testTag = "import_select_folder_btn",
                onClick = {
                    onDismiss()
                    onSelectFolder()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary: Select Files
            ImportActionCard(
                title = "Select Model File(s)",
                subtitle = "Pick one or multiple model weight files directly using the system file selector.",
                icon = Icons.Default.FileOpen,
                accentColor = Color(0xFFA855F7),
                testTag = "import_select_files_btn",
                onClick = {
                    onDismiss()
                    onSelectFiles()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Demo Pack / Sample Models Folder
            ImportActionCard(
                title = "Import Demo Models Folder",
                subtitle = "Generates and scans a sample models directory (Llama-3.2 1B, DeepSeek-R1 1.5B, Whisper) with the live progress status bar.",
                icon = Icons.Default.AutoAwesome,
                accentColor = Color(0xFF10B981),
                testTag = "import_demo_pack_btn",
                onClick = {
                    onDismiss()
                    onImportDemoPack()
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Format info pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Supported: GGUF (llama.cpp), LiteRT / TFLite (TensorFlow), ONNX Runtime, SafeTensors, and PyTorch bin containers.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
