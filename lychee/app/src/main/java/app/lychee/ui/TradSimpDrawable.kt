// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import app.lychee.LycheeKeyboard

/** The 繁/简 toolbar key: shows the script Chinese is typed in now (繁 Traditional, 简 Simplified). */
class TradSimpDrawable(context: Context) : Drawable() {
    private val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size * 0.78f
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFFFFFFF.toInt()
    }

    override fun draw(canvas: Canvas) {
        val label = LycheeKeyboard.tradSimpLabel()
        val b = bounds
        val y = b.exactCenterY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, b.exactCenterX(), y, paint)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
