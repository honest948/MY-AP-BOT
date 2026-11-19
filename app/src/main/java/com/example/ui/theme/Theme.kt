package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val HonestDarkColorScheme = darkColorScheme(
    primary = LuminousEmerald,
    onPrimary = DeepObsidian,
    primaryContainer = DeepEmerald,
    onPrimaryContainer = MintMist,
    secondary = CyberMint,
    onSecondary = DeepObsidian,
    secondaryContainer = SlateEmerald,
    onSecondaryContainer = MintMist,
    tertiary = ElectricCyan,
    onTertiary = DeepObsidian,
    tertiaryContainer = DeepCyan,
    onTertiaryContainer = CrispWhite,
    background = DeepObsidian,
    onBackground = CrispWhite,
    surface = MidnightPine,
    onSurface = CrispWhite,
    surfaceVariant = SlateEmerald,
    onSurfaceVariant = SageText,
    surfaceContainerLowest = DeepObsidian,
    surfaceContainerLow = MidnightPine,
    surfaceContainer = SlateEmerald,
    surfaceContainerHigh = ElevatedPine,
    outline = MossOutline,
    outlineVariant = SubtleMoss,
    error = SignalCoral,
    onError = DeepObsidian,
    errorContainer = DeepCoral,
    onErrorContainer = CrispWhite
)

private val HonestShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HonestBotProTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HonestDarkColorScheme,
        typography = HonestBotTypography,
        shapes = HonestShapes,
        content = content
    )
}
