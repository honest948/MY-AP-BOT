package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PlatformIntegration
import com.example.model.PlatformKey
import com.example.model.PlatformStatus
import com.example.model.UiState
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberMint
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedPine
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.MidnightPine
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MossOutline
import com.example.ui.theme.SageText
import com.example.ui.theme.SignalCoral
import com.example.ui.theme.SlateEmerald
import com.example.viewmodel.CommandCenterViewModel

@Composable
fun PlatformsScreen(viewModel: CommandCenterViewModel) {
    val platformsState by viewModel.platformsState.collectAsStateWithLifecycle()
    val webhookOutcome by viewModel.webhookTestOutcome.collectAsStateWithLifecycle()
    val platforms = (platformsState as? UiState.Success)?.data.orEmpty()

    var editingPlatform by remember { mutableStateOf<PlatformIntegration?>(null) }
    var testWebhookUrl by remember { mutableStateOf("https://httpbin.org/post") }
    var testWebhookSecret by remember { mutableStateOf("honest-bot-hmac-secret") }

    if (editingPlatform != null) {
        val plat = editingPlatform!!
        var endpoint by remember(plat.id) { mutableStateOf(plat.endpointOrHandle) }
        var autoSync by remember(plat.id) { mutableStateOf(plat.autoSyncEnabled) }
        var secret by remember(plat.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editingPlatform = null },
            containerColor = SlateEmerald,
            title = { Text("Configure ${plat.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Endpoint URL or Channel Handle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Webhook Signing Secret (Hashed SHA-256)") },
                        placeholder = { Text("Leave blank to keep existing") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Sync Real-Time Events", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePlatformConfig(plat, endpoint, autoSync, secret)
                        editingPlatform = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian)
                ) {
                    Text("Save Connector")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlatform = null }) {
                    Text("Cancel", color = SageText)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text("BUSINESS OPERATING PLATFORMS", style = MaterialTheme.typography.labelSmall, color = CyberMint)
                    Text("Omnichannel & Social Connectors", style = MaterialTheme.typography.headlineMedium)
                }
            }

            items(platforms, key = { it.id }) { plat ->
                val st = PlatformStatus.fromRaw(plat.status)
                val badgeColor = when (st) {
                    PlatformStatus.CONNECTED -> CyberMint
                    PlatformStatus.CONFIGURED -> ElectricCyan
                    PlatformStatus.NEEDS_KEYS -> AmberPulse
                    PlatformStatus.PAUSED -> SignalCoral
                }
                val icon = when (PlatformKey.fromRaw(plat.platformKey)) {
                    PlatformKey.WHATSAPP_CLOUD -> Icons.Default.CloudQueue
                    PlatformKey.WEBPAIR_BRIDGE -> Icons.Default.Hub
                    PlatformKey.INSTAGRAM_FB -> Icons.Default.Forum
                    PlatformKey.X_YOUTUBE -> Icons.Default.Public
                    PlatformKey.MEDIA_EXTRACTOR -> Icons.Default.MusicNote
                    PlatformKey.CRM_WEBHOOK -> Icons.Default.Webhook
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateEmerald),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingPlatform = plat }
                        .testTag("platform_card_${plat.platformKey.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = badgeColor)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plat.name, style = MaterialTheme.typography.titleMedium)
                            Text(plat.endpointOrHandle, style = MonoCodeStyle, color = SageText)
                            Text(
                                "Auto-Sync: ${if (plat.autoSyncEnabled) "Enabled" else "Manual"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = badgeColor
                            )
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                st.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Live HMAC-Signed Outbound Webhook Test Runner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightPine),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Api, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("HMAC-SHA256 CRM Webhook Tester", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Send a real signed HTTP POST event from Honest Bot Pro to verify your CRM, Zapier, Make, or custom Meta webhook relay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SageText
                        )
                        OutlinedTextField(
                            value = testWebhookUrl,
                            onValueChange = { testWebhookUrl = it },
                            label = { Text("Webhook Endpoint URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webhook_url_input")
                        )
                        OutlinedTextField(
                            value = testWebhookSecret,
                            onValueChange = { testWebhookSecret = it },
                            label = { Text("HMAC Signing Secret") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { viewModel.testPlatformWebhook(testWebhookUrl, testWebhookSecret) },
                            colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_webhook_button")
                        ) {
                            Text("Send Signed Webhook Ping", fontWeight = FontWeight.Bold)
                        }

                        if (webhookOutcome != null) {
                            val out = webhookOutcome!!
                            Surface(
                                color = ElevatedPine,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (out.success) CyberMint else SignalCoral),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = out.message,
                                        style = MonoCodeStyle,
                                        color = if (out.success) CyberMint else SignalCoral
                                    )
                                    if (out.signaturePrefix.isNotBlank()) {
                                        Text(
                                            text = "X-HonestBot-Signature-256: ${out.signaturePrefix}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SageText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
