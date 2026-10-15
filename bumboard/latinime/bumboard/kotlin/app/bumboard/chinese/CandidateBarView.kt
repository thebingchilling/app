package app.bumboard.chinese

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.core.Annotation
import app.bumboard.rime.Candidate
import com.android.inputmethod.latin.R

/**
 * A scrolling row of Chinese candidates shown in place of LatinIME's suggestion strip.
 * Each candidate can carry its reading(s) and a short English meaning underneath.
 * Colours come from the keyboard theme's suggestion strip style so it matches the keys.
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    var onPick: (Int) -> Unit = {}
    var onLongPick: (Int) -> Unit = {}

    /** Reading and meaning for a candidate, or null when there is nothing to show. */
    var annotate: (Candidate) -> Annotation? = { null }

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val textColor: Int
    private val firstColor: Int

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val a = context.obtainStyledAttributes(
            null, R.styleable.SuggestionStripView, R.attr.suggestionStripViewStyle, R.style.SuggestionStripView,
        )
        textColor = a.getColor(R.styleable.SuggestionStripView_colorSuggested, 0xFF000000.toInt())
        firstColor = a.getColor(R.styleable.SuggestionStripView_colorAutoCorrect, textColor)
        a.recycle()
    }

    /** Sets a fixed height for the bar, so the keyboard does not jump as candidates change. */
    fun setBarHeight(px: Int) {
        val lp = layoutParams ?: return
        if (lp.height != px) {
            lp.height = px
            layoutParams = lp
        }
    }

    fun setCandidates(candidates: List<Candidate>) {
        row.removeAllViews()
        candidates.forEachIndexed { index, candidate ->
            row.addView(cell(index, candidate, annotate(candidate)), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        scrollTo(0, 0)
    }

    private fun cell(index: Int, candidate: Candidate, annotation: Annotation?): View {
        val color = if (index == 0) firstColor else textColor
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            minimumWidth = dp(44)
            background = selectableBackground()
            setOnClickListener { onPick(index) }
            setOnLongClickListener { onLongPick(index); true }
        }
        cell.addView(text(candidate.text, 20f, color).apply { includeFontPadding = false }, wrap())
        annotation?.lines?.forEach { line ->
            cell.addView(text(line, 11.5f, ColorUtils.setAlphaComponent(color, 0xD0)).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }, wrap())
        }
        annotation?.meaning?.let { meaning ->
            cell.addView(text(meaning, 10.5f, ColorUtils.setAlphaComponent(color, 0xA0)).apply {
                maxWidth = dp(132)
            }, wrap())
        }
        return cell
    }

    private fun text(value: String, sp: Float, color: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
    }

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun selectableBackground() = TypedValue().let { value ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        context.getDrawable(value.resourceId)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
