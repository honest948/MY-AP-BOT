package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.data.BotConfigGenerator
import com.example.model.ConnectedAccount
import com.example.model.OwnerPolicy
import com.example.ui.theme.CyberMint
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LuminousEmerald
import com.example.ui.theme.MossOutline
import com.example.ui.theme.SageText
import com.example.ui.theme.SlateEmerald

@Composable
fun ProBotAutomationCard(
    policy: OwnerPolicy,
    accounts: List<ConnectedAccount>,
    isBusy: Boolean,
    onSaveAutomation: (OwnerPolicy) -> Unit,
    onCopyBridgeEnv: (String) -> Unit
) {
    var botName by remember(policy.botName) { mutableStateOf(policy.botName.ifBlank { "Honest Bot Pro" }) }
    var prefix by remember(policy.commandPrefix) { mutableStateOf(policy.commandPrefix.ifBlank { "." }) }
    var botMode by remember(policy.botMode) { mutableStateOf(policy.botMode.ifBlank { "public" }) }
    var webPairUrl by remember(policy.webPairBridgeUrl) { mutableStateOf(policy.webPairBridgeUrl) }
    var sessionId by remember(policy.sessionId) { mutableStateOf(policy.sessionId) }

    var readMessages by remember(policy.readMessages) { mutableStateOf(policy.readMessages) }
    var alwaysOnline by remember(policy.alwaysOnline) { mutableStateOf(policy.alwaysOnline) }
    var autoTyping by remember(policy.autoTyping) { mutableStateOf(policy.autoTyping) }
    var autoRecording by remember(policy.autoRecording) { mutableStateOf(policy.autoRecording) }
    var autoReact by remember(policy.autoReact) { mutableStateOf(policy.autoReact) }
    var ownerReact by remember(policy.ownerReact) { mutableStateOf(policy.ownerReact) }
    var autoViewStatus by remember(policy.autoViewStatus) { mutableStateOf(policy.autoViewStatus) }
    var autoLikeStatus by remember(policy.autoLikeStatus) { mutableStateOf(policy.autoLikeStatus) }
    var autoDownloader by remember(policy.autoDownloader) { mutableStateOf(policy.autoDownloader) }

    var antiLinkMode by remember(policy.antiLinkMode) { mutableStateOf(policy.antiLinkMode) }
    var antiDeletePath by remember(policy.antiDeletePath) { mutableStateOf(policy.antiDeletePath) }
    var antiEditPath by remember(policy.antiEditPath) { mutableStateOf(policy.antiEditPath) }
    var antiCallEnabled by remember(policy.antiCallEnabled) { mutableStateOf(policy.antiCallEnabled) }
    var rejectMsg by remember(policy.callRejectMessage) { mutableStateOf(policy.callRejectMessage) }
    var antiStatusEnabled by remember(policy.antiStatusEnabled) { mutableStateOf(policy.antiStatusEnabled) }

    var welcomeEnabled by remember(policy.welcomeEnabled) { mutableStateOf(policy.welcomeEnabled) }
    var goodbyeEnabled by remember(policy.goodbyeEnabled) { mutableStateOf(policy.goodbyeEnabled) }
    var welcomeMsg by remember(policy.welcomeMessage) { mutableStateOf(policy.welcomeMessage) }
    var goodbyeMsg by remember(policy.goodbyeMessage) { mutableStateOf(policy.goodbyeMessage) }

    var sudoCsv by remember(policy.sudoNumbers) { mutableStateOf(policy.sudoNumbers.joinToString(", ")) }
    var bannedCsv by remember(policy.bannedNumbers) { mutableStateOf(policy.bannedNumbers.joinToString(", ")) }

    fun buildUpdatedPolicy(): OwnerPolicy = policy.copy(
        botName = botName.trim().ifBlank { "Honest Bot Pro" },
        commandPrefix = prefix.trim().ifBlank { "." }.take(4),
        botMode = botMode,
        webPairBridgeUrl = webPairUrl.trim(),
        sessionId = sessionId.trim(),
        readMessages = readMessages,
        alwaysOnline = alwaysOnline,
        autoTyping = autoTyping,
        autoRecording = autoRecording,
        autoReact = autoReact,
        ownerReact = ownerReact,
        autoViewStatus = autoViewStatus,
        autoLikeStatus = autoLikeStatus,
        autoDownloader = autoDownloader,
        antiLinkMode = antiLinkMode,
        antiDeletePath = antiDeletePath,
        antiEditPath = antiEditPath,
        antiCallEnabled = antiCallEnabled,
        callRejectMessage = rejectMsg.trim(),
        antiStatusEnabled = antiStatusEnabled,
        welcomeEnabled = welcomeEnabled,
        goodbyeEnabled = goodbyeEnabled,
        welcomeMessage = welcomeMsg.trim(),
        goodbyeMessage = goodbyeMsg.trim(),
        sudoNumbers = sudoCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        bannedNumbers = bannedCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateEmerald),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MossOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberMint)
                Spacer(Modifier.width(8.dp))
                Text("Pro Bot Engine, Presence & Anti-Protection", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Configure command prefix, operating mode, Baileys WebPair bridge, status automation, anti-link/anti-delete/anti-call shields, and export bridge config.env.",
                style = MaterialTheme.typography.bodySmall,
                color = SageText
            )

            OutlinedTextField(
                value = botName,
                onValueChange = { botName = it },
                label = { Text("Bot Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Command Prefix", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(".", "/", "!").forEach { p ->
                    FilterChip(
                        selected = prefix == p,
                        onClick = { prefix = p },
                        label = { Text(p, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Text("Bot Access Mode", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("public", "private", "inbox", "groups").forEach { mode ->
                    FilterChip(
                        selected = botMode == mode,
                        onClick = { botMode = mode },
                        label = { Text(mode.uppercase()) }
                    )
                }
            }

            OutlinedTextField(
                value = webPairUrl,
                onValueChange = { webPairUrl = it },
                label = { Text("WebPair / Baileys Bridge URL (Optional)") },
                placeholder = { Text("https://your-bot-bridge.example.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("webpair_bridge_url_input")
            )
            OutlinedTextField(
                value = sessionId,
                onValueChange = { sessionId = it },
                label = { Text("Default SESSION_ID (Optional)") },
                placeholder = { Text("HONEST~SESSION_TOKEN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MossOutline)
            Text("Presence & Status Automation", style = MaterialTheme.typography.titleSmall, color = CyberMint)

            AutomationSwitch("Auto-Read Messages (Blue Ticks)", readMessages) { readMessages = it }
            AutomationSwitch("Always Online Presence", alwaysOnline) { alwaysOnline = it }
            AutomationSwitch("Auto-View WhatsApp Statuses", autoViewStatus) { autoViewStatus = it }
            AutomationSwitch("Auto-Like WhatsApp Statuses", autoLikeStatus) { autoLikeStatus = it }
            AutomationSwitch("Auto-Downloader (Detect Social Links)", autoDownloader) { autoDownloader = it }
            AutomationSwitch("Auto-React to Messages", autoReact) { autoReact = it }
            AutomationSwitch("Owner Auto-React Badge", ownerReact) { ownerReact = it }

            Text("Auto-Typing Indicator", style = MaterialTheme.typography.labelMedium, color = SageText)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("off", "all", "inbox", "group").forEach { opt ->
                    FilterChip(selected = autoTyping == opt, onClick = { autoTyping = opt }, label = { Text(opt.uppercase()) })
                }
            }

            Text("Auto-Recording Indicator", style = MaterialTheme.typography.labelMedium, color = SageText)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("off", "all", "inbox", "group").forEach { opt ->
                    FilterChip(selected = autoRecording == opt, onClick = { autoRecording = opt }, label = { Text(opt.uppercase()) })
                }
            }

            HorizontalDivider(color = MossOutline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Anti-Abuse Security Shields", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
            }

            Text("Anti-Link Mode", style = MaterialTheme.typography.labelMedium, color = SageText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off", "warn", "delete").forEach { m ->
                    FilterChip(selected = antiLinkMode == m, onClick = { antiLinkMode = m }, label = { Text(m.uppercase()) })
                }
            }

            Text("Anti-Delete Recovery Path", style = MaterialTheme.typography.labelMedium, color = SageText)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("off", "inbox", "group", "all").forEach { p ->
                    FilterChip(selected = antiDeletePath == p, onClick = { antiDeletePath = p }, label = { Text(p.uppercase()) })
                }
            }

            Text("Anti-Edit Recovery Path", style = MaterialTheme.typography.labelMedium, color = SageText)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("off", "inbox", "group", "all").forEach { p ->
                    FilterChip(selected = antiEditPath == p, onClick = { antiEditPath = p }, label = { Text(p.uppercase()) })
                }
            }

            AutomationSwitch("Anti-Status Mention Protection", antiStatusEnabled) { antiStatusEnabled = it }
            AutomationSwitch("Anti-Call Auto-Reject", antiCallEnabled) { antiCallEnabled = it }
            if (antiCallEnabled) {
                OutlinedTextField(
                    value = rejectMsg,
                    onValueChange = { rejectMsg = it },
                    label = { Text("Call Reject Message") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MossOutline)
            Text("Group Welcome & Access Control", style = MaterialTheme.typography.titleSmall, color = CyberMint)

            AutomationSwitch("Welcome Message on Member Join", welcomeEnabled) { welcomeEnabled = it }
            if (welcomeEnabled) {
                OutlinedTextField(
                    value = welcomeMsg,
                    onValueChange = { welcomeMsg = it },
                    label = { Text("Welcome Template (@user @group @count)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AutomationSwitch("Goodbye Message on Member Leave", goodbyeEnabled) { goodbyeEnabled = it }
            if (goodbyeEnabled) {
                OutlinedTextField(
                    value = goodbyeMsg,
                    onValueChange = { goodbyeMsg = it },
                    label = { Text("Goodbye Template (@user @group)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = sudoCsv,
                onValueChange = { sudoCsv = it },
                label = { Text("SUDO Admin Numbers (comma-separated)") },
                placeholder = { Text("+263780549558, +27...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bannedCsv,
                onValueChange = { bannedCsv = it },
                label = { Text("Banned Numbers (comma-separated)") },
                placeholder = { Text("+263770000000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSaveAutomation(buildUpdatedPolicy()) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_bot_automation_button")
            ) {
                Text("Save Pro Bot Automation", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val env = BotConfigGenerator.generateBridgeConfigEnv(buildUpdatedPolicy(), accounts)
                    onCopyBridgeEnv(env)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("copy_bridge_env_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Bridge config.env (Docker / Heroku)", color = ElectricCyan)
            }
        }
    }
}

@Composable
private fun AutomationSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
