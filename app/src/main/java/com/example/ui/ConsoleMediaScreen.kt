package com.example.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BotCommand
import com.example.model.CommandCategory
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.MediaJob
import com.example.model.MediaType
import com.example.model.OwnerPolicy
import com.example.model.PlatformKey
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
import com.example.ui.theme.SlateEmerald
import com.example.viewmodel.CommandCenterViewModel
import com.example.viewmodel.ConsoleMediaViewModel

@Composable
fun ConsoleMediaScreen(
    commandCenterVm: CommandCenterViewModel,
    consoleMediaVm: ConsoleMediaViewModel
) {
    val policyState by commandCenterVm.ownerPolicyState.collectAsStateWithLifecycle()
    val accountsState by commandCenterVm.accountsState.collectAsStateWithLifecycle()
    val commandsState by commandCenterVm.commandsState.collectAsStateWithLifecycle()
    val mediaJobsState by commandCenterVm.mediaJobsState.collectAsStateWithLifecycle()
    val platformsState by commandCenterVm.platformsState.collectAsStateWithLifecycle()
    val consoleState by consoleMediaVm.uiState.collectAsStateWithLifecycle()

    val policy = (policyState as? UiState.Success)?.data ?: OwnerPolicy()
    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val commands = (commandsState as? UiState.Success)?.data.orEmpty()
    val mediaJobs = (mediaJobsState as? UiState.Success)?.data.orEmpty()
    val platforms = (platformsState as? UiState.Success)?.data.orEmpty()

    val customExtractorUrl = remember(platforms) {
        platforms.firstOrNull { it.platformKey == PlatformKey.MEDIA_EXTRACTOR.name }
            ?.endpointOrHandle
            ?.takeIf { it.startsWith("http") && !it.contains("example") }
    }

    val activeAccount: ConnectedAccount? = remember(accounts, consoleState.selectedAccountId) {
        accounts.firstOrNull { it.id == consoleState.selectedAccountId }
            ?: accounts.firstOrNull { ConnectionStatus.fromRaw(it.connectionStatus) == ConnectionStatus.LIVE }
            ?: accounts.firstOrNull()
    }

    var commandInput by remember { mutableStateOf(".play dj onilo mix") }
    var dispatchViaWhatsApp by remember { mutableStateOf(true) }
    var showAddCommandDialog by remember { mutableStateOf(false) }

    if (showAddCommandDialog) {
        AddCustomCommandDialog(
            onDismiss = { showAddCommandDialog = false },
            onSave = { trigger, title, desc, cat, template, web ->
                commandCenterVm.saveCustomCommand(trigger, title, desc, cat, template, web)
                showAddCommandDialog = false
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BOT CONSOLE & MEDIA STUDIO", style = MaterialTheme.typography.labelSmall, color = CyberMint)
                        Text("Commands & .play Downloads", style = MaterialTheme.typography.headlineMedium)
                    }
                    OutlinedButton(
                        onClick = { showAddCommandDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_custom_command_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CyberMint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Command", color = CyberMint)
                    }
                }
            }

            item {
                // Active Linked Account Switcher
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightPine),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Operate Behind Linked Account / Group",
                            style = MaterialTheme.typography.labelLarge,
                            color = SageText
                        )
                        if (accounts.isEmpty()) {
                            Text(
                                "No linked WhatsApp accounts yet. Add one in the Linking tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberPulse
                            )
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                accounts.forEach { acc ->
                                    val selected = acc.id == activeAccount?.id
                                    val isGroup = TargetType.fromRaw(acc.targetType) == TargetType.GROUP_TARGET
                                    FilterChip(
                                        selected = selected,
                                        onClick = { consoleMediaVm.selectAccount(acc.id) },
                                        leadingIcon = {
                                            Icon(
                                                if (isGroup) Icons.Default.Group else Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text("${acc.displayName} (${acc.e164Number})", maxLines = 1)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (consoleState.policyBlockedReason != null) {
                item {
                    PolicyBlockedBanner(reason = consoleState.policyBlockedReason!!)
                }
            }

            item {
                // Command & .play Runner Card
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
                        Text("Execute Bot Command or Media Search", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                ".play dj onilo mix",
                                ".video trending music video",
                                ".dl https://",
                                ".menu",
                                ".ping",
                                ".antilink delete",
                                ".welcome on",
                                "/news latest global headlines",
                                "/social viral trends on X and TikTok",
                                "/share Honest Tech Support guide"
                            ).forEach { sample ->
                                Surface(
                                    color = ElevatedPine,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, MossOutline),
                                    modifier = Modifier.clickable { commandInput = sample }
                                ) {
                                    Text(
                                        text = if (" " in sample) sample.substringBefore(" ") + " ..." else sample,
                                        style = MonoCodeStyle,
                                        color = CyberMint,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            label = { Text("Command (Prefix ${policy.commandPrefix}: .play, .menu, .ping, .antilink, /ask)") },
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_command_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeAccount != null) {
                                    "Auto-send to ${activeAccount.e164Number} (${TargetType.fromRaw(activeAccount.targetType).label})"
                                } else {
                                    "Dispatch via WhatsApp Cloud API"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SageText,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = dispatchViaWhatsApp,
                                onCheckedChange = { dispatchViaWhatsApp = it }
                            )
                        }

                        Button(
                            onClick = {
                                consoleMediaVm.executeInput(
                                    rawInput = commandInput,
                                    policy = policy,
                                    account = activeAccount,
                                    commands = commands,
                                    dispatchToWhatsApp = dispatchViaWhatsApp,
                                    customExtractorUrl = customExtractorUrl
                                )
                            },
                            enabled = !consoleState.isRunningCommand && !consoleState.isSearchingMedia,
                            colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("run_bot_command_button")
                        ) {
                            if (consoleState.isRunningCommand || consoleState.isSearchingMedia) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DeepObsidian, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (consoleState.isSearchingMedia) "Searching Platforms..." else "Executing OpenAI Web Search...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Behind Linked Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (consoleState.feedbackMessage != null) {
                item {
                    Surface(
                        color = ElevatedPine,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = consoleState.feedbackMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Discovered Music / Video Results (.play / .video / .dl)
            if (consoleState.mediaMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "Matched Social Media Tracks & Videos",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberMint
                    )
                }
                items(consoleState.mediaMatches, key = { it.id }) { match ->
                    val isDownloading = consoleState.downloadingMatchId == match.id
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateEmerald),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
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
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${match.platform.label} • ${match.mediaType.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(match.durationLabel, style = MonoCodeStyle, color = SageText)
                            }
                            Text(match.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${match.artistOrChannel} • ${match.sourceUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isDownloading) {
                                LinearProgressIndicator(
                                    progress = { consoleState.downloadProgress },
                                    color = CyberMint,
                                    trackColor = ElevatedPine,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "Downloading & uploading to WhatsApp (${(consoleState.downloadProgress * 100).toInt()}%)...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberMint
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        consoleMediaVm.downloadAndUploadMatch(
                                            match = match,
                                            policy = policy,
                                            targetAccount = activeAccount,
                                            uploadToWhatsApp = true,
                                            customExtractorUrl = customExtractorUrl
                                        )
                                    },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (TargetType.fromRaw(activeAccount?.targetType.orEmpty()) == TargetType.GROUP_TARGET) {
                                            "Download & Upload Group"
                                        } else {
                                            "Download & Send Inbox"
                                        }
                                    )
                                }
                                OutlinedButton(
                                    onClick = { consoleMediaVm.sendMediaLinkFallback(match, activeAccount) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share link to WhatsApp", tint = CyberMint, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (consoleState.lastDownloadedFile != null) {
                item {
                    OutlinedButton(
                        onClick = { consoleMediaVm.saveLastDownloadedToDevice() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = CyberMint)
                        Spacer(Modifier.width(8.dp))
                        Text("Save \"${consoleState.lastDownloadedTitle}\" to Device Downloads", color = CyberMint)
                    }
                }
            }

            if (consoleState.lastResult != null) {
                val res = consoleState.lastResult!!
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightPine),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, LuminousEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${res.commandUsed} → ${res.targetLabel}", style = MonoCodeStyle, color = CyberMint)
                                Text("${res.latencyMs}ms", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                            Text(res.responseText, style = MaterialTheme.typography.bodyMedium)
                            if (res.citations.isNotEmpty()) {
                                Text("Live Web Sources:", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                res.citations.take(4).forEach { url ->
                                    Text("• $url", style = MaterialTheme.typography.bodySmall, color = SageText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Registered Bot Commands", style = MaterialTheme.typography.titleMedium)
            }

            items(commands, key = { it.id }) { cmd ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedPine),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MossOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { commandInput = "${cmd.trigger} " }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (CommandCategory.fromRaw(cmd.category)) {
                                CommandCategory.MEDIA_AUDIO -> Icons.Default.MusicNote
                                CommandCategory.MEDIA_VIDEO -> Icons.Default.VideoLibrary
                                else -> Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = null,
                            tint = CyberMint
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cmd.trigger, style = MonoCodeStyle, color = CyberMint, fontWeight = FontWeight.Bold)
                                Text(cmd.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (cmd.ownerOnly) {
                                    Text("OWNER", style = MaterialTheme.typography.labelSmall, color = AmberPulse)
                                }
                            }
                            Text(cmd.description, style = MaterialTheme.typography.bodySmall, color = SageText)
                            if (cmd.aliases.isNotEmpty()) {
                                Text(
                                    text = "Aliases: ${cmd.aliases.joinToString(", ")}",
                                    style = MonoCodeStyle,
                                    color = ElectricCyan
                                )
                            }
                        }
                        Switch(
                            checked = cmd.commandEnabled,
                            onCheckedChange = { commandCenterVm.toggleCommand(cmd, it) }
                        )
                    }
                }
            }

            if (mediaJobs.isNotEmpty()) {
                item {
                    Text("Recent Media Download & Upload Jobs", style = MaterialTheme.typography.titleMedium)
                }
                items(mediaJobs.take(6), key = { it.id }) { job ->
                    MediaJobRow(job)
                }
            }
        }
    }
}

@Composable
private fun MediaJobRow(job: MediaJob) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateEmerald),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MossOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${job.sourcePlatform} • ${job.mediaType} → ${job.targetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SageText
                )
            }
            Text(job.status, style = MaterialTheme.typography.labelSmall, color = CyberMint)
        }
    }
}

@Composable
private fun AddCustomCommandDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, CommandCategory, String, Boolean) -> Unit
) {
    var trigger by remember { mutableStateOf("/promo") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("Find latest updates and share pack for: {{query}}") }
    var category by remember { mutableStateOf(CommandCategory.SHARE) }
    var usesWeb by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateEmerald,
        title = { Text("Create Custom Bot Command") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Trigger (e.g. .mix or /promo)") }, singleLine = true)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text("Prompt Template ({{query}})") })
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommandCategory.entries.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use OpenAI Live Web Search", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = usesWeb, onCheckedChange = { usesWeb = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(trigger, title, description, category, template, usesWeb) },
                colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian)
            ) {
                Text("Save Command")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SageText) }
        }
    )
}
