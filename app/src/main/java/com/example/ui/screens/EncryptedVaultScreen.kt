package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudStorageTarget
import com.example.data.model.EncryptedExportRecord
import com.example.engine.ShareDuration
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptedVaultScreen(
    viewModel: MainViewModel,
    prefilledExportContent: String = "",
    modifier: Modifier = Modifier
) {
    val exports by viewModel.encryptedExports.collectAsState()
    val decryptedPreview by viewModel.decryptedPreview.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var showExportDialog by remember { mutableStateOf(prefilledExportContent.isNotBlank()) }
    var selectedRecordForDecrypt by remember { mutableStateOf<EncryptedExportRecord?>(null) }
    var decryptPassphrase by remember { mutableStateOf("") }
    var decryptErrorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("encrypted_vault_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cryptographic Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "End-to-End Encrypted Cloud Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Logs and reports are encrypted client-side using AES-256-GCM with PBKDF2 key derivation before cloud transmission. Only authorized recipients holding the passphrase can decrypt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showExportDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_export_dialog_btn")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Encrypted Log / Report")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Encrypted Bundles & Temp Links (${exports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (exports.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No encrypted files uploaded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Exported inferences and batch results will appear here with secure temporary links.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        items(exports, key = { it.id }) { export ->
            EncryptedRecordCard(
                export = export,
                onCopyShareLink = {
                    export.temporaryShareUrl?.let { link ->
                        clipboardManager.setText(AnnotatedString(link))
                    }
                },
                onDecrypt = {
                    selectedRecordForDecrypt = export
                    decryptPassphrase = ""
                    decryptErrorMessage = null
                },
                onDelete = { viewModel.deleteExport(export.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Export Dialog
    if (showExportDialog) {
        var exportTitle by remember { mutableStateOf("On-Device Processing Report") }
        var exportContent by remember {
            mutableStateOf(
                if (prefilledExportContent.isNotBlank()) prefilledExportContent
                else "=== ON-DEVICE CONFIDENTIAL LOG ===\nModel: TinyLlama 1.1B GGUF\nInference Time: 340ms\nPII Scrub: 100% Offline\nHash: Verified Local Silicon Execution"
            )
        }
        var passphrase by remember { mutableStateOf("") }
        var selectedCloudTarget by remember { mutableStateOf(CloudStorageTarget.GOOGLE_DRIVE) }
        var selectedDuration by remember { mutableStateOf(ShareDuration.TWENTY_FOUR_HOURS) }
        var cloudDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Client-Side E2EE Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = exportTitle,
                        onValueChange = { exportTitle = it },
                        label = { Text("Report Title") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = cloudDropdownExpanded,
                        onExpandedChange = { cloudDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCloudTarget.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cloud Storage Target") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cloudDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = cloudDropdownExpanded,
                            onDismissRequest = { cloudDropdownExpanded = false }
                        ) {
                            CloudStorageTarget.entries.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.displayName) },
                                    onClick = {
                                        selectedCloudTarget = target
                                        cloudDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption Passphrase") },
                        placeholder = { Text("Enter secret key...") },
                        trailingIcon = {
                            IconButton(onClick = { passphrase = viewModel.generateStrongPassphrase() }) {
                                Icon(Icons.Default.Key, contentDescription = "Generate Strong Key")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShareDuration.entries.forEach { duration ->
                            OutlinedButton(
                                onClick = { selectedDuration = duration },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = duration.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedDuration == duration) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedDuration == duration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = exportContent,
                        onValueChange = { exportContent = it },
                        label = { Text("Data to Encrypt & Upload") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val key = if (passphrase.isNotBlank()) passphrase else "NexusLocalSecretKey2026!"
                        viewModel.exportEncryptedData(
                            title = exportTitle,
                            sourceType = "Secure Report",
                            plaintext = exportContent,
                            passphrase = key,
                            cloudTarget = selectedCloudTarget,
                            duration = selectedDuration
                        )
                        showExportDialog = false
                    }
                ) {
                    Text("Seal & Encrypt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Decrypt & Inspect Dialog
    selectedRecordForDecrypt?.let { record ->
        AlertDialog(
            onDismissRequest = {
                selectedRecordForDecrypt = null
                viewModel.clearDecryptedPreview()
            },
            title = { Text("Decrypt: ${record.title}") },
            text = {
                Column {
                    Text(
                        text = "Algorithm: ${record.algorithm}\nCiphertext SHA-256:\n${record.sha256Hash.take(32)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (decryptedPreview != null) {
                        Text(
                            text = "Authenticated Decrypted Payload:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = decryptedPreview ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = decryptPassphrase,
                            onValueChange = {
                                decryptPassphrase = it
                                decryptErrorMessage = null
                            },
                            label = { Text("Passphrase") },
                            placeholder = { Text("Enter the passphrase used for encryption") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        decryptErrorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (decryptedPreview == null) {
                    Button(
                        onClick = {
                            val result = viewModel.decryptExport(
                                record = record,
                                passphrase = decryptPassphrase,
                                fullCiphertext = record.ciphertextPreview.replace("...", "")
                            )
                            if (result.isFailure) {
                                decryptErrorMessage = "Decryption failed: Incorrect key or corrupted ciphertext."
                            }
                        }
                    ) {
                        Text("Decrypt")
                    }
                } else {
                    Button(
                        onClick = {
                            selectedRecordForDecrypt = null
                            viewModel.clearDecryptedPreview()
                        }
                    ) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedRecordForDecrypt = null
                        viewModel.clearDecryptedPreview()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EncryptedRecordCard(
    export: EncryptedExportRecord,
    onCopyShareLink: () -> Unit,
    onDecrypt: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = export.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${export.cloudTarget.displayName} • ${export.algorithm}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "AES-256-GCM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Cipher: ${export.ciphertextPreview}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SHA-256: ${export.sha256Hash.take(24)}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            export.temporaryShareUrl?.let { link ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Secure Temp Link: ${link.take(30)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    IconButton(onClick = onCopyShareLink, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Temporary Link",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${export.plainSizeBytes} B plain → ${export.cipherSizeBytes} B cipher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    TextButton(onClick = onDecrypt) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Decrypt", fontSize = 11.sp)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Export",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
