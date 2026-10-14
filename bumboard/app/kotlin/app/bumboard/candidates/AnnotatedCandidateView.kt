package app.bumboard.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme

/**
 * One candidate: the word, then its romanisation line(s), then a one-line English meaning.
 * Used by upstream's CandidateItemUi (patch 0002) for the candidate bar and the expanded grid.
 */
@SuppressLint("ViewConstructor")
class AnnotatedCandidateView(ctx: Context, private val theme: Theme) : LinearLayout(ctx) {

    var candidate: CandidateWord = CandidateWord.Empty
        private set

    private val word = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(theme.candidateTextColor)
        isSingleLine = true
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private val readingViews = List(3) {
        small(ctx, 11.5f, theme.candidateCommentColor).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private val meaning = small(ctx, 10.5f, ColorUtils.setAlphaComponent(theme.candidateTextColor, 0xB0)).apply {
        maxWidth = (resources.displayMetrics.density * 132).toInt()
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(word, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        readingViews.forEach { addView(it, smallParams()) }
        addView(meaning, smallParams())
    }

    private fun smallParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = (resources.displayMetrics.density * 1).toInt()
    }

    fun update(c: CandidateWord) {
        candidate = c
        word.text = c.text
        val annotation = BumBoard.annotatorOrNull()
            ?.annotate(c.text, c.comment, BumBoard.mode, BumBoard.displayPrefs())
        val lines = annotation?.lines.orEmpty()
        readingViews.forEachIndexed { i, v ->
            val line = lines.getOrNull(i)
            v.text = line
            v.visibility = if (line == null) View.GONE else View.VISIBLE
        }
        val m = annotation?.meaning
        meaning.text = m
        meaning.visibility = if (m == null) View.GONE else View.VISIBLE
        // Latin / punctuation candidates without readings keep upstream's comment (e.g. emoji names)
        if (annotation == null || (lines.isEmpty() && m == null)) {
            if (c.comment.isNotBlank() && BumBoard.mode == app.bumboard.core.InputMode.Other) {
                readingViews[0].text = c.comment
                readingViews[0].visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private fun small(ctx: Context, sp: Float, color: Int) = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            includeFontPadding = false
            visibility = View.GONE
        }

        /** The annotated view inside a candidate cell, if [cell] is one. */
        fun find(cell: View): AnnotatedCandidateView? = when (cell) {
            is AnnotatedCandidateView -> cell
            is android.view.ViewGroup -> (0 until cell.childCount).firstNotNullOfOrNull { find(cell.getChildAt(it)) }
            else -> null
        }
    }
}
