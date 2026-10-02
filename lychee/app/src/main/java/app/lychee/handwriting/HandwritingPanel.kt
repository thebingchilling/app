// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.lychee.chinese.ChineseInput.Language
import app.lychee.readings.CandidateLines
import app.lychee.readings.Readings
import app.lychee.rime.Rime
import app.lychee.ui.CandidateBar
import app.lychee.ui.CandidateCell
import app.lychee.ui.CandidateListener
import app.lychee.ui.LineSettings
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import java.util.concurrent.Executors

/**
 * Handwriting over the keys: write with a finger, pick from the candidates (with readings and
 * meanings like typed candidates). Starting a new character after a pause takes the first
 * candidate of the last one, so writing can go on character after character.
 */
@SuppressLint("ViewConstructor")
class HandwritingPanel(context: Context, private val host: Host) : LinearLayout(context), CandidateListener {

    interface Host {
        /** Mandarin, Cantonese, or null for English */
        val language: Language?
        fun commit(text: String)
        fun backspace()
        fun enter()
        /** up to 20 characters before the cursor, so the recognizer knows the context */
        fun textBeforeCursor(): String
        /** Traditional/Simplified as the user wants it */
        fun toScript(text: String, model: HandwritingModels.Model): String
        fun close()
        fun showCard(text: String, lines: CandidateLines)
    }

    private val colors = Settings.getValues().mColors
    private val bar = CandidateBar(context, this)
    private val ink = InkView(context)
    private val hint = TextView(context)
    private var candidates: List<String> = emptyList()
    private var recognizer: DigitalInkRecognizer? = null
    private var recognizerModel: HandwritingModels.Model? = null
    private val lookupThread = Executors.newSingleThreadExecutor { Thread(it, "lychee-hanzi-lookup") }
    private var request = 0

    init {
        orientation = VERTICAL
        colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        isClickable = true
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(LineSettings.barHeightDp(context))))
        val middle = android.widget.FrameLayout(context)
        hint.gravity = Gravity.CENTER
        hint.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        middle.addView(hint, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        middle.addView(ink, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(middle, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val keys = LinearLayout(context).apply { orientation = HORIZONTAL }
        keys.addView(key("⌨", R.string.lychee_back_to_keyboard) { finishCharacter(); host.close() }, LayoutParams(0, dp(48), 1f))
        keys.addView(key("␣", R.string.lychee_space) { if (!finishCharacter()) host.commit(" ") }, LayoutParams(0, dp(48), 2f))
        keys.addView(key("⌫", R.string.lychee_backspace) { if (ink.isEmpty) host.backspace() else clear() }, LayoutParams(0, dp(48), 1f))
        keys.addView(key("↵", R.string.lychee_enter) { if (!finishCharacter()) host.enter() }, LayoutParams(0, dp(48), 1f))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        bar.visibility = INVISIBLE
    }

    /** Call when the panel is shown (the language may have changed). */
    fun onShow() {
        clear()
        val model = model()
        HandwritingModels.refresh(model)
        val usesMlKit = HandwritingModels.isInstalled(model)
        hint.text = context.getString(
            when {
                usesMlKit -> R.string.lychee_write_here
                model == HandwritingModels.Model.ENGLISH -> R.string.lychee_handwriting_english_needs_download
                else -> R.string.lychee_write_here_basic
            }
        )
    }

    fun onHide() {
        finishCharacter()
        recognizer?.close()
        recognizer = null
        recognizerModel = null
    }

    private fun model() = when (host.language) {
        Language.CANTONESE -> HandwritingModels.Model.CANTONESE
        Language.MANDARIN -> HandwritingModels.Model.MANDARIN
        null -> HandwritingModels.Model.ENGLISH
    }

    /** Takes the first candidate of what is written; false when nothing was written. */
    private fun finishCharacter(): Boolean {
        if (ink.isEmpty) return false
        candidates.firstOrNull()?.let { host.commit(it) }
        clear()
        return true
    }

    private fun clear() {
        ink.clear()
        candidates = emptyList()
        request++
        bar.visibility = INVISIBLE
        hint.visibility = VISIBLE
    }

    private fun recognize() {
        val id = ++request
        val model = model()
        val strokes = ink.strokes()
        if (strokes.isEmpty()) return
        if (HandwritingModels.isInstalled(model)) {
            if (recognizerModel != model) {
                recognizer?.close()
                recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model.mlKit).build())
                recognizerModel = model
            }
            val builder = Ink.builder()
            for (s in strokes) {
                val sb = Ink.Stroke.builder()
                for (p in s) sb.addPoint(Ink.Point.create(p.x, p.y, p.t))
                builder.addStroke(sb.build())
            }
            val context = RecognitionContext.builder()
                .setPreContext(host.textBeforeCursor())
                .setWritingArea(WritingArea(ink.width.toFloat(), ink.height.toFloat()))
                .build()
            recognizer!!.recognize(builder.build(), context).addOnSuccessListener { result ->
                if (id == request) show(result.candidates.map { host.toScript(it.text, model) }.distinct())
            }.addOnFailureListener {
                if (id == request) lookup(id, strokes, model)
            }
        } else if (model != HandwritingModels.Model.ENGLISH) {
            lookup(id, strokes, model)
        }
    }

    /** The built-in recognizer: characters only, about 9,500 of them. */
    private fun lookup(id: Int, strokes: List<List<InkView.P>>, model: HandwritingModels.Model) {
        // hanzi_lookup works in a 256 × 256 box
        val scale = 255f / maxOf(ink.width, ink.height, 1)
        val points = strokes.map { s -> s.map { HanziLookup.Point((it.x * scale).toInt(), (it.y * scale).toInt()) } }
        val appContext = context.applicationContext
        lookupThread.execute {
            val matches = HanziLookup.get(appContext).lookup(points, 10).map { host.toScript(it.hanzi, model) }.distinct()
            post { if (id == request) show(matches) }
        }
    }

    private fun show(texts: List<String>) {
        candidates = texts
        if (texts.isEmpty()) return
        val settings = LineSettings(context)
        val lang = host.language ?: Language.MANDARIN
        val lines = texts.map {
            if (settings.anyLine && Readings.isOpen) CandidateLines.of(it, "", lang, settings.jyutping) else CandidateLines(null, null, null, null)
        }
        bar.show(texts.map { Rime.Candidate(it, "") }, lines, settings)
        bar.visibility = VISIBLE
        hint.visibility = INVISIBLE
    }

    // --- CandidateListener (the candidate row) ---

    override fun onPick(index: Int) {
        candidates.getOrNull(index)?.let { host.commit(it) }
        clear()
    }

    override fun onLongPress(index: Int, text: String, lines: CandidateLines) = host.showCard(text, lines)

    override fun onExpand() {} // the row scrolls; ten candidates need no grid

    override fun onCollapse() {}

    private fun key(label: String, description: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(colors.get(ColorType.KEY_TEXT))
        background = CandidateCell.pressedBackground(context)
        contentDescription = context.getString(description)
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** The writing surface: records strokes with times, draws them, asks for recognition after each. */
    private inner class InkView(context: Context) : View(context) {
        inner class P(val x: Float, val y: Float, val t: Long)

        private val strokes = ArrayList<ArrayList<P>>()
        private val paths = ArrayList<Path>()
        private var lastStrokeEnd = 0L
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colors.get(ColorType.KEY_TEXT)
        }

        val isEmpty get() = strokes.isEmpty()

        fun strokes(): List<List<P>> = strokes.map { it.toList() }

        fun clear() {
            strokes.clear()
            paths.clear()
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val now = SystemClock.uptimeMillis()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // a new character after a pause: the last one is done
                    if (strokes.isNotEmpty() && candidates.isNotEmpty() && now - lastStrokeEnd > NEXT_CHARACTER_PAUSE) finishCharacter()
                    strokes.add(arrayListOf(P(event.x, event.y, System.currentTimeMillis())))
                    paths.add(Path().apply { moveTo(event.x, event.y) })
                    hint.visibility = INVISIBLE
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = strokes.lastOrNull() ?: return true
                    val path = paths.last()
                    for (h in 0 until event.historySize) {
                        stroke.add(P(event.getHistoricalX(h), event.getHistoricalY(h), System.currentTimeMillis()))
                        path.lineTo(event.getHistoricalX(h), event.getHistoricalY(h))
                    }
                    stroke.add(P(event.x, event.y, System.currentTimeMillis()))
                    path.lineTo(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> {
                    lastStrokeEnd = now
                    recognize()
                }
            }
            invalidate()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            for (p in paths) canvas.drawPath(p, paint)
        }
    }

    companion object {
        private const val NEXT_CHARACTER_PAUSE = 900L
    }
}
