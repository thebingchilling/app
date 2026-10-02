// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lychee.LycheePrefs
import app.lychee.chinese.ChineseInput.Language
import app.lychee.readings.CandidateLines
import app.lychee.readings.Readings
import app.lychee.readings.Romanization
import app.lychee.readings.WordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * The long-press card: everything Lychee knows about a candidate, nothing cut off. Every
 * Cantonese and Mandarin reading, every English sense from each dictionary, and the
 * characters one by one.
 */
@SuppressLint("ViewConstructor")
class WordCard(context: Context, private val onClose: () -> Unit, private val onForget: () -> Unit) : LinearLayout(context) {
    private val content = LinearLayout(context)
    private val textColor = Settings.getValues().mColors.get(ColorType.KEY_TEXT)
    private val hintColor = Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT)
    private val accent = Settings.getValues().mColors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)

    init {
        orientation = VERTICAL
        Settings.getValues().mColors.setBackground(this, ColorType.MAIN_BACKGROUND)
        isClickable = true
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("Forget word") { onForget() }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
        header.addView(android.view.View(context), LayoutParams(0, 1, 1f))
        header.addView(button("✕") { onClose() }, LayoutParams(dp(48), dp(40)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.orientation = VERTICAL
        content.setPadding(dp(16), 0, dp(16), dp(16))
        val scroll = ScrollView(context)
        scroll.addView(content)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show(word: String, lines: CandidateLines, language: Language, canForget: Boolean) {
        content.removeAllViews()
        (getChildAt(0) as LinearLayout).getChildAt(0).visibility = if (canForget) VISIBLE else INVISIBLE
        val jyutpingOnly = LycheePrefs.useJyutping(context)
        content.addView(text(word, 34f, textColor, bold = false))
        val info = lines.info
        if (info == null) {
            content.addView(text("No reading or meaning for this candidate.", 14f, hintColor))
            return
        }
        // Readings: Cantonese first (Yale with Jyutping), then Mandarin
        if (info.jyutping.isNotEmpty()) {
            val shown = info.jyutping.joinToString("   ") {
                if (jyutpingOnly) it else "${Romanization.jyutpingToYale(it)} ($it)"
            }
            content.addView(labelled("粵 Cantonese", shown))
        }
        if (info.pinyin.isNotEmpty()) {
            content.addView(labelled("普 Mandarin", info.pinyin.joinToString("   ") { Romanization.pinyinToMarks(it) }))
        }
        // Meanings, from the dictionary that suits the language first
        val sections = buildList {
            if (info.typeduck.isNotEmpty()) add("TypeDuck" to info.typeduck.map { (pos, en) -> if (pos.isNotBlank()) "($pos) $en" else en })
            if (info.cedict.isNotEmpty()) add("CC-CEDICT" to info.cedict)
            if (info.canto.isNotEmpty()) add("CC-Canto" to info.canto)
        }.let { if (language == Language.MANDARIN) it.sortedBy { (name, _) -> if (name == "CC-CEDICT") 0 else 1 } else it }
        for ((name, senses) in sections) {
            content.addView(text(name, 12f, accent, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
            senses.forEachIndexed { i, sense ->
                content.addView(text(if (senses.size > 1) "${i + 1}. $sense" else sense, 15f, textColor))
            }
        }
        // Characters one by one (also the pieces of words not in the dictionary)
        val pieces = if (info.parts.isNotEmpty()) info.parts
            else if (word.codePointCount(0, word.length) > 1) word.codePoints().toArray().toList().mapNotNull { Readings.lookup(String(Character.toChars(it))) }
            else emptyList()
        if (pieces.isNotEmpty()) {
            content.addView(text(if (info.parts.isNotEmpty()) "Pieces" else "Characters", 12f, accent, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
            for (piece in pieces) content.addView(pieceRow(piece, language, jyutpingOnly))
        }
        if (sections.isEmpty() && pieces.isEmpty()) {
            content.addView(text("No English meaning in Lychee's dictionaries.", 14f, hintColor))
        }
    }

    private fun pieceRow(piece: WordInfo, language: Language, jyutpingOnly: Boolean): TextView {
        val sb = SpannableStringBuilder()
        sb.append(piece.word, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val readings = listOfNotNull(
            piece.jyutping.firstOrNull()?.let { if (jyutpingOnly) it else Romanization.jyutpingToYale(it) },
            piece.pinyin.firstOrNull()?.let { Romanization.pinyinToMarks(it) },
        )
        if (readings.isNotEmpty()) sb.append("  ").append(readings.joinToString(" · "))
        CandidateLines.shortMeaning(piece, language)?.let { sb.append(" — ").append(it) }
        return text("", 15f, textColor).apply { text = sb }
    }

    private fun labelled(label: String, value: String) = text("", 16f, textColor).apply {
        val sb = SpannableStringBuilder()
        sb.append(label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  ").append(value)
        text = sb
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setTextIsSelectable(false)
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(textColor)
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }
        background = CandidateCell.pressedBackground(context)
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
