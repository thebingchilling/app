package app.bumboard.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import app.bumboard.candidates.AnnotatedCandidateView
import app.bumboard.core.InputMode
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

/**
 * Handwriting panel: write on the pad, pick a character from the strip above it (each with
 * its readings and meaning). Recognition runs in the handwriting add-on (ML Kit).
 */
class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {

    private val theme by manager.theme()
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()

    override val title: String get() = "Handwriting"

    private val client by lazy { HandwritingClient(context.applicationContext) }
    private val d by lazy { context.resources.displayMetrics.density }

    private fun languageTag(): String = when (BumBoard.mode) {
        InputMode.Mandarin, InputMode.EnglishToMandarin -> "zh-Hani-CN"
        else -> "zh-Hani-HK"
    }

    private val strip by lazy { LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL } }
    private val message by lazy {
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ColorUtils.setAlphaComponent(theme.keyTextColor, 0xB0))
            gravity = Gravity.CENTER
        }
    }
    private val pad by lazy { InkPad(context, theme) { recognizeSoon() } }

    private val recognize = Runnable {
        val strokes = pad.strokes
        if (strokes.isEmpty()) return@Runnable
        val before = service.currentInputConnection?.getTextBeforeCursor(20, 0)?.toString() ?: ""
        client.recognize(languageTag(), strokes, pad.width.toFloat(), pad.height.toFloat(), before)
    }

    private fun recognizeSoon() {
        pad.removeCallbacks(recognize)
        pad.postDelayed(recognize, 250)
    }

    private val view by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(strip)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (BumBoard.barHeightDp * d).toInt()))
            addView(message, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(pad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                val m = (8 * d).toInt(); setMargins(m, m, m, m)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                listOf(
                    "Clear" to { pad.clear(); strip.removeAllViews() },
                    "Space" to { service.commitText(" ") },
                    "⌫" to { service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) },
                    "⏎" to { service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) },
                    "ABC" to { windowManager.attachWindow(KeyboardWindow) },
                ).forEach { (label, action) -> addView(button(label, action)) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * d).toInt()))
        }
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(theme.keyTextColor)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 20 * d
            setColor(theme.keyBackgroundColor)
        }
        layoutParams = LinearLayout.LayoutParams(0, (40 * d).toInt(), 1f).apply {
            val m = (4 * d).toInt(); setMargins(m, 0, m, 0)
        }
        setOnClickListener { action() }
    }

    private fun showCandidates(list: List<String>) {
        strip.removeAllViews()
        message.text = ""
        list.take(12).forEach { text ->
            val cell = AnnotatedCandidateView(context, theme).apply {
                update(CandidateWord("", text, ""))
                val p = (12 * d).toInt(); setPadding(p, 0, p, 0)
                setOnClickListener {
                    service.commitText(text)
                    pad.clear()
                    strip.removeAllViews()
                }
            }
            strip.addView(cell, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onCreateView(): View = view

    override fun onAttached() {
        client.onResult = { r ->
            when (r) {
                is HandwritingClient.Result.Candidates -> showCandidates(r.list)
                HandwritingClient.Result.Downloading -> message.text = "Downloading the handwriting model… try again in a moment"
                HandwritingClient.Result.Failed -> message.text = "Couldn't read that — try again"
            }
        }
        if (!client.isInstalled()) {
            message.text = "Handwriting needs the BumBoard Handwriting add-on (${client.addOnPackage})"
        } else {
            message.text = "Write a character"
            client.bind()
        }
    }

    override fun onDetached() {
        pad.removeCallbacks(recognize)
        client.unbind()
    }

    /** The writing surface. Points are (x, y, time ms). */
    @SuppressLint("ViewConstructor")
    class InkPad(ctx: Context, theme: Theme, private val onStroke: () -> Unit) : View(ctx) {
        val strokes = mutableListOf<MutableList<FloatArray>>()
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5 * ctx.resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = theme.keyTextColor
        }

        init {
            background = GradientDrawable().apply {
                cornerRadius = 24 * ctx.resources.displayMetrics.density
                setColor(theme.keyBackgroundColor)
            }
        }

        fun clear() {
            strokes.clear()
            path.reset()
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val t = SystemClock.uptimeMillis().toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    strokes += mutableListOf(floatArrayOf(e.x, e.y, t))
                    path.moveTo(e.x, e.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    strokes.lastOrNull()?.add(floatArrayOf(e.x, e.y, t))
                    path.lineTo(e.x, e.y)
                }
                MotionEvent.ACTION_UP -> onStroke()
            }
            invalidate()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(path, paint)
        }
    }
}
