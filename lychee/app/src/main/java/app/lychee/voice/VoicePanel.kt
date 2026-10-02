// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.ui.CandidateCell
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** The voice typing panel over the keys: a big microphone button, the language, and a level meter. */
@SuppressLint("ViewConstructor")
class VoicePanel(context: Context, private val onMic: () -> Unit, private val onClose: () -> Unit) : LinearLayout(context) {
    private val colors = Settings.getValues().mColors
    private val status = TextView(context)
    private val language = TextView(context)
    private val mic = MicButton(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        isClickable = true
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        language.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        language.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        language.setPadding(dp(16), 0, 0, 0)
        header.addView(language, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val keyboard = TextView(context).apply {
            text = "⌨"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            background = CandidateCell.pressedBackground(context)
            contentDescription = context.getString(R.string.lychee_back_to_keyboard)
            setOnClickListener { onClose() }
        }
        header.addView(keyboard, LayoutParams(dp(56), dp(44)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(context), LayoutParams(1, 0, 1f))
        mic.setOnClickListener { onMic() }
        mic.contentDescription = context.getString(R.string.voice)
        addView(mic, LayoutParams(dp(96), dp(96)))
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        status.setTextColor(colors.get(ColorType.KEY_TEXT))
        status.gravity = Gravity.CENTER
        status.setPadding(dp(16), dp(12), dp(16), 0)
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(context), LayoutParams(1, 0, 1f))
    }

    fun setLanguage(label: String) { language.text = label }

    fun setStatus(text: String) { status.text = text }

    fun setListening(listening: Boolean) {
        mic.active = listening
        mic.level = 0f
        mic.invalidate()
    }

    fun setLevel(level: Float) {
        mic.level = level
        mic.invalidate()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** A round microphone button with a ring that grows with the voice level. */
    private inner class MicButton(context: Context) : View(context) {
        var active = false
        var level = 0f
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        init { isClickable = true }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) * 0.72f
            val accent = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
            if (active) {
                fill.color = (accent and 0x00FFFFFF) or 0x44000000
                canvas.drawCircle(cx, cy, r + (minOf(cx, cy) - r) * (level * 8f).coerceIn(0f, 1f), fill)
            }
            fill.color = if (active) accent else colors.get(ColorType.KEY_BACKGROUND)
            canvas.drawCircle(cx, cy, r, fill)
            text.color = colors.get(ColorType.KEY_TEXT)
            text.textSize = r * 0.9f
            canvas.drawText("🎤", cx, cy - (text.descent() + text.ascent()) / 2, text)
        }
    }
}
