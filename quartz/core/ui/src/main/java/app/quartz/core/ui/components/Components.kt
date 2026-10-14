@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.quartz.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.quartz.core.ui.icons.MsIcon
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.theme.ViewfinderColors

/**
 * Pixel Camera 10.1-style choice: rounded square when inactive, circle (or
 * pill, for text) when selected, with a spring between the two.
 *
 * @param onScrim colours for use over the viewfinder (white on black).
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: MsIcon? = null,
    enabled: Boolean = true,
    onScrim: Boolean = true,
    contentDescription: String? = null,
    size: Dp = 44.dp,
) {
    val radius by animateDpAsState(if (selected) size / 2 else 12.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chipShape")
    val container by animateColorAsState(
        when {
            onScrim && selected -> Color.White
            onScrim -> Color.White.copy(alpha = 0.14f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chipColor",
    )
    val content = when {
        !enabled -> if (onScrim) Color.White.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        onScrim && selected -> Color.Black
        onScrim -> Color.White
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = content,
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .semantics {
                role = Role.RadioButton
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) SymbolIcon(icon, null, Modifier.size(22.dp), filled = selected)
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

enum class ShutterStyle { PHOTO, VIDEO, RECORDING, RUNNING }

/**
 * The shutter: white ring; white disc for photos, red disc for video, red
 * rounded square while recording (shape morph), with an optional progress
 * ring for long exposures.
 */
@Composable
fun ShutterButton(
    style: ShutterStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    enabled: Boolean = true,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    contentDescription: String,
) {
    val motion = MaterialTheme.motionScheme
    val pressed = remember { mutableFloatStateOf(1f) }
    val scale by animateFloatAsState(pressed.floatValue, motion.fastSpatialSpec(), label = "shutterScale")
    val stop = style == ShutterStyle.RECORDING || style == ShutterStyle.RUNNING
    val inner by animateDpAsState(if (stop) 30.dp else 62.dp, motion.defaultSpatialSpec(), label = "shutterInner")
    val corner by animateDpAsState(if (stop) 8.dp else 31.dp, motion.defaultSpatialSpec(), label = "shutterCorner")
    val color by animateColorAsState(
        if (style == ShutterStyle.PHOTO) Color.White else ViewfinderColors.Record,
        motion.defaultEffectsSpec(),
        label = "shutterColor",
    )
    val click by rememberUpdatedState(onClick)
    val longStart by rememberUpdatedState(onLongPressStart)
    val longEnd by rememberUpdatedState(onLongPressEnd)
    Box(
        modifier
            .size(80.dp)
            .scale(scale)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed.floatValue = 0.9f
                        tryAwaitRelease()
                        pressed.floatValue = 1f
                        longEnd?.invoke()
                    },
                    onTap = { click() },
                    onLongPress = { longStart?.invoke() },
                )
            }
            .drawBehind {
                drawCircle(Color.White, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
                if (progress != null) {
                    drawArc(
                        ViewfinderColors.Warning, -90f, 360f * progress, false,
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        style = Stroke(4.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .drawBehind { drawRect(if (enabled) color else color.copy(alpha = 0.4f)) },
        )
    }
}

/** A round icon button on the viewfinder scrim (flip, settings, gallery). */
@Composable
fun ScrimIconButton(
    icon: MsIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    filled: Boolean = false,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = ViewfinderColors.Scrim,
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
        modifier = modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SymbolIcon(icon, contentDescription, Modifier.size(24.dp).rotate(rotation), filled = filled)
        }
    }
}

/**
 * Rotation for icons that follow the device while the UI stays portrait:
 * takes the shortest way round instead of spinning through 270°.
 */
@Composable
fun animatedDeviceRotation(deviceRotation: Int): Float {
    val last = remember { floatArrayOf(0f) }
    val target = remember(deviceRotation) {
        val current = last[0]
        var delta = (-deviceRotation.toFloat() - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        last[0] = current + delta
        last[0]
    }
    val animated by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "deviceRotation")
    return animated
}

/** Thin outline used around the thumbnail. */
fun Modifier.thinOutline(): Modifier = border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)), CircleShape)
