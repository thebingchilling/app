// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.LycheePrefs
import app.lychee.chinese.ChineseInput.Language
import app.lychee.readings.CandidateLines
import app.lychee.rime.Rime
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** What the candidate views report back. */
interface CandidateListener {
    fun onPick(index: Int)
    fun onLongPress(index: Int, text: String, lines: CandidateLines)
    fun onExpand()
    fun onCollapse()
}

/** Which lines to show under each candidate, read once per update. */
class LineSettings(context: Context) {
    val cantonese = LycheePrefs.showYale(context)
    val mandarin = LycheePrefs.showPinyin(context)
    val english = LycheePrefs.showEnglish(context)
    val wrap = LycheePrefs.wrapMeanings(context)
    val jyutping = LycheePrefs.useJyutping(context)
    val anyLine get() = cantonese || mandarin || english

    companion object {
        /** Height of the candidate bar in dp for these settings. */
        fun barHeightDp(context: Context): Int {
            val s = LineSettings(context)
            if (!s.anyLine) return 44
            var h = 34 // the word
            if (s.cantonese || s.mandarin) h += 14
            if (s.english) h += if (s.wrap) 27 else 14
            return h + 6
        }
    }
}

private fun Context.dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
private fun Context.dpi(value: Int) = dp(value.toFloat()).toInt()

/** One candidate: the word, then "Yale · pinyin", then the English meaning. */
class CandidateCell(context: Context, private val inGrid: Boolean) : LinearLayout(context) {
    private val word = TextView(context)
    private val reading = TextView(context)
    private val meaning = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = context.dpi(if (inGrid) 4 else 10)
        setPadding(pad, context.dpi(2), pad, context.dpi(2))
        val colors = Settings.getValues().mColors
        val text = colors.get(ColorType.KEY_TEXT)
        val hint = colors.get(ColorType.KEY_HINT_TEXT)
        word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        word.setTextColor(text)
        word.maxLines = 1
        word.ellipsize = TextUtils.TruncateAt.END
        reading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        reading.setTextColor(hint)
        reading.maxLines = 1
        reading.ellipsize = TextUtils.TruncateAt.END
        meaning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        meaning.setTextColor(text)
        meaning.alpha = 0.85f
        for (v in listOf(word, reading, meaning)) {
            v.gravity = Gravity.CENTER
            v.includeFontPadding = false
            addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        background = pressedBackground(context)
        isClickable = true
        isLongClickable = true
    }

    fun bind(candidate: Rime.Candidate, lines: CandidateLines, settings: LineSettings, first: Boolean) {
        word.text = candidate.text
        word.setTypeface(null, if (first && !inGrid) Typeface.BOLD else Typeface.NORMAL)
        val readingParts = listOfNotNull(
            lines.cantonese.takeIf { settings.cantonese },
            lines.mandarin.takeIf { settings.mandarin },
        )
        reading.visibility = if (settings.cantonese || settings.mandarin) VISIBLE else GONE
        reading.text = readingParts.joinToString(" · ")
        meaning.visibility = if (settings.english) VISIBLE else GONE
        meaning.text = lines.english ?: ""
        val wrap = settings.wrap || inGrid
        meaning.maxLines = if (wrap) 2 else 1
        meaning.ellipsize = TextUtils.TruncateAt.END
        // keep cells readable but not huge: long meanings are cut (or wrapped), the card has them all
        val max = context.dpi(if (inGrid) 400 else if (settings.wrap) 150 else 180)
        meaning.maxWidth = max
        reading.maxWidth = max
        word.maxWidth = context.dpi(if (inGrid) 400 else 240)
        contentDescription = listOfNotNull(candidate.text, reading.text.toString().ifEmpty { null }, lines.english).joinToString(", ")
    }

    companion object {
        fun pressedBackground(context: Context) = android.graphics.drawable.StateListDrawable().apply {
            val pressed = GradientDrawable().apply {
                cornerRadius = context.dp(6f)
                setColor((Settings.getValues().mColors.get(ColorType.KEY_TEXT) and 0x00FFFFFF) or 0x22000000)
            }
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
        }
    }
}

/** The candidate bar that replaces HeliBoard's suggestion strip while typing Chinese. */
@SuppressLint("ViewConstructor")
class CandidateBar(context: Context, private val listener: CandidateListener) : LinearLayout(context) {
    private val scroll = HorizontalScrollView(context)
    private val row = LinearLayout(context)
    private val expand = TextView(context)
    private val cells = ArrayList<CandidateCell>()
    private var shownLines: List<CandidateLines> = emptyList()
    private var shown: List<Rime.Candidate> = emptyList()

    init {
        orientation = HORIZONTAL
        Settings.getValues().mColors.setBackground(this, ColorType.STRIP_BACKGROUND)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.isFillViewport = true
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        expand.text = "⌄"
        expand.gravity = Gravity.CENTER
        expand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        expand.setTextColor(Settings.getValues().mColors.get(ColorType.TOOL_BAR_EXPAND_KEY))
        expand.background = CandidateCell.pressedBackground(context)
        expand.contentDescription = "More candidates"
        expand.setOnClickListener { listener.onExpand() }
        addView(expand, LayoutParams(context.dpi(40), LayoutParams.MATCH_PARENT))
    }

    fun show(candidates: List<Rime.Candidate>, lines: List<CandidateLines>, settings: LineSettings) {
        shown = candidates
        shownLines = lines
        while (cells.size < candidates.size) {
            val cell = CandidateCell(context, inGrid = false)
            val i = cells.size
            cell.setOnClickListener { listener.onPick(i) }
            cell.setOnLongClickListener { shown.getOrNull(i)?.let { c -> listener.onLongPress(i, c.text, shownLines[i]) }; true }
            cells.add(cell)
        }
        row.removeAllViews()
        candidates.forEachIndexed { i, c ->
            cells[i].bind(c, lines[i], settings, i == 0)
            row.addView(cells[i], LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                minimumWidth = context.dpi(48)
            })
        }
        expand.visibility = if (candidates.size > 1) VISIBLE else INVISIBLE
        scroll.scrollTo(0, 0)
    }
}

/** All candidates in a grid over the keys (the bar's ⌄ button). */
@SuppressLint("ViewConstructor", "NotifyDataSetChanged")
class CandidateGrid(context: Context, private val listener: CandidateListener) : LinearLayout(context) {
    private val list = RecyclerView(context)
    private var items = ArrayList<Pair<Rime.Candidate, CandidateLines>>()
    private var settings = LineSettings(context)
    private var loadMore: ((Int) -> List<Pair<Rime.Candidate, CandidateLines>>)? = null
    private var exhausted = false

    init {
        orientation = VERTICAL
        Settings.getValues().mColors.setBackground(this, ColorType.MAIN_BACKGROUND)
        isClickable = true // keep touches away from the keys underneath
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.END }
        val close = TextView(context).apply {
            text = "⌃"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Settings.getValues().mColors.get(ColorType.TOOL_BAR_EXPAND_KEY))
            background = CandidateCell.pressedBackground(context)
            contentDescription = "Fewer candidates"
            setOnClickListener { listener.onCollapse() }
        }
        header.addView(close, LayoutParams(context.dpi(48), context.dpi(36)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val columns = 4
        val manager = GridLayoutManager(context, columns * 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                // long words take a whole row, two-character words half a row
                override fun getSpanSize(position: Int): Int {
                    val n = items.getOrNull(position)?.first?.text?.codePointCount(0, items[position].first.text.length) ?: 1
                    return when {
                        n >= 5 -> columns * 2
                        n >= 3 -> columns
                        else -> 2
                    }
                }
            }
        }
        list.layoutManager = manager
        list.adapter = Adapter()
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!exhausted && manager.findLastVisibleItemPosition() >= items.size - 8) more()
            }
        })
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun show(first: List<Pair<Rime.Candidate, CandidateLines>>, loader: (Int) -> List<Pair<Rime.Candidate, CandidateLines>>) {
        settings = LineSettings(context)
        items = ArrayList(first)
        loadMore = loader
        exhausted = false
        more()
        list.adapter?.notifyDataSetChanged()
        list.scrollToPosition(0)
    }

    private fun more() {
        val next = loadMore?.invoke(items.size).orEmpty()
        if (next.isEmpty()) {
            exhausted = true
            return
        }
        val start = items.size
        items.addAll(next)
        list.post { list.adapter?.notifyItemRangeInserted(start, next.size) }
    }

    private inner class Holder(val cell: CandidateCell) : RecyclerView.ViewHolder(cell)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val cell = CandidateCell(context, inGrid = true)
            cell.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(LineSettings.barHeightDp(context) + 8))
            return Holder(cell)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (candidate, lines) = items[position]
            holder.cell.bind(candidate, lines, settings, false)
            holder.cell.setOnClickListener { listener.onPick(holder.bindingAdapterPosition) }
            holder.cell.setOnLongClickListener {
                val p = holder.bindingAdapterPosition
                items.getOrNull(p)?.let { listener.onLongPress(p, it.first.text, it.second) }
                true
            }
        }
    }
}
