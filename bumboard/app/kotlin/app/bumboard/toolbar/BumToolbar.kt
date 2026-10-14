package app.bumboard.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import com.google.android.flexbox.FlexboxLayout
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi

/**
 * BumBoard's toolbar buttons, added in front of upstream's (patch 0002 hooks ButtonsBarUi):
 * language (粵 / 普 / EN), English→Chinese lookup, and 繁/简.
 */
object BumToolbar {

    fun attach(bar: ButtonsBarUi, ctx: Context, theme: Theme) {
        BumBoard.attach()
        val language = LabelButton(ctx, theme).apply {
            contentDescription = "Language"
            setOnClickListener { LanguageControls.cycleLanguage() }
        }
        val lookup = LabelButton(ctx, theme).apply {
            contentDescription = "English to Chinese"
            setOnClickListener { LanguageControls.toggleEnglishLookup() }
        }
        val script = LabelButton(ctx, theme).apply {
            contentDescription = "Traditional or Simplified"
            setOnClickListener { LanguageControls.toggleScript() }
        }
        val size = (ctx.resources.displayMetrics.density * 40).toInt()
        listOf(language, lookup, script).forEachIndexed { i, b ->
            bar.root.addView(b, i, FlexboxLayout.LayoutParams(size, size))
        }

        fun refresh() {
            val lang = LanguageControls.language
            language.label = lang.label
            lookup.label = if (lang == LanguageControls.Language.Mandarin) "EN→普" else "EN→粵"
            lookup.active = LanguageControls.englishLookup
            lookup.visibility = if (lang == LanguageControls.Language.English) View.GONE else View.VISIBLE
            val s = LanguageControls.scriptLabel
            script.label = s ?: ""
            script.visibility = if (s == null) View.GONE else View.VISIBLE
        }
        refresh()
        val listener = { refresh() }
        language.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                BumBoard.addListener(listener)
                refresh()
            }

            override fun onViewDetachedFromWindow(v: View) {
                BumBoard.removeListener(listener)
            }
        })
    }

    /** A round toolbar button showing a short text label, Material 3 style. */
    @SuppressLint("ViewConstructor")
    class LabelButton(ctx: Context, private val theme: Theme) : FrameLayout(ctx) {
        private val text = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(theme.altKeyTextColor)
            gravity = Gravity.CENTER
            isSingleLine = true
        }
        private val pill = GradientDrawable().apply {
            cornerRadius = ctx.resources.displayMetrics.density * 20
        }

        var label: String
            get() = text.text.toString()
            set(value) {
                text.text = value
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (value.length > 2) 10.5f else 15f)
            }

        var active: Boolean = false
            set(value) {
                field = value
                pill.setColor(if (value) theme.genericActiveBackgroundColor else 0)
                text.setTextColor(if (value) theme.genericActiveForegroundColor else theme.altKeyTextColor)
            }

        init {
            isClickable = true
            isFocusable = true
            val inset = (ctx.resources.displayMetrics.density * 2).toInt()
            addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(inset, inset, inset, inset)
            })
            text.background = pill
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.keyPressHighlightColor, 0xFF)),
                null, GradientDrawable().apply {
                    cornerRadius = ctx.resources.displayMetrics.density * 20
                    setColor(0xFFFFFFFF.toInt())
                }
            )
            active = false
        }
    }
}
