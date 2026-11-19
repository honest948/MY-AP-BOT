package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.EngagementEvent
import com.example.model.EventStatus
import com.example.model.GlobalFleetState
import com.example.model.MediaJob
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

@Composable
fun DashboardScreen(
    viewModel: CommandCenterViewModel,
    onNavigateLinking: () -> Unit,
    onNavigateConsole: () -> Unit,
    onNavigateVault: () -> Unit
) {
    val policyState by viewModel.ownerPolicyState.collectAsStateWithLifecycle()
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    val mediaJobsState by viewModel.mediaJobsState.collectAsStateWithLifecycle()

    val policy = (policyState as? UiState.Success)?.data ?: OwnerPolicy()
    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val mediaJobs = (mediaJobsState as? UiState.Success)?.data.orEmpty()

    val liveCount = accounts.count { ConnectionStatus.fromRaw(it.connectionStatus) == ConnectionStatus.LIVE }
    val awaitingCount = accounts.count { ConnectionStatus.fromRaw(it.connectionStatus) == ConnectionStatus.AWAITING_CODE }
    val totalDailyMessages = accounts.sumOf { it.messagesUsedToday }
    val avgLatency = if (events.isNotEmpty()) events.map { it.latencyMs }.average().toLong() else 0L

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Hero Command Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onNavigateLinking)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_command_banner),
                        contentDescription = "Command Dashboard Hero",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(DeepObsidian.copy(alpha = 0.94f), DeepObsidian.copy(alpha = 0.58f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "HONEST BOT PRO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberMint
                                )
                                Text(
                                    text = "Owner Command Center",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            LivePulseBadge(
                                status = if (GlobalFleetState.fromRaw(policy.globalFleetState) == GlobalFleetState.PAUSED_ALL) {
                                    ConnectionStatus.PAUSED
                                } else {
                                    ConnectionStatus.LIVE
                                }
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$liveCount LIVE linked • $awaitingCount awaiting code",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                ApiStatusBanner(
                    diagnostics = viewModel.apiDiagnostics,
                    onOpenVault = onNavigateVault
                )
            }

            item {
                // Global Super-Admin Master Switch Card
                val currentFleet = GlobalFleetState.fromRaw(policy.globalFleetState)
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
                            Icon(Icons.Default.Shield, contentDescription = null, tint = CyberMint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Global Fleet Master Control",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = currentFleet.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = SageText
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlobalFleetState.entries.forEach { state ->
                                val selected = currentFleet == state
                                val activeColor = when (state) {
                                    GlobalFleetState.ONLINE -> LuminousEmerald
                                    GlobalFleetState.VIP_LOCKDOWN -> ElectricCyan
                                    GlobalFleetState.MAINTENANCE -> AmberPulse
                                    GlobalFleetState.PAUSED_ALL -> SignalCoral
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setGlobalFleetState(state, policy) },
                                    label = { Text(state.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeColor.copy(alpha = 0.22f),
                                        selectedLabelColor = activeColor
                                    ),
                                    modifier = Modifier.testTag("fleet_state_${state.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            item {
                // 2x2 KPI Grid
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiTile(
                            title = "Live Connections",
                            value = "$liveCount / ${accounts.size}",
                            subtitle = "Encrypted links",
                            icon = Icons.Default.Link,
                            accent = CyberMint,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            title = "Bot Operations",
                            value = "${events.size + totalDailyMessages}",
                            subtitle = "Messages & runs",
                            icon = Icons.Default.Bolt,
                            accent = LuminousEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiTile(
                            title = ".play Media Jobs",
                            value = "${mediaJobs.size}",
                            subtitle = "Audio & video sync",
                            icon = Icons.Default.MusicNote,
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            title = "Avg AI Latency",
                            value = if (avgLatency > 0) "${avgLatency}ms" else "Ready",
                            subtitle = "OpenAI web search",
                            icon = Icons.Default.Speed,
                            accent = AmberPulse,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                // Engagement Analytics Trend Card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("User Engagement & Traffic", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "REAL-TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberMint
                            )
                        }
                        val series = if (events.size >= 4) {
                            events.take(7).reversed().mapIndexed { i, e -> (e.latencyMs / 150f).coerceIn(2f, 14f) + i }
                        } else {
                            listOf(3f, 6f, 5f, 9f, 7f, 12f, (events.size + liveCount * 2).toFloat().coerceAtLeast(4f))
                        }
                        EngagementTrendChart(values = series)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateLinking,
                                colors = ButtonDefaults.buttonColors(containerColor = LuminousEmerald, contentColor = DeepObsidian),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dashboard_link_account_button")
                            ) {
                                Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Link Account", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onNavigateConsole,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dashboard_open_console_button")
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run .play / Bot", color = ElectricCyan)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Live Operations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (events.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MidnightPine),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MossOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SageText, modifier = Modifier.size(28.dp))
                            Text("No bot interactions logged yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Link a WhatsApp account or run .play / /news in the Bot Console to watch live analytics populate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageText
                            )
                        }
                    }
                }
            } else {
                items(events.take(8), key = { it.id }) { evt ->
                    EventRowCard(evt)
                }
            }

            item {
                SupportContactCard()
            }
        }
    }
}

@Composable
private fun KpiTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SlateEmerald),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MossOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = SageText)
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
        }
    }
}

@Composable
private fun EventRowCard(event: EngagementEvent) {
    val st = EventStatus.fromRaw(event.status)
    val badgeColor = when (st) {
        EventStatus.DELIVERED -> CyberMint
        EventStatus.PROCESSED -> ElectricCyan
        EventStatus.BLOCKED -> AmberPulse
        EventStatus.FAILED -> SignalCoral
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedPine),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MossOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = event.commandTrigger,
                    style = MonoCodeStyle,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.e164Number,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = event.responsePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = SageText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${event.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor
            )
        }
    }
}
