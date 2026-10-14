package app.quartz.camera.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.quartz.core.camera.AnalysisFrame
import app.quartz.core.caps.Size
import app.quartz.core.settings.GridType
import app.quartz.core.ui.theme.ViewfinderColors
import kotlin.math.abs

/** Portrait display aspect (width / height) of a preview buffer. */
fun displayAspect(previewSize: Size?, sensorOrientation: Int): Float {
    previewSize ?: return 3f / 4f
    return if (sensorOrientation % 180 != 0) previewSize.h.toFloat() / previewSize.w else previewSize.w.toFloat() / previewSize.h
}

/**
 * Camera preview in a SurfaceView whose buffer is fixed to the session's
 * preview size, with all gestures handled in one place so pinch, swipe and
 * taps don't fight each other.
 */
@Composable
fun Viewfinder(
    previewSize: Size?,
    sensorOrientation: Int,
    onSurface: (PreviewSurface?) -> Unit,
    onTap: (Float, Float) -> Unit,
    onLongPress: (Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
    onZoom: (Float) -> Unit,
    onPinch: (Boolean) -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val aspect = displayAspect(previewSize, sensorOrientation)
    val surfaceCb by rememberUpdatedState(onSurface)
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val double by rememberUpdatedState(onDoubleTap)
    val zoom by rememberUpdatedState(onZoom)
    val pinch by rememberUpdatedState(onPinch)
    val swipe by rememberUpdatedState(onSwipe)
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    val decided: Pair<Int, PointerEvent>? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var r: Pair<Int, PointerEvent>? = null
                        while (r == null) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            val main = e.changes.firstOrNull { it.id == down.id }
                            r = when {
                                pressed.size > 1 -> 2 to e
                                main == null || !main.pressed -> 1 to e
                                (main.position - down.position).getDistance() > slop -> 2 to e
                                else -> null
                            }
                        }
                        r
                    }
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    when {
                        decided == null -> {
                            long(down.position.x / w, down.position.y / h)
                            do {
                                val e = awaitPointerEvent()
                            } while (e.changes.any { it.pressed })
                        }
                        decided.first == 1 -> {
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                awaitFirstDown(requireUnconsumed = false)
                            }
                            if (second != null) {
                                double()
                                do {
                                    val e = awaitPointerEvent()
                                } while (e.changes.any { it.pressed })
                            } else {
                                tap(down.position.x / w, down.position.y / h)
                            }
                        }
                        else -> {
                            var e = decided.second
                            var prevSpan: Float? = null
                            var pinching = false
                            var dx = 0f
                            while (true) {
                                val pressed = e.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    if (!pinching) pinch(true)
                                    pinching = true
                                    val span = (pressed[0].position - pressed[1].position).getDistance()
                                    prevSpan?.let { if (it > 1f) zoom(span / it) }
                                    prevSpan = span
                                } else {
                                    prevSpan = null
                                    if (!pinching) dx = pressed[0].position.x - down.position.x
                                }
                                e.changes.forEach { it.consume() }
                                e = awaitPointerEvent()
                            }
                            if (pinching) pinch(false)
                            if (!pinching && abs(dx) > swipeThreshold) swipe(if (dx < 0) 1 else -1)
                        }
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            surfaceCb(PreviewSurface(holder.surface, Size(width, height)))
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceCb(null)
                        }
                    })
                }
            },
            update = { view -> previewSize?.let { view.holder.setFixedSize(it.w, it.h) } },
            modifier = Modifier.fillMaxSize(),
        )
        overlay()
    }
}

@Composable
fun GridOverlay(type: GridType) {
    if (type == GridType.NONE) return
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.45f)
        val stroke = 1.dp.toPx()
        val fractions = when (type) {
            GridType.THIRDS -> listOf(1 / 3f, 2 / 3f)
            GridType.SQUARE -> listOf(0.25f, 0.5f, 0.75f)
            GridType.GOLDEN -> listOf(0.382f, 0.618f)
            GridType.NONE -> emptyList()
        }
        fractions.forEach { f ->
            drawLine(c, Offset(size.width * f, 0f), Offset(size.width * f, size.height), stroke)
            drawLine(c, Offset(0f, size.height * f), Offset(size.width, size.height * f), stroke)
        }
    }
}

/** Horizon line; turns yellow when the phone is level (within 1°). */
@Composable
fun LevelOverlay(roll: Float, deviceRotation: Int) {
    var angle = roll + deviceRotation
    while (angle > 180) angle -= 360
    while (angle < -180) angle += 360
    // Snap to the nearest of 0/±90/180 relative to how the phone is held.
    val tilt = ((angle + 45 + 360) % 90) - 45
    val level = abs(tilt) < 1f
    Canvas(Modifier.fillMaxSize()) {
        val c = if (level) ViewfinderColors.Warning else Color.White.copy(alpha = 0.8f)
        val len = size.minDimension * 0.18f
        rotate(-tilt, center) {
            drawLine(c, Offset(center.x - len, center.y), Offset(center.x - len * 0.25f, center.y), 2.dp.toPx())
            drawLine(c, Offset(center.x + len * 0.25f, center.y), Offset(center.x + len, center.y), 2.dp.toPx())
        }
        drawCircle(c, 3.dp.toPx(), center)
    }
}

@Composable
fun AnalysisOverlay(frame: AnalysisFrame?, showHistogram: Boolean) {
    frame ?: return
    frame.overlay?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
        )
    }
    if (showHistogram && frame.histogram.isNotEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(width = 112.dp, height = 56.dp)
                    .background(ViewfinderColors.Scrim, RoundedCornerShape(8.dp)),
            ) {
                val h = frame.histogram
                val max = (h.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                val bw = size.width / h.size
                h.forEachIndexed { i, v ->
                    val bh = size.height * (v / max)
                    drawRect(Color.White.copy(alpha = 0.85f), Offset(i * bw, size.height - bh), androidx.compose.ui.geometry.Size(bw, bh))
                }
            }
        }
    }
}

/** Pixel-style focus ring at the tap point. */
@Composable
fun BoxScope.FocusRing(focus: FocusUi) {
    val scale = remember(focus.id) { Animatable(1.4f) }
    LaunchedEffect(focus.id) { scale.animateTo(1f, tween(220)) }
    Canvas(Modifier.fillMaxSize()) {
        val r = 36.dp.toPx() * scale.value
        val c = Offset(size.width * focus.x, size.height * focus.y)
        drawCircle(Color.White, r, c, style = Stroke(1.5.dp.toPx()))
        if (focus.locked) drawCircle(ViewfinderColors.Warning, r + 4.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    }
    if (focus.locked) {
        Text(
            "AE/AF lock",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(ViewfinderColors.Warning, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ShutterFlash(counter: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(counter) {
        if (counter == 0) return@LaunchedEffect
        alpha.snapTo(0.7f)
        alpha.animateTo(0f, tween(180))
    }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha.value)))
}
