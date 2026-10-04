package com.perpcorp.edgellm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.perpcorp.edgellm.cloudhub.CloudHubDefaults
import com.perpcorp.edgellm.cloudhub.CloudHubKeys
import com.perpcorp.edgellm.cloudhub.CloudHubService
import com.perpcorp.edgellm.cloudhub.CloudMessage
import com.perpcorp.edgellm.cloudhub.CloudProviders
import com.perpcorp.edgellm.cloudhub.EncryptedCloudHubStore
import com.perpcorp.edgellm.cloudhub.InferenceMode
import com.perpcorp.edgellm.cloudhub.apiKeyFor
import com.perpcorp.edgellm.cloudhub.inferenceMode
import com.perpcorp.edgellm.cloudhub.modelFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opt-in Cloud Hub settings (ported PrivateLM cloud settings surface).
 *
 * Default state is identical to today: LOCAL mode, no keys, zero network
 * calls. Everything here writes to the hardware-encrypted store. Enabling
 * CLOUD mode explicitly routes prompts to the selected third-party API —
 * the warning below says so in plain language next to the toggle.
 */
@Composable
fun CloudHubSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { EncryptedCloudHubStore.get(context) }
    val service = remember { CloudHubService(store) }

    var cloudEnabled by remember { mutableStateOf(false) }
    var providerId by remember { mutableStateOf(CloudHubDefaults.PROVIDER) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("Custom API") }
    var customBaseUrl by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(CloudHubDefaults.TEMPERATURE) }
    var maxTokensText by remember { mutableStateOf(CloudHubDefaults.MAX_TOKENS.toString()) }
    var showKey by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun reload() {
        cloudEnabled = store.inferenceMode() == InferenceMode.CLOUD
        providerId = store.getString(CloudHubKeys.PROVIDER, CloudHubDefaults.PROVIDER)
            ?: CloudHubDefaults.PROVIDER
        val provider = CloudProviders.byId(providerId)
        apiKey = store.apiKeyFor(provider.id)
        model = store.getString(CloudHubKeys.modelFor(provider.id), "").orEmpty()
            .ifBlank { provider.defaultModel }
        customName = store.getString(CloudHubKeys.CUSTOM_NAME, "Custom API") ?: "Custom API"
        customBaseUrl = store.getString(CloudHubKeys.CUSTOM_BASE_URL, "").orEmpty()
        temperature = store.getFloat(CloudHubKeys.TEMPERATURE, CloudHubDefaults.TEMPERATURE)
        maxTokensText = store.getInt(CloudHubKeys.MAX_TOKENS, CloudHubDefaults.MAX_TOKENS).toString()
    }

    LaunchedEffect(Unit) { reload() }
    val provider = CloudProviders.byId(providerId)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Cloud Hub (opt-in)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (store.isEncrypted()) {
                    "Keys are stored hardware-encrypted on this device."
                } else {
                    "Warning: hardware keystore unavailable — keys use private app storage."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (cloudEnabled) "Cloud mode" else "Local mode (default)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (cloudEnabled) {
                            "Prompts leave this device for ${provider.label}. No key stored = no calls."
                        } else {
                            "100% on-device. Nothing leaves the phone."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = cloudEnabled,
                    onCheckedChange = {
                        cloudEnabled = it
                        store.putString(
                            CloudHubKeys.INFERENCE_MODE,
                            if (it) InferenceMode.CLOUD.id else InferenceMode.LOCAL.id,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                OutlinedButton(
                    onClick = { providerMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(provider.label, modifier = Modifier.weight(1f))
                }
                DropdownMenu(
                    expanded = providerMenu,
                    onDismissRequest = { providerMenu = false },
                ) {
                    CloudProviders.ALL.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = {
                                providerMenu = false
                                providerId = p.id
                                store.putString(CloudHubKeys.PROVIDER, p.id)
                                apiKey = store.apiKeyFor(p.id)
                                model = store.getString(CloudHubKeys.modelFor(p.id), "").orEmpty()
                                    .ifBlank { p.defaultModel }
                                testResult = null
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    store.putString(CloudHubKeys.apiKeyFor(provider.id), it)
                    testResult = null
                },
                label = { Text("${provider.label} API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    OutlinedButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    store.putString(CloudHubKeys.modelFor(provider.id), it)
                    testResult = null
                },
                label = { Text("Model (blank = ${provider.defaultModel.ifBlank { "required" }})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (provider.needsCustomBaseUrl) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customBaseUrl,
                    onValueChange = {
                        customBaseUrl = it
                        store.putString(CloudHubKeys.CUSTOM_BASE_URL, it)
                        testResult = null
                    },
                    label = { Text("Custom base URL (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = {
                        customName = it
                        store.putString(CloudHubKeys.CUSTOM_NAME, it)
                    },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Temperature: ${"%.2f".format(temperature)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = temperature,
                onValueChange = {
                    temperature = it
                    store.putFloat(CloudHubKeys.TEMPERATURE, it)
                },
                valueRange = 0f..1.5f,
                steps = 14,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = {
                    maxTokensText = it.filter(Char::isDigit).take(5)
                    maxTokensText.toIntOrNull()?.let { v ->
                        if (v in 1..32768) store.putInt(CloudHubKeys.MAX_TOKENS, v)
                    }
                },
                label = { Text("Max tokens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        CoroutineScope(Dispatchers.Main).launch {
                            val res = withContext(Dispatchers.IO) {
                                service.sendMessage(
                                    messages = listOf(
                                        CloudMessage("user", "Reply with exactly: Cloud Hub OK"),
                                    ),
                                    maxTokens = 32,
                                )
                            }
                            testResult = res.take(300)
                            testing = false
                        }
                    },
                    enabled = !testing,
                ) {
                    Text(if (testing) "Testing…" else "Test connection")
                }
            }
            testResult?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("ERROR")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
