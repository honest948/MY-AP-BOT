package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AdminAuditLog
import com.example.model.GlobalFleetState
import com.example.model.OwnerPolicy
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun AdminVaultScreen(viewModel: CommandCenterViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val policyState by viewModel.ownerPolicyState.collectAsStateWithLifecycle()
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val policy = (policyState as? UiState.Success)?.data ?: OwnerPolicy()
    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()
    var copiedEnvBanner by remember { mutableStateOf(false) }

    var maintenanceMsg by remember(policy.maintenanceMessage) { mutableStateOf(policy.maintenanceMessage) }
    var allowNewLinks by remember(policy.allowNewLinkRequests) { mutableStateOf(policy.allowNewLinkRequests) }
    var defaultQuota by remember(policy.defaultDailyQuota) { mutableFloatStateOf(policy.defaultDailyQuota.toFloat().coerceIn(50f, 5000f)) }
    var maxMediaMb by remember(policy.maxMediaSizeMb) { mutableFloatStateOf(policy.maxMediaSizeMb.toFloat().coerceIn(4f, 100f)) }

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
                    Text("SECURITY VAULT & ADMIN AUDIT", style = MaterialTheme.typography.labelSmall, color = CyberMint)
                    Text("Encryption & Governance", style = MaterialTheme.typography.headlineMedium)
                }
            }

            item {
                // Hardware Keystore & Secrets Diagnostics Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateEmerald),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CyberMint)
                            Spacer(Modifier.width(8.dp))
                            Text("Encrypted Session Vault & API Secrets", style = MaterialTheme.typography.titleMedium)
                        }
                        DiagnosticRow(
                            label = "Android Keystore AES-256-GCM",
                            active = viewModel.isHardwareVaultReady,
                            value = if (viewModel.isHardwareVaultReady) "Active & Sealed" else "Unavailable"
                        )
                        DiagnosticRow(
                            label = "OpenAI Responses + Web Search",
                            active = viewModel.apiDiagnostics.openAiConfigured,
                            value = if (viewModel.apiDiagnostics.openAiConfigured) "Key Loaded" else "Set OPENAI_API_KEY in Secrets"
                        )
                        DiagnosticRow(
                            label = "WhatsApp Business Cloud API",
                            active = viewModel.apiDiagnostics.whatsAppConfigured,
                            value = if (viewModel.apiDiagnostics.whatsAppConfigured) {
                                "Phone ID ${viewModel.apiDiagnostics.phoneNumberIdMasked}"
                            } else {
                                "Set WHATSAPP_ACCESS_TOKEN & PHONE_NUMBER_ID"
                            }
                        )
                        DiagnosticRow(
                            label = "Media Extractor Bridge (.play/.dl)",
                            active = viewModel.apiDiagnostics.mediaExtractorConfigured,
                            value = if (viewModel.apiDiagnostics.mediaExtractorConfigured) "Connected" else "Optional (Direct URLs active)"
                        )
                    }
                }
            }

            item {
                // Global Owner Policy Editor
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightPine),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Owner Global Policy Settings", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = maintenanceMsg,
                            onValueChange = { maintenanceMsg = it },
                            label = { Text("Maintenance Auto-Reply Message") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allow New Account Link Requests", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = allowNewLinks, onCheckedChange = { allowNewLinks = it })
                        }
                        Text("Default User Daily Quota: ${defaultQuota.toLong()}", style = MaterialTheme.typography.bodySmall)
                        Slider(value = defaultQuota, onValueChange = { defaultQuota = it }, valueRange = 50f..5000f)

                        Text("Max Media Upload Cap: ${maxMediaMb.toLong()} MB", style = MaterialTheme.typography.bodySmall)
                        Slider(value = maxMediaMb, onValueChange = { maxMediaMb = it }, valueRange = 4f..100f)

                        Button(
                            onClick = {
                                viewModel.saveGlobalPolicySettings(
                                    fleetState = GlobalFleetState.fromRaw(policy.globalFleetState),
                                    maintenanceMessage = maintenanceMsg,
                                    allowNewLinkRequests = allowNewLinks,
                                    defaultDailyQuota = defaultQuota.toLong(),
                                    maxMediaSizeMb = maxMediaMb.toLong()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_global_policy_button")
                        ) {
                            Text("Save Global Policy", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                ProBotAutomationCard(
                    policy = policy,
                    accounts = accounts,
                    isBusy = isBusy,
                    onSaveAutomation = { updated -> viewModel.saveBotAutomationPolicy(updated) },
                    onCopyBridgeEnv = { env ->
                        clipboard.setText(AnnotatedString(env))
                        copiedEnvBanner = true
                    }
                )
            }

            if (copiedEnvBanner) {
                item {
                    Surface(
                        color = CyberMint.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyberMint.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bridge config.env copied to clipboard! Paste into your Docker / Heroku / Render environment variables to sync Honest Bot Pro settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberMint,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                SupportContactCard()
            }

            item {
                val userEmail = Firebase.auth.currentUser?.email ?: "Owner Account"
                OutlinedButton(
                    onClick = { signOutOwner(credentialManager, scope) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = SignalCoral)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out ($userEmail)", color = SignalCoral)
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = CyberMint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Immutable Owner Admin Audit Trail", style = MaterialTheme.typography.titleMedium)
                }
            }

            items(auditLogs.take(20), key = { it.id }) { log ->
                AuditLogCard(log)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    active: Boolean,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Key, contentDescription = null, tint = if (active) CyberMint else AmberPulse, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Surface(
            color = (if (active) CyberMint else AmberPulse).copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) CyberMint else AmberPulse,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AuditLogCard(log: AdminAuditLog) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedPine),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MossOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(log.actionType, style = MonoCodeStyle, color = ElectricCyan, fontWeight = FontWeight.Bold)
                Text(log.targetLabel, style = MonoCodeStyle, color = CyberMint)
            }
            Text(log.details, style = MaterialTheme.typography.bodySmall, color = SageText)
        }
    }
}
