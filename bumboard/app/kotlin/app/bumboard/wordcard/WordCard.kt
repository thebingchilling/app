package app.bumboard.wordcard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import app.bumboard.candidates.AnnotatedCandidateView
import app.bumboard.core.Annotator
import app.bumboard.core.Headword
import app.bumboard.core.Lang
import app.bumboard.core.Resolved
import app.bumboard.core.Romanization
import app.bumboard.core.Sense
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.input.BaseInputView

/**
 * The full dictionary entry for a candidate, shown on long-press (patch 0002 hooks
 * BaseInputView.showCandidateActionMenu). Upstream's candidate actions (e.g. "forget word")
 * are offered as buttons at the bottom.
 */
object WordCard {

    private var popup: PopupWindow? = null

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    /** Returns false when there is nothing to show, so upstream's menu is used instead. */
    fun show(inputView: BaseInputView, idx: Int, text: String, anchor: View): Boolean {
        val annotator = BumBoard.annotatorOrNull() ?: return false
        val comment = AnnotatedCandidateView.find(anchor)?.candidate
            ?.takeIf { it.text == text }?.comment ?: ""
        val resolved = annotator.resolve(text, comment, BumBoard.mode) ?: return false
        if (resolved.headwords.isEmpty() && resolved.reading == null) return false
        dismiss()
        val ctx = inputView.context
        val theme = inputView.theme
        val card = CardContent(ctx, theme)
        card.fill(text, resolved)

        val window = PopupWindow(card.root, inputView.width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = ctx.dp(8).toFloat()
            setOnDismissListener { if (popup === this) popup = null }
        }
        card.onClose = { window.dismiss() }
        card.root.maxHeight = (inputView.height * 0.9).toInt().coerceAtLeast(ctx.dp(240))
        popup = window
        window.showAtLocation(inputView, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0)

        // upstream candidate actions, e.g. forgetting a learnt word
        inputView.service.lifecycleScope.launch {
            val actions = inputView.fcitx.runOnReady { getCandidateActions(idx) }
            actions.forEach { action ->
                card.addAction(action.text) {
                    window.dismiss()
                    inputView.fcitx.launchOnReady { it.triggerCandidateAction(idx, action.id) }
                }
            }
        }
        return true
    }

    private fun Context.dp(v: Int) = (resources.displayMetrics.density * v).toInt()

    private class MaxHeightFrame(ctx: Context) : FrameLayout(ctx) {
        var maxHeight = Int.MAX_VALUE
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }

    /** Builds the card's views. Plain Views: this runs inside the input method window. */
    private class CardContent(val ctx: Context, val theme: Theme) {

        var onClose: () -> Unit = {}

        private val fg = theme.popupTextColor
        private val muted = ColorUtils.setAlphaComponent(theme.popupTextColor, 0xA8)
        private val accent = theme.genericActiveBackgroundColor
        private val prefs = BumBoard.displayPrefs()

        private val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(12), ctx.dp(20), ctx.dp(8))
        }
        private val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(ctx.dp(12), 0, ctx.dp(12), ctx.dp(8))
        }

        val root = MaxHeightFrame(ctx).apply {
            background = GradientDrawable().apply {
                setColor(theme.popupBackgroundColor)
                cornerRadii = floatArrayOf(
                    ctx.dp(28).toFloat(), ctx.dp(28).toFloat(), ctx.dp(28).toFloat(), ctx.dp(28).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            column.addView(
                ScrollView(ctx).apply { addView(body); isVerticalScrollBarEnabled = true },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            column.addView(actions)
            addView(column)
        }

        fun addAction(label: String, onClick: () -> Unit) {
            actions.addView(button(label, onClick), 0)
        }

        fun fill(text: String, r: Resolved) {
            actions.addView(button(ctx.getString(R.string.bumboard_close)) { onClose() })
            val main = r.headwords.firstOrNull()
            // headword: Traditional · Simplified
            val forms = if (main != null && !r.composed && main.traditional != main.simplified)
                "${main.traditional} · ${main.simplified}" else text
            body.addView(textView(forms, 34f, fg, bold = false).apply { setTextIsSelectable(false) })

            readings(r, main).forEach { (label, value) -> body.addView(labelled(label, value)) }

            if (r.composed) {
                section(ctx.getString(R.string.bumboard_card_parts))
                r.headwords.forEach { hw -> part(hw, r.lang) }
                return
            }
            r.headwords.forEachIndexed { i, hw ->
                if (i > 0) {
                    body.addView(divider())
                    body.addView(textView("${hw.traditional} · ${hw.simplified}", 22f, fg))
                    readings(Resolved(r.lang, null, null, null, listOf(hw), false), hw)
                        .forEach { (label, value) -> body.addView(labelled(label, value)) }
                }
                senses(hw, r)
            }
        }

        /** All readings of the word in both languages, the matched one first. */
        private fun readings(r: Resolved, hw: Headword?): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            val yue = buildList {
                if (r.lang == Lang.Cantonese) r.reading?.let(::add)
                if (r.lang == Lang.Mandarin) r.otherReading?.let(::add)
                hw?.readings?.get(Lang.Cantonese)?.forEach { if (it !in this) add(it) }
            }.take(3)
            val cmn = buildList {
                if (r.lang == Lang.Mandarin) r.reading?.let(::add)
                if (r.lang == Lang.Cantonese) r.otherReading?.let(::add)
                hw?.readings?.get(Lang.Mandarin)?.forEach { if (it !in this) add(it) }
            }.take(3)
            if (yue.isNotEmpty()) {
                out += "Jyutping" to yue.joinToString("  ·  ")
                out += "Yale" to yue.joinToString("  ·  ") { Annotator.yale(it, prefs.copy(yaleToneMarks = true)) }
            }
            if (cmn.isNotEmpty()) {
                out += "Pinyin" to cmn.joinToString("  ·  ") {
                    Romanization.format(it, Romanization.Style.PinyinMarks)
                }
            }
            return out
        }

        private fun senses(hw: Headword, r: Resolved) {
            val groups = listOf(
                Lang.Cantonese to R.string.bumboard_card_cantonese,
                Lang.Mandarin to R.string.bumboard_card_mandarin,
                Lang.Any to R.string.bumboard_card_character,
            ).let { if (r.lang == Lang.Mandarin) listOf(it[1], it[0], it[2]) else it }
            for ((lang, title) in groups) {
                val list = hw.senses.filter { it.lang == lang && it.partOfSpeech != "cl" }
                if (list.isEmpty()) continue
                section(ctx.getString(title))
                list.take(MAX_SENSES).forEachIndexed { i, s -> sense(i + 1, s) }
                val classifiers = hw.senses.filter { it.lang == lang && it.partOfSpeech == "cl" }
                classifiers.forEach { body.addView(note(it.gloss.removePrefix("CL:").let { cl -> "${ctx.getString(R.string.bumboard_card_measure_word)} $cl" })) }
            }
        }

        private fun sense(n: Int, s: Sense) {
            val sb = SpannableStringBuilder()
            sb.append("$n  ", ForegroundColorSpan(muted), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(s.gloss)
            val tags = listOfNotNull(s.partOfSpeech?.let(::posName), s.register?.let(::registerName), s.reading?.let {
                if (s.lang == Lang.Mandarin) Romanization.format(it, Romanization.Style.PinyinMarks) else it
            }).filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                sb.append("   ")
                val start = sb.length
                sb.append(tags.joinToString(" · "))
                sb.setSpan(ForegroundColorSpan(muted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            body.addView(textView(sb, 15f, fg).apply { setPadding(0, ctx.dp(4), 0, ctx.dp(2)) })
            s.written?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.bumboard_card_written)} $it"))
            }
            s.vernacular?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.bumboard_card_vernacular)} $it"))
            }
        }

        private fun part(hw: Headword, lang: Lang) {
            val reading = hw.readings[lang]?.firstOrNull()?.let {
                if (lang == Lang.Cantonese) it else Romanization.format(it, Romanization.Style.PinyinMarks)
            } ?: ""
            val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss?.let { Annotator.shorten(it, 80) } ?: ""
            body.addView(textView("${hw.traditional}  $reading  —  $gloss", 15f, fg).apply {
                setPadding(0, ctx.dp(4), 0, ctx.dp(2))
            })
        }

        private fun section(title: String) {
            body.addView(textView(title.uppercase(), 12f, accent, bold = true).apply {
                letterSpacing = 0.08f
                setPadding(0, ctx.dp(14), 0, ctx.dp(2))
            })
        }

        private fun labelled(label: String, value: String): TextView {
            val sb = SpannableStringBuilder()
            sb.append(label, ForegroundColorSpan(muted), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("   ")
            val start = sb.length
            sb.append(value)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return textView(sb, 16f, fg).apply { setPadding(0, ctx.dp(2), 0, 0) }
        }

        private fun note(t: String) = textView(t, 13f, muted).apply { setPadding(ctx.dp(20), 0, 0, ctx.dp(2)) }

        private fun divider() = View(ctx).apply {
            setBackgroundColor(theme.dividerColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(1)).apply {
                topMargin = ctx.dp(14); bottomMargin = ctx.dp(10)
            }
        }

        private fun textView(t: CharSequence, sp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        private fun button(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            background = GradientDrawable().apply {
                cornerRadius = ctx.dp(20).toFloat()
                setColor(ColorUtils.setAlphaComponent(accent, 0x1F))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = ctx.dp(8) }
            setOnClickListener { onClick() }
        }

        private fun posName(code: String) = POS[code] ?: code
        private fun registerName(code: String) = REGISTERS[code] ?: code

        companion object {
            const val MAX_SENSES = 12
            // TypeDuck codes (see TypeDuck-Web consts.ts)
            val POS = mapOf(
                "n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb",
                "morph" to "morpheme", "mw" to "measure word", "part" to "particle", "oth" to "other",
                "x" to "", "pron" to "pronoun", "prep" to "preposition", "conj" to "conjunction",
                "num" to "number", "intj" to "interjection", "aff" to "affix",
            )
            val REGISTERS = mapOf(
                "wri" to "written", "ver" to "vernacular", "for" to "formal", "lzh" to "classical",
                "yue" to "Cantonese", "cmn" to "Mandarin",
            )
        }
    }
}
