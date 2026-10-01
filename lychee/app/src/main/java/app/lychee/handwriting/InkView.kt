/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package app.lychee.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** A writing pad: draws strokes and reports them as timestamped points. */
class InkView(context: Context) : View(context) {

    class Point(val x: Float, val y: Float, val t: Long)

    /** Called when a stroke starts (before its first point is added). */
    var onStrokeStart: (() -> Unit)? = null

    /** Called with the finished stroke. */
    var onStrokeEnd: ((List<Point>) -> Unit)? = null

    var hint: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val paths = mutableListOf<Path>()
    private var current: Path? = null
    private var points = mutableListOf<Point>()

    val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * 5
    }

    val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 14
    }

    fun clear() {
        paths.clear()
        current = null
        points = mutableListOf()
        invalidate()
    }

    val isEmpty get() = paths.isEmpty() && current == null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onStrokeStart?.invoke()
                current = Path().apply { moveTo(event.x, event.y) }
                points = mutableListOf(Point(event.x, event.y, now))
            }
            MotionEvent.ACTION_MOVE -> {
                val path = current ?: return true
                for (i in 0 until event.historySize) {
                    path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
                    points += Point(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i) - event.eventTime + now)
                }
                path.lineTo(event.x, event.y)
                points += Point(event.x, event.y, now)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val path = current ?: return true
                path.lineTo(event.x, event.y)
                points += Point(event.x, event.y, now)
                paths += path
                current = null
                onStrokeEnd?.invoke(points)
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (isEmpty && hint.isNotEmpty()) {
            canvas.drawText(hint, width / 2f, height / 2f, hintPaint)
        }
        paths.forEach { canvas.drawPath(it, inkPaint) }
        current?.let { canvas.drawPath(it, inkPaint) }
    }
}
