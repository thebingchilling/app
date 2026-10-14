@file:OptIn(ExperimentalTextApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.quartz.core.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.quartz.core.ui.R

/** Google Sans Flex (OFL 1.1), bundled as one variable font. */
private fun flex(weight: Int) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GoogleSansFlex = FontFamily(flex(300), flex(400), flex(500), flex(600), flex(700))

private fun TextStyle.flex() = copy(fontFamily = GoogleSansFlex)

/** The Material 3 Expressive type scale, set in Google Sans Flex. */
val QuartzTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(),
        headlineMedium = headlineMedium.flex(),
        headlineSmall = headlineSmall.flex(),
        titleLarge = titleLarge.flex(),
        titleMedium = titleMedium.flex(),
        titleSmall = titleSmall.flex(),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(),
        labelMedium = labelMedium.flex(),
        labelSmall = labelSmall.flex(),
        displayLargeEmphasized = displayLargeEmphasized.flex(),
        displayMediumEmphasized = displayMediumEmphasized.flex(),
        displaySmallEmphasized = displaySmallEmphasized.flex(),
        headlineLargeEmphasized = headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = headlineSmallEmphasized.flex(),
        titleLargeEmphasized = titleLargeEmphasized.flex(),
        titleMediumEmphasized = titleMediumEmphasized.flex(),
        titleSmallEmphasized = titleSmallEmphasized.flex(),
        bodyLargeEmphasized = bodyLargeEmphasized.flex(),
        bodyMediumEmphasized = bodyMediumEmphasized.flex(),
        bodySmallEmphasized = bodySmallEmphasized.flex(),
        labelLargeEmphasized = labelLargeEmphasized.flex(),
        labelMediumEmphasized = labelMediumEmphasized.flex(),
        labelSmallEmphasized = labelSmallEmphasized.flex(),
    )
}

/** Readouts (ISO, shutter, timer, zoom) use tabular figures so digits don't jump. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
