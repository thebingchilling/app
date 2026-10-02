// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.chinese

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.lychee.LycheePrefs
import app.lychee.rime.Rime
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.Log
import java.util.Locale

/**
 * Mandarin and Cantonese typing for HeliBoard: while one of them is the current language,
 * letters go to Rime instead of HeliBoard's word logic, Rime's candidates go to the
 * [CandidateUi], and the chosen words are committed to the editor.
 */
class ChineseInput(private val context: Context, private val connection: RichInputConnection) {

    enum class Language(val schema: String) {
        MANDARIN("lychee_mandarin"),
        CANTONESE("lychee_cantonese");

        companion object {
            fun of(locale: Locale): Language? = when (locale.language) {
                "zh" -> MANDARIN
                "yue" -> CANTONESE
                else -> null
            }
        }
    }

    interface CandidateUi {
        /** Shows the first candidates; [language] says which readings matter most. */
        fun showCandidates(candidates: List<Rime.Candidate>, language: Language, preedit: String)
        fun hideCandidates()
    }

    var ui: CandidateUi? = null
    var language: Language? = null
        private set

    private var session: Rime.Session? = null
    private var sessionSchema: String? = null
    private var composing = false
    private var quoteOpen = false
    private var singleQuoteOpen = false
    private val mainHandler = Handler(Looper.getMainLooper())

    val isActive get() = language != null
    val isComposing get() = composing

    /** Call when the keyboard language changes. */
    fun onLanguageChanged(locale: Locale) {
        val newLanguage = Language.of(locale)
        if (newLanguage == language) return
        finishComposition(commit = true)
        language = newLanguage
        if (newLanguage != null) RimeData.prepare(context)
    }

    fun onStartInput() {
        resetComposition()
        quoteOpen = false
        singleQuoteOpen = false
    }

    fun onFinishInput() {
        // the editor is going away: keep what was typed as it is
        if (composing) connection.finishComposingText()
        resetComposition()
        RimeData.syncUserData()
    }

    /** The user moved the cursor away from the text being composed: keep the letters as typed. */
    fun onCursorMovedByUser() {
        if (!composing) return
        connection.finishComposingText()
        resetComposition()
    }

    /** Handles a key press. Returns false when HeliBoard should handle it as usual. */
    fun onEvent(event: Event): Boolean {
        val lang = language ?: return false
        if (event.isGesture || event.isConsumed) return false
        val code = event.codePoint
        val keyCode = event.keyCode

        if (keyCode == KeyCode.DELETE) {
            if (!composing) return false
            val s = session() ?: return false
            s.processKey(Rime.KEY_BACKSPACE)
            update(s)
            return true
        }
        if (code == Event.NOT_A_CODE_POINT || code < 0) {
            // functional keys: switching layouts keeps the composition, anything else
            // (emoji, clipboard, arrows, language switch...) takes the first candidate first
            if (composing && keyCode !in keepComposing) selectCandidate(0)
            return false
        }
        if (code in 'a'.code..'z'.code || (composing && code in 'A'.code..'Z'.code)) {
            val s = session() ?: return notReady()
            s.processKey(Character.toLowerCase(code))
            update(s)
            return true
        }
        if (code == '\''.code && composing) {
            val s = session() ?: return false
            s.processKey(code)
            update(s)
            return true
        }
        if (composing) {
            val s = session() ?: return false
            when (code) {
                Constants.CODE_SPACE -> {
                    selectCandidate(0)
                    return true
                }
                Constants.CODE_ENTER -> {
                    // Enter types the letters as they are
                    val raw = s.input
                    s.clearComposition()
                    connection.beginBatchEdit()
                    connection.commitText(raw, 1)
                    connection.endBatchEdit()
                    resetComposition()
                    return true
                }
            }
            // anything else (punctuation, digits, symbols): take the first candidate, then the key
            selectCandidate(0)
            if (composing) return true // the first candidate covered only part of the input
            return commitPunctuation(code)
        }
        return commitPunctuation(code)
    }

    /** Text from multi-character keys (emoji, ".com"…): finish the composition first. */
    fun onTextInput() {
        if (composing) selectCandidate(0)
    }

    /** Picks candidate [index] (0 = first of all candidates). */
    fun selectCandidate(index: Int) {
        val s = session ?: return
        if (!composing) return
        s.selectCandidate(index)
        update(s)
    }

    /** Fetches candidates beyond the first page, for the expanded candidate view. */
    fun candidates(start: Int, max: Int): List<Rime.Candidate> =
        if (composing) session?.candidates(start, max) ?: emptyList() else emptyList()

    /** Removes a learned word (only words Rime learned can be removed). */
    fun forgetCandidate(index: Int) {
        val s = session ?: return
        if (s.deleteCandidate(index)) update(s)
    }

    /** Traditional or Simplified output for the current language, from the 繁/简 button. */
    fun setTraditional(traditional: Boolean) {
        val lang = language ?: return
        LycheePrefs.setTraditional(context, lang, traditional)
        session?.let { applyOptions(it, lang) }
        if (composing) session?.let { update(it) }
    }

    fun isTraditional(): Boolean = language?.let { LycheePrefs.isTraditional(context, it) } ?: false

    private fun applyOptions(s: Rime.Session, lang: Language) {
        val traditional = LycheePrefs.isTraditional(context, lang)
        when (lang) {
            Language.MANDARIN -> s.setOption("traditionalization", traditional)
            Language.CANTONESE -> s.setOption("simplification", !traditional)
        }
        s.setOption("ascii_mode", false)
        s.setOption("emoji", LycheePrefs.emojiCandidates(context))
    }

    private fun session(): Rime.Session? {
        val lang = language ?: return null
        if (!Rime.isReady) return null
        val s = session?.takeIf { it.isValid } ?: Rime.Session().also { session = it; sessionSchema = null }
        if (!s.isValid) return null
        if (sessionSchema != lang.schema) {
            s.clearComposition()
            if (!s.selectSchema(lang.schema)) {
                Log.w(TAG, "could not select ${lang.schema}")
                return null
            }
            sessionSchema = lang.schema
        }
        applyOptions(s, lang)
        return s
    }

    private fun notReady(): Boolean {
        ui?.let { mainHandler.post { (it as? NotReadyUi)?.showPreparing() } }
        RimeData.prepare(context)
        return false // the letter is typed as is meanwhile
    }

    /** Commits Rime's output, shows the composition and candidates. */
    private fun update(s: Rime.Session) {
        val commit = s.getCommit()
        val composition = s.composition
        connection.beginBatchEdit()
        if (!commit.isNullOrEmpty()) {
            connection.commitText(commit, 1)
        }
        if (composition != null && composition.preedit.isNotEmpty()) {
            connection.setComposingText(composition.preedit, 1)
            composing = true
        } else {
            if (composing && commit.isNullOrEmpty()) connection.setComposingText("", 1)
            composing = false
        }
        connection.endBatchEdit()
        val lang = language
        if (composing && lang != null) {
            ui?.showCandidates(s.candidates(0, FIRST_PAGE), lang, composition?.preedit ?: "")
        } else {
            ui?.hideCandidates()
        }
    }

    private fun finishComposition(commit: Boolean) {
        if (!composing) return
        if (commit) selectCandidate(0) else onCursorMovedByUser()
        if (composing) { // first candidate did not cover everything
            connection.finishComposingText()
            resetComposition()
        }
    }

    private fun resetComposition() {
        session?.clearComposition()
        if (composing) ui?.hideCandidates()
        composing = false
    }

    /** Chinese punctuation for ,.?!:; and friends; returns false for everything else. */
    private fun commitPunctuation(code: Int): Boolean {
        if (!LycheePrefs.fullWidthPunctuation(context)) return false
        val before = connection.getCodePointBeforeCursor()
        val afterDigit = before in '0'.code..'9'.code
        val text = when (code) {
            ','.code -> if (afterDigit) null else "，"
            '.'.code -> if (afterDigit) null else "。"
            ':'.code -> if (afterDigit) null else "："
            '?'.code -> "？"
            '!'.code -> "！"
            ';'.code -> "；"
            '('.code -> "（"
            ')'.code -> "）"
            '<'.code -> "《"
            '>'.code -> "》"
            '['.code -> "【"
            ']'.code -> "】"
            '{'.code -> "「"
            '}'.code -> "」"
            '\\'.code -> "、"
            '~'.code -> "～"
            '^'.code -> "……"
            '_'.code -> "——"
            '$'.code -> "￥"
            '"'.code -> (if (quoteOpen) "”" else "“").also { quoteOpen = !quoteOpen }
            '\''.code -> (if (singleQuoteOpen) "’" else "‘").also { singleQuoteOpen = !singleQuoteOpen }
            else -> null
        } ?: return false
        connection.beginBatchEdit()
        connection.commitText(text, 1)
        connection.endBatchEdit()
        return true
    }

    /** Optional: a [CandidateUi] that can say Chinese input is still being prepared. */
    interface NotReadyUi {
        fun showPreparing()
    }

    companion object {
        private const val TAG = "ChineseInput"
        private val keepComposing = setOf(
            KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.ALPHA, KeyCode.SYMBOL, KeyCode.SYMBOL_ALPHA,
            KeyCode.NUMPAD, KeyCode.UNSPECIFIED,
        )
        const val FIRST_PAGE = 40
    }
}
