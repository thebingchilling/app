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
import android.widget.ImageView
import android.widget.Toast
import app.lychee.handwriting.HandwritingModels
import app.lychee.handwriting.HandwritingPanel
import app.lychee.voice.MicPermissionActivity
import app.lychee.voice.VoiceEngine
import app.lychee.voice.VoicePanel
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Lychee's part of the keyboard: Chinese input through Rime, and the candidate bar, grid and
 * word card that show readings and meanings. LatinIME forwards keys, cursor moves and language
 * changes here.
 */
class LycheeKeyboard(private val context: Context, private val connection: RichInputConnection) :
    ChineseInput.CandidateUi, ChineseInput.NotReadyUi, CandidateListener {

    init {
        instance = this
    }

    val chinese = ChineseInput(context, connection).also { it.ui = this }

    private var stripContainer: ViewGroup? = null
    private var keyboardWrapper: ViewGroup? = null
    private var bar: CandidateBar? = null
    private var grid: CandidateGrid? = null
    private var card: WordCard? = null
    private var handwriting: HandwritingPanel? = null
    private var voice: VoicePanel? = null
    private var inputView: View? = null
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
        this.inputView = inputView
        defaultStripHeight = context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        bar = CandidateBar(context, this).also {
            it.visibility = View.GONE
            strip.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        grid = CandidateGrid(context, this).also {
            it.visibility = View.GONE
            wrapper.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        handwriting = HandwritingPanel(context, handwritingHost).also {
            it.visibility = View.GONE
            wrapper.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        voice = VoicePanel(context, onMic = { toggleVoice() }, onClose = { closeVoice() }).also {
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
        (handwriting?.parent as? ViewGroup)?.removeView(handwriting)
        (voice?.parent as? ViewGroup)?.removeView(voice)
        VoiceEngine.stop()
        bar = null; grid = null; card = null; handwriting = null; voice = null
    }

    fun onLanguageChanged(locale: Locale) {
        chinese.onLanguageChanged(locale)
        hideCandidates()
        applyStripHeight()
        refreshTradSimpKeys()
        if (handwriting?.visibility == View.VISIBLE) handwriting?.onShow()
    }

    /** Settings changed (lines shown, wrap). */
    fun onSettingsChanged() {
        applyStripHeight()
    }

    /** Returns true when Lychee handled the key. */
    fun onEvent(event: Event): Boolean {
        when (event.keyCode) {
            LycheeKeyCodes.TRAD_SIMP -> {
                toggleTradSimp()
                return true
            }
            LycheeKeyCodes.HANDWRITING -> {
                chinese.onTextInput() // takes the first candidate of anything being typed
                showHandwriting()
                return true
            }
            KeyCode.VOICE_INPUT -> {
                chinese.onTextInput()
                if (VoiceEngine.isInstalled(context)) {
                    showVoice()
                    return true
                }
                // without the download, HeliBoard hands over to the phone's voice typing
                Toast.makeText(context, R.string.lychee_voice_not_downloaded, Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return chinese.onEvent(event)
    }

    fun onTextInput() = chinese.onTextInput()

    fun onStartInput() {
        hideCandidates()
        chinese.onStartInput()
    }

    fun onFinishInput() {
        chinese.onFinishInput()
        hideCandidates()
        closeVoice()
        handwriting?.let { if (it.visibility == View.VISIBLE) { it.onHide(); it.visibility = View.GONE } }
    }

    // --- 繁/简 ---

    private fun toggleTradSimp() {
        if (!chinese.isActive) {
            Toast.makeText(context, R.string.lychee_trad_simp_only_chinese, Toast.LENGTH_SHORT).show()
            return
        }
        val traditional = !chinese.isTraditional()
        chinese.setTraditional(traditional)
        refreshTradSimpKeys()
        Toast.makeText(context, if (traditional) R.string.lychee_traditional else R.string.lychee_simplified, Toast.LENGTH_SHORT).show()
    }

    /** Redraws the 繁/简 toolbar keys (their label follows the language and setting). */
    private fun refreshTradSimpKeys() {
        fun walk(v: View) {
            if (v.tag == ToolbarKey.TRAD_SIMP && v is ImageView) v.drawable?.invalidateSelf()
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        inputView?.let { walk(it) }
    }

    // --- handwriting ---

    private fun showHandwriting() {
        RimeData.prepare(context) // for OpenCC
        val panel = handwriting ?: return
        hideCandidates()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.onShow()
    }

    private val handwritingHost = object : HandwritingPanel.Host {
        override val language get() = chinese.language
        override fun commit(text: String) = commitText(text)
        override fun backspace() {
            connection.beginBatchEdit()
            connection.deleteTextBeforeCursor(Character.charCount(connection.getCodePointBeforeCursor().coerceAtLeast(0)).coerceAtLeast(1))
            connection.endBatchEdit()
        }
        override fun enter() = commitText("\n")
        override fun textBeforeCursor() = connection.getTextBeforeCursor(20, 0)?.toString() ?: ""
        override fun toScript(text: String, model: HandwritingModels.Model) = when (model) {
            HandwritingModels.Model.CANTONESE -> Script.forLanguage(context, text, Language.CANTONESE)
            HandwritingModels.Model.MANDARIN -> Script.forLanguage(context, text, Language.MANDARIN)
            HandwritingModels.Model.ENGLISH -> text
        }
        override fun close() {
            handwriting?.onHide()
            handwriting?.visibility = View.GONE
        }
        override fun showCard(text: String, lines: CandidateLines) {
            cardIndex = -1
            val c = card ?: return
            val full = if (lines.info == null && Readings.isOpen) CandidateLines.of(text, "", chinese.language ?: Language.MANDARIN, LycheePrefs.useJyutping(context)) else lines
            c.show(text, full, chinese.language ?: Language.MANDARIN, canForget = false)
            c.visibility = View.VISIBLE
            c.bringToFront()
        }
    }

    private fun commitText(text: String) {
        connection.beginBatchEdit()
        connection.commitText(text, 1)
        connection.endBatchEdit()
    }

    // --- voice ---

    private var lastVoiceText = ""

    private fun showVoice() {
        RimeData.prepare(context) // for OpenCC
        val panel = voice ?: return
        hideCandidates()
        panel.visibility = View.VISIBLE
        panel.bringToFront()
        panel.setLanguage(context.getString(when {
            !LycheePrefs.voiceFollowsKeyboard(context) -> R.string.lychee_voice_language_auto
            chinese.language == Language.CANTONESE -> R.string.lychee_language_cantonese
            chinese.language == Language.MANDARIN -> R.string.lychee_language_mandarin
            else -> R.string.lychee_voice_language_english
        }))
        startVoice()
    }

    private fun voiceLanguage() = when {
        !LycheePrefs.voiceFollowsKeyboard(context) -> "auto"
        chinese.language == Language.CANTONESE -> "yue"
        chinese.language == Language.MANDARIN -> "zh"
        else -> "en"
    }

    private fun startVoice() {
        val panel = voice ?: return
        if (!MicPermissionActivity.hasPermission(context)) {
            panel.setStatus(context.getString(R.string.lychee_mic_needed))
            MicPermissionActivity.request(context)
            return
        }
        lastVoiceText = ""
        panel.setListening(true)
        panel.setStatus(context.getString(R.string.lychee_voice_loading))
        VoiceEngine.start(context, voiceLanguage(), voiceListener)
    }

    private fun toggleVoice() {
        if (VoiceEngine.isRecording) VoiceEngine.stop() else startVoice()
    }

    private fun closeVoice() {
        VoiceEngine.stop()
        voice?.visibility = View.GONE
    }

    private val voiceListener = object : VoiceEngine.Listener {
        override fun onLevel(level: Float) {
            voice?.setLevel(level)
        }

        override fun onSpeaking(speaking: Boolean) {
            voice?.setStatus(context.getString(if (speaking) R.string.lychee_voice_hearing else R.string.lychee_voice_listening))
        }

        override fun onText(text: String, language: String) {
            val target = chinese.language ?: when (language) {
                "yue" -> Language.CANTONESE
                "zh" -> Language.MANDARIN
                else -> null
            }
            var out = if (target != null) Script.forLanguage(context, text, target) else text
            // a space between pieces of English, none between Chinese ones
            val before = connection.getCodePointBeforeCursor()
            if (before > 0 && before.toChar().isLetterOrDigit() && before < 0x2E80 && out.first().code < 0x2E80) out = " $out"
            commitText(out)
            lastVoiceText = out
        }

        override fun onError(message: String) {
            voice?.setStatus(context.getString(R.string.lychee_voice_error, message))
        }

        override fun onStopped() {
            voice?.setListening(false)
            if (voice?.visibility == View.VISIBLE) voice?.setStatus(context.getString(R.string.lychee_voice_tap_to_talk))
        }
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
        @Volatile
        private var instance: LycheeKeyboard? = null

        /** The 繁/简 key's label: the script Chinese is typed in now. */
        fun tradSimpLabel(): String {
            val k = instance ?: return "繁"
            if (!k.chinese.isActive) return "繁"
            return if (k.chinese.isTraditional()) "繁" else "简"
        }

        private val background = Executors.newSingleThreadExecutor { Thread(it, "lychee-readings") }

        /** Prepare Rime early (copying its data the first time), so typing Chinese works at once. */
        fun warmUp(context: Context) {
            if (RimeData.sharedDir(context).exists()) RimeData.prepare(context)
        }
    }
}
