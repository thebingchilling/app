package app.bumboard.toolbar

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow

/**
 * Resize the keyboard from the keyboard itself (Gboard's "Resize"). Changes apply live:
 * upstream's InputView listens to these preferences.
 */
class ResizeWindow : InputWindow.ExtendedInputWindow<ResizeWindow>() {

    private val theme by manager.theme()
    private val keyboard = AppPrefs.getInstance().keyboard

    override val title: String get() = "Resize"

    private val landscape: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private data class Row(val label: String, val pref: ManagedPreference.PInt, val min: Int, val max: Int, val unit: String, val default: Int)

    private val rows by lazy {
        if (landscape) listOf(
            Row("Height", keyboard.keyboardHeightPercentLandscape, 10, 90, "%", 49),
            Row("Side space", keyboard.keyboardSidePaddingLandscape, 0, 300, " dp", 0),
            Row("Bottom space", keyboard.keyboardBottomPaddingLandscape, 0, 100, " dp", 0),
        ) else listOf(
            Row("Height", keyboard.keyboardHeightPercent, 10, 90, "%", 30),
            Row("Side space", keyboard.keyboardSidePadding, 0, 120, " dp", 0),
            Row("Bottom space", keyboard.keyboardBottomPadding, 0, 100, " dp", 0),
        )
    }

    private val view by lazy {
        val d = context.resources.displayMetrics.density
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * d).toInt()
            setPadding(p, (8 * d).toInt(), p, p)
            val bars = rows.map { row -> addRow(row) }
            addView(TextView(context).apply {
                text = "Reset"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(theme.genericActiveBackgroundColor)
                gravity = Gravity.CENTER
                val h = (16 * d).toInt()
                setPadding(h, (10 * d).toInt(), h, (10 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 20 * d
                    setColor(ColorUtils.setAlphaComponent(theme.genericActiveBackgroundColor, 0x24))
                }
                setOnClickListener {
                    rows.zip(bars).forEach { (row, bar) ->
                        row.pref.setValue(row.default)
                        bar.progress = row.default - row.min
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = (12 * d).toInt()
            })
        }
    }

    private fun LinearLayout.addRow(row: Row): SeekBar {
        val d = context.resources.displayMetrics.density
        val value = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.genericActiveBackgroundColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = row.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(theme.keyTextColor)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
        addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * d).toInt()
        })
        val bar = SeekBar(context).apply {
            max = row.max - row.min
            progress = row.pref.getValue() - row.min
            progressTintList = ColorStateList.valueOf(theme.genericActiveBackgroundColor)
            thumbTintList = ColorStateList.valueOf(theme.genericActiveBackgroundColor)
            progressBackgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.keyTextColor, 0x40))
        }
        fun show(v: Int) { value.text = "$v${row.unit}" }
        show(row.pref.getValue())
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) = show(progress + row.min)
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) = row.pref.setValue(s.progress + row.min)
        })
        addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40 * d).toInt()))
        return bar
    }

    override fun onCreateView(): View = view

    override fun onAttached() {}

    override fun onDetached() {}
}
