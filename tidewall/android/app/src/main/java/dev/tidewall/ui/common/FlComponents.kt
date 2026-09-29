package dev.tidewall.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Layout constants and building blocks mirroring FlClash's widgets
// (lib/widgets/card.dart, lib/widgets/list.dart, lib/common/constant.dart).

/** FlClash's corner radii (AppCorner). */
object Corner {
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

/** Spacing between dashboard widgets. */
val GridSpacing = 14.dp

/** Height of a dashboard widget spanning [lines] rows (FlClash getWidgetHeight). */
fun widgetHeight(lines: Int): Dp = (80 * lines + 14 * (lines - 1)).dp

/**
 * FlClash's CommonCard: an outlined card on surfaceContainerLow; when
 * selected it turns secondaryContainer with a primary outline.
 */
@Composable
fun FlCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    radius: Dp = Corner.md,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    val border = BorderStroke(1.dp, if (selected) cs.primary else cs.surfaceContainerHighest)
    val container = if (selected) cs.secondaryContainer else cs.surfaceContainerLow
    val contentColor = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, contentColor = contentColor, border = border, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = contentColor, border = border, content = content)
    }
}

/** Icon + label header at the top of a dashboard card (FlClash InfoHeader). */
@Composable
fun InfoHeader(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            leading != null -> leading()
            icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (leading != null || icon != null) Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A dashboard card: fixed height, FlClash's large radius, optional header. */
@Composable
fun DashboardCard(
    lines: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FlCard(modifier.height(widgetHeight(lines)), radius = Corner.lg, onClick = onClick) {
        Column(Modifier.fillMaxSize()) {
            if (label != null) InfoHeader(label, icon)
            content()
        }
    }
}

/** Section title in lists (FlClash ListHeader). */
@Composable
fun ListHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier.padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Centered empty-state message (FlClash NullStatus). */
@Composable
fun NullStatus(label: String, icon: ImageVector, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(PaddingValues(32.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) {
                Icon(icon, null, Modifier.size(96.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null) action()
        }
    }
}
