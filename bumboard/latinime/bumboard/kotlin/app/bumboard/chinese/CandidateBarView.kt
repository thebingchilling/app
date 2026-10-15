package app.bumboard.chinese

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import app.bumboard.rime.Candidate
import com.android.inputmethod.latin.R

/**
 * A scrolling row of Chinese candidates shown in place of LatinIME's suggestion strip.
 * Colours come from the keyboard theme's suggestion strip style so it matches the keys.
 */
class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    var onPick: (Int) -> Unit = {}

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

    fun setCandidates(candidates: List<Candidate>) {
        row.removeAllViews()
        val padding = dp(14)
        candidates.forEachIndexed { index, candidate ->
            val view = TextView(context).apply {
                text = candidate.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(if (index == 0) firstColor else textColor)
                gravity = Gravity.CENTER
                setPadding(padding, 0, padding, 0)
                minWidth = dp(44)
                background = selectableBackground()
                setOnClickListener { onPick(index) }
            }
            row.addView(view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        scrollTo(0, 0)
    }

    private fun selectableBackground() = TypedValue().let { value ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        context.getDrawable(value.resourceId)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
