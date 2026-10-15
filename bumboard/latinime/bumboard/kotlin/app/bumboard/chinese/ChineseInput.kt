package app.bumboard.chinese

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodSubtype
import app.bumboard.rime.Composition
import app.bumboard.rime.Rime
import app.bumboard.rime.RimeService
import com.android.inputmethod.event.Event
import com.android.inputmethod.latin.common.Constants

/**
 * Chinese typing for LatinIME. When the current subtype carries `BumEngine=yue` or
 * `BumEngine=cmn`, LatinIME hands its key events here first; letters go to Rime and the
 * candidates replace the suggestion strip. Everything Rime does not want falls back to LatinIME.
 */
class ChineseInput(private val ime: InputMethodService, private val prefs: SharedPreferences) {
    private var session = 0L
    private var engine: String? = null
    private var bar: CandidateBarView? = null
    private var strip: View? = null

    /** True while Rime holds a composition, so LatinIME must leave the composing text alone. */
    var isComposing = false
        private set

    val isActive: Boolean
        get() = engine != null

    fun attachViews(bar: CandidateBarView?, strip: View?) {
        this.bar = bar
        this.strip = strip
        bar?.onPick = { index -> pick(index) }
        show(null)
    }

    fun onSubtypeChanged(subtype: InputMethodSubtype?) {
        val newEngine = subtype?.getExtraValueOf(EXTRA_ENGINE)
        if (newEngine == engine) return
        reset()
        engine = newEngine
        if (newEngine != null) RimeService.start(ime) { selectSchema() }
    }

    /** Re-reads schema and Traditional/Simplified settings, e.g. after the settings changed. */
    fun onSettingsChanged() {
        if (isActive && RimeService.ready) selectSchema()
    }

    private fun selectSchema() {
        val engine = engine ?: return
        if (session == 0L) session = Rime.createSession()
        val schema = when (engine) {
            "yue" -> if (prefs.getString(PREF_CANTONESE_INPUT, "jyutping") == "yale") "bum_yue_yale" else "bum_yue"
            else -> "bum_cmn"
        }
        Rime.selectSchema(session, schema)
        val simplified = prefs.getString(PREF_SCRIPT, "traditional") == "simplified"
        Rime.setOption(session, "simplification", simplified)
        Rime.setOption(session, "traditionalization", !simplified)
    }

    /** Returns true when the event was used for Chinese input. */
    fun onEvent(event: Event): Boolean {
        if (!isActive || !RimeService.ready || session == 0L) return false
        val code = event.mCodePoint
        val handled = when {
            event.mKeyCode == Constants.CODE_DELETE -> isComposing && key(KEY_BACKSPACE)
            code == Constants.CODE_ENTER -> {
                if (isComposing) {
                    // Enter sends the typed letters as they are.
                    val raw = Rime.input(session)
                    Rime.clearComposition(session)
                    ime.currentInputConnection?.commitText(raw, 1)
                    sync()
                    true
                } else {
                    false
                }
            }
            code == Constants.CODE_SPACE -> isComposing && key(code)
            code in 'a'.code..'z'.code -> key(code)
            code in 'A'.code..'Z'.code -> {
                // A capital letter ends the composition and is typed as is.
                if (isComposing) commitFirst()
                false
            }
            code in '0'.code..'9'.code || code == '\''.code -> isComposing && key(code)
            code > 0 -> key(code)
            else -> false
        }
        return handled
    }

    /** Called before LatinIME types text directly (e.g. a key with a label of several characters). */
    fun beforeExternalText() {
        if (isComposing) commitFirst()
    }

    fun pick(index: Int) {
        if (session == 0L) return
        Rime.selectCandidate(session, index)
        sync()
    }

    /** Drops any composition, e.g. when the text field changes. */
    fun reset() {
        if (session != 0L) Rime.clearComposition(session)
        if (isComposing) ime.currentInputConnection?.finishComposingText()
        isComposing = false
        show(null)
    }

    private fun commitFirst() {
        Rime.commitComposition(session)
        sync()
    }

    private fun key(keycode: Int): Boolean {
        val handled = Rime.processKey(session, keycode)
        if (handled) sync()
        return handled
    }

    private fun sync() {
        val ic = ime.currentInputConnection
        Rime.commit(session)?.let { ic?.commitText(it, 1) }
        val composition = Rime.composition(session)
        if (composition != null) {
            ic?.setComposingText(composition.preedit, 1)
            isComposing = true
        } else if (isComposing) {
            ic?.setComposingText("", 1)
            ic?.finishComposingText()
            isComposing = false
        }
        show(composition)
    }

    private fun show(composition: Composition?) {
        val bar = bar ?: return
        if (composition == null) {
            bar.visibility = View.GONE
            strip?.visibility = View.VISIBLE
        } else {
            bar.setCandidates(composition.candidates)
            strip?.visibility = View.GONE
            bar.visibility = View.VISIBLE
        }
    }

    companion object {
        const val EXTRA_ENGINE = "BumEngine"
        const val PREF_SCRIPT = "bum_chinese_script"
        const val PREF_CANTONESE_INPUT = "bum_cantonese_input"
        private const val KEY_BACKSPACE = 0xff08
    }
}
