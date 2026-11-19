package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ApiDiagnostics
import com.example.model.ConnectionStatus
import com.example.model.CountryCodeOption
import com.example.model.CountryCodes
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

@Composable
fun LivePulseBadge(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isLive = status == ConnectionStatus.LIVE
    val color = when (status) {
        ConnectionStatus.LIVE -> CyberMint
        ConnectionStatus.AWAITING_CODE -> AmberPulse
        ConnectionStatus.PAUSED -> ElectricCyan
        ConnectionStatus.SUSPENDED, ConnectionStatus.REVOKED -> SignalCoral
    }

    val pulse = if (isLive) {
        val transition = rememberInfiniteTransition(label = "live_pulse")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )
        alpha
    } else {
        1f
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ApiStatusBanner(
    diagnostics: ApiDiagnostics,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (diagnostics.openAiConfigured && diagnostics.whatsAppConfigured) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = LuminousEmerald.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LuminousEmerald.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberMint, modifier = Modifier.size(18.dp))
                Text(
                    text = "OpenAI Web Search & WhatsApp Cloud API (${diagnostics.phoneNumberIdMasked}) active",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberMint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenVault),
            color = AmberPulse.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AmberPulse.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = "API Secrets Notice", tint = AmberPulse, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "API Keys Required in AI Studio Secrets",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberPulse
                    )
                    Text(
                        text = buildString {
                            if (!diagnostics.openAiConfigured) append("OPENAI_API_KEY missing • ")
                            if (!diagnostics.whatsAppConfigured) append("WHATSAPP_ACCESS_TOKEN / PHONE_NUMBER_ID missing • ")
                            append("Tap Vault for setup details.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EngagementTrendChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val chartPoints = if (values.isEmpty()) listOf(2f, 5f, 4f, 8f, 6f, 11f, 9f) else values
    val maxVal = (chartPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
    ) {
        val w = size.width
        val h = size.height
        val padY = 14.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(10f)

        // Subtle horizontal grid lines
        for (i in 0..2) {
            val y = padY + usableH * (i / 2f)
            drawLine(
                color = MossOutline.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val count = chartPoints.size
        val stepX = if (count > 1) w / (count - 1).toFloat() else w
        val linePath = Path()
        val fillPath = Path()

        val offsets = chartPoints.mapIndexed { idx, value ->
            val x = if (count > 1) idx * stepX else w / 2f
            val y = padY + usableH * (1f - (value / maxVal).coerceIn(0f, 1f))
            Offset(x, y)
        }

        offsets.forEachIndexed { idx, pt ->
            if (idx == 0) {
                linePath.moveTo(pt.x, pt.y)
                fillPath.moveTo(pt.x, h)
                fillPath.lineTo(pt.x, pt.y)
            } else {
                val prev = offsets[idx - 1]
                val midX = (prev.x + pt.x) / 2f
                linePath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                fillPath.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
            }
        }
        if (offsets.isNotEmpty()) {
            fillPath.lineTo(offsets.last().x, h)
            fillPath.close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(LuminousEmerald.copy(alpha = 0.36f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = CyberMint,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        offsets.forEach { pt ->
            drawCircle(color = DeepObsidian, radius = 4.5.dp.toPx(), center = pt)
            drawCircle(color = CyberMint, radius = 2.8.dp.toPx(), center = pt)
        }
    }
}

@Composable
fun SupportContactCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidnightPine),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MossOutline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = LuminousEmerald.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Honest Tech Support",
                        tint = CyberMint
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pro Made by Honest Tech Support",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "+263 780549558",
                    style = MonoCodeStyle,
                    color = CyberMint,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+263780549558")))
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("support_call_button")
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call Honest Tech Support", tint = ElectricCyan)
            }
            Surface(
                color = LuminousEmerald,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/263780549558")))
                    }
                    .testTag("support_whatsapp_button")
            ) {
                Text(
                    text = "WhatsApp",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepObsidian,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun CountryCodePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (CountryCodeOption) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) CountryCodes.presets
        else CountryCodes.presets.filter {
            it.country.lowercase().contains(q) || it.code.contains(q) || it.flag.lowercase().contains(q)
        }
    }
    val customDigits = remember(search) { search.filter { it.isDigit() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateEmerald,
        title = {
            Text("Select Country Code", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search country or type +code (e.g. +264)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (customDigits.isNotEmpty() && customDigits.length <= 4) {
                    val customCode = "+$customDigits"
                    Surface(
                        color = LuminousEmerald.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LuminousEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(CountryCodeOption(customCode, "Custom Country Code", "INT"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Use custom code", style = MaterialTheme.typography.bodyMedium, color = CyberMint)
                            Text(customCode, style = MonoCodeStyle, color = CyberMint, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { item ->
                        Surface(
                            color = ElevatedPine,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.flag}  ${item.country}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.code,
                                    style = MonoCodeStyle,
                                    color = CyberMint,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SageText)
            }
        }
    )
}

@Composable
fun PolicyBlockedBanner(
    reason: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SignalCoral.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SignalCoral.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCoral)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = SignalCoral
            )
        }
    }
}
