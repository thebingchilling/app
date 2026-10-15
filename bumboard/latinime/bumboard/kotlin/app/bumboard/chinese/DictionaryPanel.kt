package app.bumboard.chinese

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import android.widget.ScrollView
import android.widget.TextView
import app.bumboard.core.Annotator
import app.bumboard.core.DisplayPrefs
import app.bumboard.core.Headword
import app.bumboard.core.Lang
import app.bumboard.core.Resolved
import app.bumboard.core.Romanization
import app.bumboard.core.Sense
import com.android.inputmethod.latin.R

/**
 * The full dictionary entry for a candidate, shown over the keys on long-press.
 * Plain Views (this runs inside the input method window) in Material 3 colours:
 * the wallpaper-based system palette on Android 12+, BumBoard's teal palette before.
 */
class DictionaryPanel(private val host: FrameLayout) {
    private val ctx: Context = host.context
    private var root: View? = null

    val isShowing: Boolean
        get() = root != null

    fun dismiss() {
        root?.let { host.removeView(it) }
        root = null
    }

    /** Shows [resolved] for [text] covering the bottom [height] pixels of [host]. */
    fun show(text: String, resolved: Resolved, prefs: DisplayPrefs, height: Int, onType: () -> Unit) {
        dismiss()
        val c = Colors.of(ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        Content(ctx, c, prefs, body).fill(text, resolved)

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(16), dp(8), dp(16), dp(12))
            addView(button(ctx.getString(R.string.bum_close), c, filled = false) { dismiss() })
            addView(button(ctx.getString(R.string.bum_type_it), c, filled = true) {
                dismiss()
                onType()
            })
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(c.surface)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            elevation = dp(6).toFloat()
            isClickable = true // keep touches away from the keys underneath
            addView(ScrollView(ctx).apply { addView(body) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(actions)
        }
        host.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM))
        root = panel
    }

    private fun button(label: String, c: Colors, filled: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) c.onPrimary else c.primary)
        setPadding(dp(24), dp(10), dp(24), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            if (filled) setColor(c.primary) else setStroke(dp(1), c.outline)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    /** The Material 3 colour roles the panel uses. */
    private data class Colors(
        val surface: Int, val onSurface: Int, val onSurfaceVariant: Int,
        val primary: Int, val onPrimary: Int, val outline: Int,
    ) {
        companion object {
            fun of(ctx: Context): Colors {
                val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    fun c(id: Int) = ctx.getColor(id)
                    return if (dark) Colors(
                        c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral1_100),
                        c(android.R.color.system_neutral2_200), c(android.R.color.system_accent1_200),
                        c(android.R.color.system_accent1_800), c(android.R.color.system_neutral2_400),
                    ) else Colors(
                        c(android.R.color.system_neutral1_10), c(android.R.color.system_neutral1_900),
                        c(android.R.color.system_neutral2_700), c(android.R.color.system_accent1_600),
                        c(android.R.color.system_accent1_0), c(android.R.color.system_neutral2_500),
                    )
                }
                return if (dark) Colors(
                    0xFF0E1513.toInt(), 0xFFDEE4E1.toInt(), 0xFFBEC9C5.toInt(),
                    0xFF82D5C3.toInt(), 0xFF00382F.toInt(), 0xFF899390.toInt(),
                ) else Colors(
                    0xFFF5FBF8.toInt(), 0xFF171D1B.toInt(), 0xFF3F4946.toInt(),
                    0xFF006A5D.toInt(), 0xFFFFFFFF.toInt(), 0xFF6F7976.toInt(),
                )
            }
        }
    }

    /** Fills the scrolling body with the entry. */
    private class Content(val ctx: Context, val c: Colors, val prefs: DisplayPrefs, val body: LinearLayout) {

        fun fill(text: String, r: Resolved) {
            val main = r.headwords.firstOrNull()
            // headword: Traditional · Simplified
            val forms = if (main != null && !r.composed && main.traditional != main.simplified)
                "${main.traditional} · ${main.simplified}" else text
            body.addView(textView(forms, 34f, c.onSurface))
            readings(r, main).forEach { (label, value) -> body.addView(labelled(label, value)) }

            if (r.composed) {
                section(ctx.getString(R.string.bum_card_parts))
                r.headwords.forEach { hw -> part(hw, r.lang) }
                return
            }
            r.headwords.forEachIndexed { i, hw ->
                if (i > 0) {
                    body.addView(divider())
                    body.addView(textView("${hw.traditional} · ${hw.simplified}", 22f, c.onSurface))
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
                Lang.Cantonese to R.string.bum_card_cantonese,
                Lang.Mandarin to R.string.bum_card_mandarin,
                Lang.Any to R.string.bum_card_character,
            ).let { if (r.lang == Lang.Mandarin) listOf(it[1], it[0], it[2]) else it }
            for ((lang, title) in groups) {
                val list = hw.senses.filter { it.lang == lang && it.partOfSpeech != "cl" }
                if (list.isEmpty()) continue
                section(ctx.getString(title))
                list.take(MAX_SENSES).forEachIndexed { i, s -> sense(i + 1, s) }
                hw.senses.filter { it.lang == lang && it.partOfSpeech == "cl" }.forEach {
                    body.addView(note("${ctx.getString(R.string.bum_card_measure_word)} ${it.gloss.removePrefix("CL:")}"))
                }
            }
        }

        private fun sense(n: Int, s: Sense) {
            val muted = c.onSurfaceVariant
            val sb = SpannableStringBuilder()
            sb.append("$n  ", ForegroundColorSpan(muted), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(s.gloss)
            val tags = listOfNotNull(
                s.partOfSpeech?.let { POS[it] ?: it },
                s.register?.let { REGISTERS[it] ?: it },
                s.reading?.let {
                    if (s.lang == Lang.Mandarin) Romanization.format(it, Romanization.Style.PinyinMarks) else it
                },
            ).filter { it.isNotBlank() }
            if (tags.isNotEmpty()) {
                sb.append("   ")
                val start = sb.length
                sb.append(tags.joinToString(" · "))
                sb.setSpan(ForegroundColorSpan(muted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            body.addView(textView(sb, 15f, c.onSurface).apply { setPadding(0, dp(4), 0, dp(2)) })
            s.written?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.bum_card_written)} $it"))
            }
            s.vernacular?.takeIf { it.isNotBlank() }?.let {
                body.addView(note("${ctx.getString(R.string.bum_card_vernacular)} $it"))
            }
        }

        private fun part(hw: Headword, lang: Lang) {
            val reading = hw.readings[lang]?.firstOrNull()?.let {
                if (lang == Lang.Cantonese) it else Romanization.format(it, Romanization.Style.PinyinMarks)
            } ?: ""
            val gloss = hw.senses.firstOrNull { it.partOfSpeech != "cl" }?.gloss?.let { Annotator.shorten(it, 80) } ?: ""
            body.addView(textView("${hw.traditional}  $reading  —  $gloss", 15f, c.onSurface).apply {
                setPadding(0, dp(4), 0, dp(2))
            })
        }

        private fun section(title: String) {
            body.addView(textView(title, 14f, c.primary).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(16), 0, dp(4))
            })
        }

        private fun labelled(label: String, value: String): TextView {
            val sb = SpannableStringBuilder()
            sb.append(label, ForegroundColorSpan(c.onSurfaceVariant), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("   ")
            val start = sb.length
            sb.append(value)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return textView(sb, 16f, c.onSurface).apply { setPadding(0, dp(2), 0, 0) }
        }

        private fun note(t: String) = textView(t, 13f, c.onSurfaceVariant).apply {
            setPadding(dp(20), 0, 0, dp(2))
        }

        private fun divider() = View(ctx).apply {
            setBackgroundColor(c.outline)
            alpha = 0.4f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(14); bottomMargin = dp(10)
            }
        }

        private fun textView(t: CharSequence, sp: Float, color: Int) = TextView(ctx).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
        }

        private fun dp(v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

        companion object {
            const val MAX_SENSES = 12
            // TypeDuck codes
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
