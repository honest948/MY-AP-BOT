package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AccountRole
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.LinkingMethod
import com.example.model.OperatingMode
import com.example.model.TargetType
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkingScreen(
    viewModel: CommandCenterViewModel,
    onSelectAccountForConsole: (String) -> Unit
) {
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val policyState by viewModel.ownerPolicyState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val linkingError by viewModel.linkingError.collectAsStateWithLifecycle()
    val whatsAppConnection by viewModel.whatsAppConnection.collectAsStateWithLifecycle()

    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val policy = (policyState as? UiState.Success)?.data
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var targetType by remember { mutableStateOf(TargetType.PERSONAL_INBOX) }
    var linkingMethod by remember { mutableStateOf(LinkingMethod.CLOUD_API) }
    var countryCode by remember { mutableStateOf("+263") }
    var phoneInput by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var customPhoneNumberId by remember { mutableStateOf("") }
    var webPairUrlInput by remember(policy?.webPairBridgeUrl) { mutableStateOf(policy?.webPairBridgeUrl.orEmpty()) }
    var sessionIdInput by remember(policy?.sessionId) { mutableStateOf(policy?.sessionId.orEmpty()) }
    var selectedRole by remember { mutableStateOf(AccountRole.STANDARD) }
    var dispatchViaApi by remember { mutableStateOf(viewModel.apiDiagnostics.whatsAppConfigured) }
    var showCountryPicker by remember { mutableStateOf(false) }

    var statusFilter by remember { mutableStateOf<ConnectionStatus?>(null) }
    var adminSheetAccount by remember { mutableStateOf<ConnectedAccount?>(null) }
    var verifyDialogAccount by remember { mutableStateOf<ConnectedAccount?>(null) }
    var verifyDialogNote by remember { mutableStateOf<String?>(null) }
    var showBroadcastDialog by remember { mutableStateOf(false) }

    val selectedIds = remember { mutableStateListOf<String>() }

    val filteredAccounts = remember(accounts, statusFilter) {
        if (statusFilter == null) accounts
        else accounts.filter { ConnectionStatus.fromRaw(it.connectionStatus) == statusFilter }
    }

    val normalizedPreview = remember(targetType, countryCode, phoneInput) {
        if (targetType == TargetType.PERSONAL_INBOX && phoneInput.isNotBlank()) {
            val clean = viewModel.normalizeSubscriberDigits(countryCode, phoneInput)
            if (clean.isNotEmpty()) "$countryCode$clean" else null
        } else {
            null
        }
    }

    if (showCountryPicker) {
        CountryCodePickerDialog(
            onDismiss = { showCountryPicker = false },
            onSelect = {
                countryCode = it.code
                showCountryPicker = false
            }
        )
    }

    if (verifyDialogAccount != null) {
        val acc = verifyDialogAccount!!
        var codeInput by remember(acc.id) { mutableStateOf(acc.pairingCode) }
        AlertDialog(
            onDismissRequest = {
                verifyDialogAccount = null
                verifyDialogNote = null
            },
            containerColor = SlateEmerald,
            title = { Text("Activate Link Behind Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Account: ${acc.e164Number}\nSession Fingerprint: ${acc.sessionFingerprint.take(16)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SageText
                    )
                    if (!verifyDialogNote.isNullOrBlank()) {
                        Surface(
                            color = AmberPulse.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberPulse.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = verifyDialogNote!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberPulse,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Surface(
                        color = ElevatedPine,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LuminousEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(acc.pairingCode, style = MonoCodeStyle, color = CyberMint, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(acc.pairingCode)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy pairing code", tint = CyberMint)
                            }
                        }
                    }
                    if (TargetType.fromRaw(acc.targetType) == TargetType.PERSONAL_INBOX) {
                        OutlinedButton(
                            onClick = {
                                val waDigits = acc.e164Number.filter { it.isDigit() }
                                val encodedText = Uri.encode(
                                    "Honest Bot Pro Linking Code: ${acc.pairingCode}\nSession Fingerprint: ${acc.sessionFingerprint.take(12)}\nConfirm to activate bot operations behind your WhatsApp account."
                                )
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waDigits?text=$encodedText"))
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = CyberMint, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send Code via WhatsApp App", color = CyberMint)
                        }
                    }
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("Enter / Confirm Linking Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveAndVerifyLink(acc, codeInput)
                        verifyDialogAccount = null
                        verifyDialogNote = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian)
                ) {
                    Text("Verify & Go LIVE")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    verifyDialogAccount = null
                    verifyDialogNote = null
                }) {
                    Text("Later", color = SageText)
                }
            }
        )
    }

    if (showBroadcastDialog) {
        var broadcastMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            containerColor = SlateEmerald,
            title = { Text("Owner Fleet Broadcast") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Send an owner announcement or command update to all LIVE linked accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SageText
                    )
                    OutlinedTextField(
                        value = broadcastMsg,
                        onValueChange = { broadcastMsg = it },
                        label = { Text("Broadcast Message") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendAdminBroadcast(accounts, broadcastMsg)
                        showBroadcastDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian)
                ) {
                    Text("Dispatch Broadcast")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) {
                    Text("Cancel", color = SageText)
                }
            }
        )
    }

    if (adminSheetAccount != null) {
        OwnerAdminControlSheet(
            account = adminSheetAccount!!,
            onDismiss = { adminSheetAccount = null },
            onSave = { status, role, mode, bot, auto, media, web, grp, quota, blocked, antiLink, antiDel, welcome, sessId ->
                viewModel.updateOwnerAccountControls(
                    account = adminSheetAccount!!,
                    connectionStatus = status,
                    role = role,
                    operatingMode = mode,
                    botEnabled = bot,
                    autoReplyEnabled = auto,
                    mediaDownloadEnabled = media,
                    webSearchEnabled = web,
                    groupDispatchEnabled = grp,
                    dailyQuota = quota,
                    blockedCommands = blocked,
                    antiLinkEnabled = antiLink,
                    antiDeleteEnabled = antiDel,
                    welcomeEnabled = welcome,
                    sessionId = sessId
                )
                adminSheetAccount = null
            },
            onResetQuota = {
                viewModel.resetAccountDailyQuota(adminSheetAccount!!)
            },
            onRotateCode = {
                viewModel.rotatePairingCode(adminSheetAccount!!)
            },
            onTestConnection = {
                viewModel.pingAccountConnection(adminSheetAccount!!)
            },
            onDelete = {
                viewModel.revokeAndDeleteAccount(adminSheetAccount!!)
                adminSheetAccount = null
            },
            onOpenInConsole = {
                val id = adminSheetAccount!!.id
                adminSheetAccount = null
                onSelectAccountForConsole(id)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ACCOUNT LINKING & ADMIN", style = MaterialTheme.typography.labelSmall, color = CyberMint)
                        Text("Link Any WhatsApp Account", style = MaterialTheme.typography.headlineMedium)
                    }
                    OutlinedButton(
                        onClick = { showBroadcastDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("fleet_broadcast_button")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Broadcast", color = ElectricCyan)
                    }
                }
            }

            item {
                // Live Connection Diagnostics & Cloud API Handshake Card
                val waConfigured = viewModel.apiDiagnostics.whatsAppConfigured
                val conn = whatsAppConnection
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightPine),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            conn?.connected == true -> LuminousEmerald.copy(alpha = 0.6f)
                            conn?.connected == false -> SignalCoral.copy(alpha = 0.6f)
                            waConfigured -> CyberMint.copy(alpha = 0.4f)
                            else -> AmberPulse.copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (waConfigured || conn?.connected == true) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (conn?.connected == true || waConfigured) CyberMint else AmberPulse,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (waConfigured) {
                                            "WhatsApp Cloud API Ready (${viewModel.apiDiagnostics.phoneNumberIdMasked})"
                                        } else {
                                            "Local Pairing + WhatsApp App Mode"
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (waConfigured) {
                                            "Encrypted vault & Meta Graph v26.0 active"
                                        } else {
                                            "Add WHATSAPP_ACCESS_TOKEN & PHONE_NUMBER_ID in AI Studio Secrets for automated Cloud API sending."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SageText
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.testWhatsAppCloudConnection(customPhoneNumberId) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("test_wa_connection_button")
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test", color = ElectricCyan)
                            }
                        }

                        if (conn != null) {
                            Surface(
                                color = if (conn.connected) LuminousEmerald.copy(alpha = 0.12f) else SignalCoral.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = conn.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (conn.connected) CyberMint else SignalCoral,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (linkingError != null || accountsState is UiState.Error) {
                item {
                    val errText = linkingError ?: (accountsState as? UiState.Error)?.message ?: "Connection error"
                    Surface(
                        color = SignalCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SignalCoral.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCoral)
                            Text(
                                text = errText,
                                style = MaterialTheme.typography.bodySmall,
                                color = SignalCoral,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    viewModel.clearLinkingError()
                                    viewModel.retryDefaultsSync()
                                }
                            ) {
                                Text("Retry", color = CyberMint)
                            }
                        }
                    }
                }
            }

            item {
                // 3-Step Linking Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateEmerald),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Link New WhatsApp Number or Group", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enter country code + WhatsApp number to generate an encrypted pairing code so the bot operates behind that account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SageText
                        )

                        if (policy != null && !policy.allowNewLinkRequests) {
                            PolicyBlockedBanner(reason = "Owner Policy currently has 'Allow New Account Link Requests' disabled in Vault.")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = targetType == TargetType.PERSONAL_INBOX,
                                onClick = { targetType = TargetType.PERSONAL_INBOX },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Personal Inbox") }
                            )
                            FilterChip(
                                selected = targetType == TargetType.GROUP_TARGET,
                                onClick = { targetType = TargetType.GROUP_TARGET },
                                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Group / Broadcast") }
                            )
                        }

                        if (targetType == TargetType.PERSONAL_INBOX) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showCountryPicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("country_code_picker_button")
                                ) {
                                    Text(countryCode, style = MonoCodeStyle, color = CyberMint, fontWeight = FontWeight.Bold)
                                }
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
                                    label = { Text("WhatsApp Number") },
                                    placeholder = { Text("780549558") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("link_phone_input")
                                )
                            }
                            if (normalizedPreview != null) {
                                Text(
                                    text = "Target WhatsApp E.164: $normalizedPreview",
                                    style = MonoCodeStyle,
                                    color = CyberMint
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("WhatsApp Group ID or Broadcast Handle") },
                                placeholder = { Text("120363049218492@g.us") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("link_group_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Account / Group Name") },
                                placeholder = { Text("Honest VIP Lounge") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("link_display_name_input")
                            )
                            OutlinedTextField(
                                value = customPhoneNumberId,
                                onValueChange = { customPhoneNumberId = it },
                                label = { Text("WABA Phone ID (Opt)") },
                                placeholder = { Text("Default") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinkingMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = linkingMethod == method,
                                    onClick = { linkingMethod = method },
                                    label = { Text(method.label) }
                                )
                            }
                        }

                        if (linkingMethod == LinkingMethod.WEB_PAIR) {
                            OutlinedTextField(
                                value = webPairUrlInput,
                                onValueChange = { webPairUrlInput = it },
                                label = { Text("WebPair / Baileys Bridge URL") },
                                placeholder = { Text("https://your-bot-bridge.example.com") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("link_webpair_url_input")
                            )
                            OutlinedTextField(
                                value = sessionIdInput,
                                onValueChange = { sessionIdInput = it },
                                label = { Text("SESSION_ID (Optional if requesting new code)") },
                                placeholder = { Text("HONEST~SESSION_ID") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("link_session_id_input")
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(AccountRole.VIP, AccountRole.STANDARD, AccountRole.RESTRICTED).forEach { role ->
                                FilterChip(
                                    selected = selectedRole == role,
                                    onClick = { selectedRole = role },
                                    label = { Text(role.label) }
                                )
                            }
                        }

                        if (linkingMethod == LinkingMethod.CLOUD_API) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (viewModel.apiDiagnostics.whatsAppConfigured) {
                                        "Send linking code via WhatsApp Cloud API"
                                    } else {
                                        "Attempt Cloud API send (or pair via WhatsApp app)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SageText,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = dispatchViaApi, onCheckedChange = { dispatchViaApi = it })
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.createAccountLink(
                                    targetType = targetType,
                                    countryCode = countryCode,
                                    rawNumberOrGroupId = phoneInput,
                                    displayName = displayName,
                                    role = selectedRole,
                                    operatingMode = OperatingMode.FULL_AI_MEDIA,
                                    customPhoneNumberId = customPhoneNumberId,
                                    dailyQuota = if (selectedRole == AccountRole.VIP) 1000L else 250L,
                                    dispatchCodeViaWhatsApp = dispatchViaApi,
                                    linkingMethod = linkingMethod,
                                    webPairBridgeUrl = webPairUrlInput,
                                    sessionIdInput = sessionIdInput,
                                    onCreated = { createdAcc, note ->
                                        phoneInput = ""
                                        displayName = ""
                                        verifyDialogNote = note
                                        verifyDialogAccount = createdAcc
                                    }
                                )
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("generate_linking_code_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DeepObsidian,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Connecting & Generating Code...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (linkingMethod == LinkingMethod.WEB_PAIR) {
                                        "Request WebPair / Link Session"
                                    } else {
                                        "Generate Encrypted Linking Code"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Filter chips + Bulk Owner Actions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${accounts.size})") }
                        )
                        ConnectionStatus.entries.forEach { st ->
                            FilterChip(
                                selected = statusFilter == st,
                                onClick = { statusFilter = if (statusFilter == st) null else st },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    if (selectedIds.isNotEmpty()) {
                        val selectedAccounts = accounts.filter { it.id in selectedIds }
                        Surface(
                            color = MidnightPine,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, LuminousEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyberMint,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    viewModel.runBulkAdminAction(selectedAccounts, ConnectionStatus.LIVE, true)
                                    selectedIds.clear()
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyberMint)
                                    Text("Resume", color = CyberMint)
                                }
                                TextButton(onClick = {
                                    viewModel.runBulkAdminAction(selectedAccounts, ConnectionStatus.PAUSED, false)
                                    selectedIds.clear()
                                }) {
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = AmberPulse)
                                    Text("Pause", color = AmberPulse)
                                }
                            }
                        }
                    }
                }
            }

            if (accountsState is UiState.Loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberMint, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Syncing linked WhatsApp accounts...", style = MaterialTheme.typography.bodySmall, color = SageText)
                    }
                }
            }

            items(filteredAccounts, key = { it.id }) { account ->
                val isChecked = account.id in selectedIds
                val connStatus = ConnectionStatus.fromRaw(account.connectionStatus)
                val role = AccountRole.fromRaw(account.role)
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateEmerald),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (connStatus == ConnectionStatus.LIVE) LuminousEmerald.copy(alpha = 0.4f) else MossOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { adminSheetAccount = account }
                        .testTag("linked_account_card_${account.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(account.id) else selectedIds.remove(account.id)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = account.e164Number,
                                    style = MonoCodeStyle,
                                    color = CyberMint
                                )
                            }
                            LivePulseBadge(status = connStatus)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ElevatedPine,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "CODE ${account.pairingCode} • ${role.label} • ${LinkingMethod.fromRaw(account.linkingMethod).name}",
                                    style = MonoCodeStyle,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Quota ${account.messagesUsedToday}/${account.dailyQuota}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SageText
                            )
                        }

                        val progress = if (account.dailyQuota > 0) {
                            (account.messagesUsedToday.toFloat() / account.dailyQuota.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (progress > 0.85f) SignalCoral else LuminousEmerald,
                            trackColor = ElevatedPine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connStatus == ConnectionStatus.AWAITING_CODE) {
                                Button(
                                    onClick = { verifyDialogAccount = account },
                                    colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verify Link")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectAccountForConsole(account.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = CyberMint, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Operate Bot", color = CyberMint)
                                }
                            }
                            OutlinedButton(
                                onClick = { adminSheetAccount = account },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Admin Control", color = ElectricCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerAdminControlSheet(
    account: ConnectedAccount,
    onDismiss: () -> Unit,
    onSave: (
        ConnectionStatus, AccountRole, OperatingMode,
        Boolean, Boolean, Boolean, Boolean, Boolean,
        Long, List<String>,
        Boolean, Boolean, Boolean, String
    ) -> Unit,
    onResetQuota: () -> Unit,
    onRotateCode: () -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit,
    onOpenInConsole: () -> Unit
) {
    var status by remember(account.id) { mutableStateOf(ConnectionStatus.fromRaw(account.connectionStatus)) }
    var role by remember(account.id) { mutableStateOf(AccountRole.fromRaw(account.role)) }
    var mode by remember(account.id) { mutableStateOf(OperatingMode.fromRaw(account.operatingMode)) }
    var botEnabled by remember(account.id) { mutableStateOf(account.botEnabled) }
    var autoReply by remember(account.id) { mutableStateOf(account.autoReplyEnabled) }
    var mediaDl by remember(account.id) { mutableStateOf(account.mediaDownloadEnabled) }
    var webSearch by remember(account.id) { mutableStateOf(account.webSearchEnabled) }
    var groupDispatch by remember(account.id) { mutableStateOf(account.groupDispatchEnabled) }
    var antiLink by remember(account.id) { mutableStateOf(account.antiLinkEnabled) }
    var antiDelete by remember(account.id) { mutableStateOf(account.antiDeleteEnabled) }
    var welcomeEnabled by remember(account.id) { mutableStateOf(account.welcomeEnabled) }
    var sessionId by remember(account.id) { mutableStateOf(account.sessionId) }
    var quotaSlider by remember(account.id) { mutableFloatStateOf(account.dailyQuota.toFloat().coerceIn(10f, 5000f)) }
    val blocked = remember(account.id) { mutableStateListOf<String>().apply { addAll(account.blockedCommands) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateEmerald
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Owner Super-Admin Controls", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${account.displayName} • ${account.e164Number}",
                style = MonoCodeStyle,
                color = CyberMint
            )
            Text(
                "Method: ${LinkingMethod.fromRaw(account.linkingMethod).label} • Fingerprint: ${account.sessionFingerprint}",
                style = MaterialTheme.typography.labelSmall,
                color = SageText
            )

            HorizontalDivider(color = MossOutline)

            Text("Connection Status", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionStatus.entries.forEach { st ->
                    FilterChip(
                        selected = status == st,
                        onClick = { status = st },
                        label = { Text(st.label) }
                    )
                }
            }

            Text("User Role & Governance Tier", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountRole.entries.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = {
                            role = r
                            if (r == AccountRole.BANNED || r == AccountRole.SUSPENDED) {
                                botEnabled = false
                                status = ConnectionStatus.SUSPENDED
                            }
                        },
                        label = { Text(r.label) }
                    )
                }
            }

            Text("Bot Operating Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OperatingMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label) }
                    )
                }
            }

            AdminSwitchRow("Bot Engine Active Behind Account", botEnabled) { botEnabled = it }
            AdminSwitchRow("AI Auto-Responder", autoReply) { autoReply = it }
            AdminSwitchRow("Media Downloads (.play / .video / .dl)", mediaDl) { mediaDl = it }
            AdminSwitchRow("OpenAI Live Internet Search", webSearch) { webSearch = it }
            AdminSwitchRow("Group / Broadcast Dispatch", groupDispatch) { groupDispatch = it }
            AdminSwitchRow("Anti-Link Group Shield", antiLink) { antiLink = it }
            AdminSwitchRow("Anti-Delete Message Recovery", antiDelete) { antiDelete = it }
            AdminSwitchRow("Group Welcome Greeting", welcomeEnabled) { welcomeEnabled = it }

            OutlinedTextField(
                value = sessionId,
                onValueChange = { sessionId = it },
                label = { Text("WebPair SESSION_ID (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Daily Message/Media Quota: ${quotaSlider.toLong()}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = quotaSlider,
                onValueChange = { quotaSlider = it },
                valueRange = 10f..5000f
            )

            Text("Block Specific Commands for User", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(".play", ".video", ".dl", ".menu", ".ping", "/ask", "/news", "/social", "/share").forEach { cmd ->
                    val isBlocked = cmd in blocked
                    FilterChip(
                        selected = isBlocked,
                        onClick = {
                            if (isBlocked) blocked.remove(cmd) else blocked.add(cmd)
                        },
                        label = { Text(if (isBlocked) "Blocked $cmd" else cmd) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onResetQuota, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Usage")
                }
                OutlinedButton(onClick = onRotateCode, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rotate Code")
                }
            }

            Button(
                onClick = {
                    onSave(
                        status, role, mode,
                        botEnabled, autoReply, mediaDl, webSearch, groupDispatch,
                        quotaSlider.toLong(), blocked.toList(),
                        antiLink, antiDelete, welcomeEnabled, sessionId
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_admin_controls_button")
            ) {
                Text("Save Owner Admin Controls", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onOpenInConsole, modifier = Modifier.weight(1f)) {
                    Text("Operate in Console", color = CyberMint)
                }
                OutlinedButton(onClick = onTestConnection, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test Link", color = ElectricCyan)
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = SignalCoral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Revoke & Delete Account Link", color = SignalCoral)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AdminSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
