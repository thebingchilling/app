package dev.tidewall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import dev.tidewall.data.ThemeMode

// FlClash's default palette: Material 3 "content" scheme from seed #D8C0C3,
// used when "Use system colors" (dynamic color) is off or unavailable.
private val Light = lightColorScheme(
    primary = Color(0xFF6C5A5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8C0C3),
    onPrimaryContainer = Color(0xFF5F4D50),
    inversePrimary = Color(0xFFD9C1C4),
    secondary = Color(0xFF665C5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDDFE0),
    onSecondaryContainer = Color(0xFF6C6263),
    tertiary = Color(0xFF6B5B4E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7C2B2),
    onTertiaryContainer = Color(0xFF5E4F43),
    background = Color(0xFFFEF8F7),
    onBackground = Color(0xFF1D1B1B),
    surface = Color(0xFFFEF8F7),
    onSurface = Color(0xFF1D1B1B),
    surfaceVariant = Color(0xFFEEDFE0),
    onSurfaceVariant = Color(0xFF4E4446),
    surfaceTint = Color(0xFF6C5A5C),
    inverseSurface = Color(0xFF323030),
    inverseOnSurface = Color(0xFFF6EFEF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF807475),
    outlineVariant = Color(0xFFD1C3C4),
    scrim = Color(0xFF000000),
    surfaceContainer = Color(0xFFF3ECEC),
    surfaceContainerHigh = Color(0xFFEDE7E6),
    surfaceContainerHighest = Color(0xFFE7E1E1),
    surfaceContainerLow = Color(0xFFF9F2F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF5DCDF),
    onPrimary = Color(0xFF3C2C2F),
    primaryContainer = Color(0xFFD8C0C3),
    onPrimaryContainer = Color(0xFF5F4D50),
    inversePrimary = Color(0xFF6C5A5C),
    secondary = Color(0xFFD1C3C4),
    onSecondary = Color(0xFF362E2F),
    secondaryContainer = Color(0xFF504748),
    onSecondaryContainer = Color(0xFFC2B5B6),
    tertiary = Color(0xFFF4DECD),
    onTertiary = Color(0xFF3B2E23),
    tertiaryContainer = Color(0xFFD7C2B2),
    onTertiaryContainer = Color(0xFF5E4F43),
    background = Color(0xFF151313),
    onBackground = Color(0xFFE7E1E1),
    surface = Color(0xFF151313),
    onSurface = Color(0xFFE7E1E1),
    surfaceVariant = Color(0xFF4E4446),
    onSurfaceVariant = Color(0xFFD1C3C4),
    surfaceTint = Color(0xFFD9C1C4),
    inverseSurface = Color(0xFFE7E1E1),
    inverseOnSurface = Color(0xFF323030),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9A8E8F),
    outlineVariant = Color(0xFF4E4446),
    scrim = Color(0xFF000000),
    surfaceContainer = Color(0xFF211F1F),
    surfaceContainerHigh = Color(0xFF2C2929),
    surfaceContainerHighest = Color(0xFF373434),
    surfaceContainerLow = Color(0xFF1D1B1B),
    surfaceContainerLowest = Color(0xFF0F0E0E),
)

/** Status colors that Material 3 has no role for (latency quality, as FlClash colors them). */
@Immutable
data class StatusColors(val good: Color, val fair: Color, val poor: Color)

private val flStatus = StatusColors(Color(0xFF4CAF50), Color(0xFFC57F0A), Color(0xFFF44336))

val LocalStatusColors = staticCompositionLocalOf { flStatus }

private val lightStatus = flStatus
private val darkStatus = flStatus

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace)

// Flutter's Material 3 type scale, which FlClash uses unchanged.
private val AppTypography = Typography()

@Composable
fun TidewallTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) darkStatus else lightStatus) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
