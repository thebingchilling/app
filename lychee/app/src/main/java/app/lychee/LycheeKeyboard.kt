// SPDX-License-Identifier: GPL-3.0-only
package app.lychee

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.lychee.chinese.ChineseInput
import app.lychee.chinese.ChineseInput.Language
import app.lychee.chinese.RimeData
import app.lychee.readings.CandidateLines
import app.lychee.readings.Readings
import app.lychee.rime.Rime
import app.lychee.ui.CandidateBar
import app.lychee.ui.CandidateGrid
import app.lychee.ui.CandidateListener
import app.lychee.ui.LineSettings
import app.lychee.ui.WordCard
import helium314.keyboard.event.Event
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Lychee's part of the keyboard: Chinese input through Rime, and the candidate bar, grid and
 * word card that show readings and meanings. LatinIME forwards keys, cursor moves and language
 * changes here.
 */
class LycheeKeyboard(private val context: Context, connection: RichInputConnection) :
    ChineseInput.CandidateUi, ChineseInput.NotReadyUi, CandidateListener {

    val chinese = ChineseInput(context, connection).also { it.ui = this }

    private var stripContainer: ViewGroup? = null
    private var keyboardWrapper: ViewGroup? = null
    private var bar: CandidateBar? = null
    private var grid: CandidateGrid? = null
    private var card: WordCard? = null
    private var defaultStripHeight = 0
    private var cardIndex = -1

    /** Call whenever HeliBoard creates its input view. */
    fun attach(inputView: View) {
        if (!Readings.isOpen) background.execute { Readings.open(context) }
        detachViews()
        val strip = inputView.findViewById<ViewGroup>(R.id.strip_container) ?: return
        val wrapper = inputView.findViewById<ViewGroup>(R.id.keyboard_view_wrapper) ?: return
        stripContainer = strip
        keyboardWrapper = wrapper
        defaultStripHeight = context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        bar = CandidateBar(context, this).also {
            it.visibility = View.GONE
            strip.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        grid = CandidateGrid(context, this).also {
            it.visibility = View.GONE
            wrapper.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        card = WordCard(context, onClose = { hideCard() }, onForget = { forget() }).also {
            it.visibility = View.GONE
            wrapper.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        applyStripHeight()
    }

    private fun detachViews() {
        (bar?.parent as? ViewGroup)?.removeView(bar)
        (grid?.parent as? ViewGroup)?.removeView(grid)
        (card?.parent as? ViewGroup)?.removeView(card)
        bar = null; grid = null; card = null
    }

    fun onLanguageChanged(locale: Locale) {
        chinese.onLanguageChanged(locale)
        hideCandidates()
        applyStripHeight()
    }

    /** Settings changed (lines shown, wrap). */
    fun onSettingsChanged() {
        applyStripHeight()
    }

    /** Returns true when Lychee handled the key. */
    fun onEvent(event: Event): Boolean = chinese.onEvent(event)

    fun onTextInput() = chinese.onTextInput()

    fun onStartInput() {
        hideCandidates()
        chinese.onStartInput()
    }

    fun onFinishInput() {
        chinese.onFinishInput()
        hideCandidates()
    }

    /** The bar is taller in Mandarin and Cantonese, to fit the readings and meanings. */
    private fun applyStripHeight() {
        val strip = stripContainer ?: return
        val height = if (chinese.isActive) dp(LineSettings.barHeightDp(context)).coerceAtLeast(defaultStripHeight)
            else defaultStripHeight
        val params = strip.layoutParams ?: return
        if (params.height != height) {
            params.height = height
            strip.layoutParams = params
        }
    }

    // --- ChineseInput.CandidateUi ---

    private var language = Language.MANDARIN
    private var current: List<Rime.Candidate> = emptyList()

    override fun showCandidates(candidates: List<Rime.Candidate>, language: Language, preedit: String) {
        this.language = language
        current = candidates
        hideCard()
        grid?.visibility = View.GONE
        val settings = LineSettings(context)
        val lines = candidates.map { linesFor(it, settings) }
        bar?.show(candidates, lines, settings)
        bar?.visibility = View.VISIBLE
    }

    override fun hideCandidates() {
        current = emptyList()
        bar?.visibility = View.GONE
        grid?.visibility = View.GONE
        hideCard()
    }

    override fun showPreparing() {
        android.widget.Toast.makeText(context, R.string.lychee_preparing_chinese, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun linesFor(c: Rime.Candidate, settings: LineSettings): CandidateLines =
        if (!settings.anyLine || !Readings.isOpen) CandidateLines(null, null, null, null)
        else CandidateLines.of(c.text, c.comment, language, settings.jyutping)

    // --- CandidateListener ---

    override fun onPick(index: Int) {
        grid?.visibility = View.GONE
        chinese.selectCandidate(index)
    }

    override fun onLongPress(index: Int, text: String, lines: CandidateLines) {
        val c = card ?: return
        cardIndex = index
        val full = if (lines.info == null && Readings.isOpen) CandidateLines.of(text, "", language, LycheePrefs.useJyutping(context)) else lines
        c.show(text, full, language, canForget = chinese.isComposing)
        c.visibility = View.VISIBLE
        c.bringToFront()
    }

    override fun onExpand() {
        val g = grid ?: return
        val settings = LineSettings(context)
        val first = current.map { it to linesFor(it, settings) }
        g.show(first) { start -> chinese.candidates(start, 60).map { it to linesFor(it, settings) } }
        g.visibility = View.VISIBLE
        g.bringToFront()
    }

    override fun onCollapse() {
        grid?.visibility = View.GONE
    }

    private fun hideCard() {
        card?.visibility = View.GONE
        cardIndex = -1
    }

    private fun forget() {
        if (cardIndex >= 0) chinese.forgetCandidate(cardIndex)
        hideCard()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    companion object {
        private val background = Executors.newSingleThreadExecutor { Thread(it, "lychee-readings") }

        /** Prepare Rime early (copying its data the first time), so typing Chinese works at once. */
        fun warmUp(context: Context) {
            if (RimeData.sharedDir(context).exists()) RimeData.prepare(context)
        }
    }
}
