@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.quartz.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Static fallback for Android 8–11 (no Material You): a cool, neutral palette
// generated from a quartz-grey seed (#6B7A8F) with the Material Theme Builder's tonal steps.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF3F5F7D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE4FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF6A5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    surfaceContainerHighest = Color(0xFFE1E2E6),
    outline = Color(0xFF72787F),
    outlineVariant = Color(0xFFC2C7CF),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA7C8EA),
    onPrimary = Color(0xFF07314E),
    primaryContainer = Color(0xFF254865),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Color(0xFFBAC8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3B4857),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFFD5BEE4),
    onTertiary = Color(0xFF3A2948),
    tertiaryContainer = Color(0xFF513F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE1E2E6),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE1E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2E),
    surfaceContainerHighest = Color(0xFF323539),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
)

@Composable
fun quartzColorScheme(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkFallback else LightFallback
    }
}

/**
 * Material 3 Expressive theme: dynamic colour (Material You) on Android 12+,
 * Google Sans Flex, expressive spring motion.
 *
 * [forceDark] is used by the camera screen, which is always dark so colour
 * never tints how the picture is judged.
 */
@Composable
fun QuartzTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = quartzColorScheme(dark),
        motionScheme = MotionScheme.expressive(),
        typography = QuartzTypography,
        content = content,
    )
}

/** Fixed colours used over the viewfinder regardless of theme. */
object ViewfinderColors {
    val Scrim = Color(0x66000000)
    val ScrimStrong = Color(0xB3000000)
    val OnScrim = Color(0xFFFFFFFF)
    val OnScrimDim = Color(0xB3FFFFFF)
    val Record = Color(0xFFE5393A)
    val Warning = Color(0xFFFFB300)
}
