package app.bumboard.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import app.bumboard.settings.SettingsActivity
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil

/**
 * Gboard-style toolbar: the pinned [Tool]s, then a button that opens the tools panel
 * ([ToolsWindow]). Patch 0002 calls [bind] where upstream wires its toolbar buttons; upstream's
 * own buttons are kept (their actions are reused) but taken off the bar.
 */
object BumToolbar {

    private class Host(val bar: ButtonsBarUi, val theme: Theme, val windowManager: InputWindowManager) {
        val ctx: Context get() = bar.ctx
        val views = mutableMapOf<Tool, ToolChip>()
        var refresh: () -> Unit = {}
    }

    private var host: Host? = null

    fun bind(bar: ButtonsBarUi, theme: Theme, windowManager: InputWindowManager) {
        BumBoard.attach()
        val h = Host(bar, theme, windowManager)
        host = h
        bar.root.justifyContent = JustifyContent.SPACE_EVENLY
        rebuild(h)
        val listener = { h.refresh() }
        bar.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                BumBoard.addListener(listener)
                h.refresh()
            }

            override fun onViewDetachedFromWindow(v: View) {
                BumBoard.removeListener(listener)
            }
        })
        // show as many pinned tools as fit
        bar.root.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) v.post { fit(h) }
        }
    }

    /** Re-creates the toolbar after the pinned tools changed. */
    fun rebuild() {
        host?.let(::rebuild)
    }

    private fun rebuild(h: Host) {
        val root = h.bar.root
        root.removeAllViews()
        h.views.clear()
        val size = dp(h.ctx, 40)
        for (tool in ToolPrefs.toolbar) {
            val chip = ToolChip(h.ctx, h.theme, tool).apply {
                setOnClickListener { run(tool) }
                contentDescription = tool.title
            }
            h.views[tool] = chip
            root.addView(chip, FlexboxLayout.LayoutParams(size, size))
        }
        val tools = ToolChip(h.ctx, h.theme, null).apply {
            setIcon(R.drawable.ms_apps)
            contentDescription = "Tools"
            setOnClickListener { h.windowManager.attachWindow(ToolsWindow()) }
        }
        root.addView(tools, FlexboxLayout.LayoutParams(size, size))
        h.refresh = { refresh(h) }
        refresh(h)
        root.post { fit(h) }
    }

    private fun fit(h: Host) {
        val root = h.bar.root
        val width = root.width
        if (width <= 0) return
        val capacity = (width / dp(h.ctx, 44)) - 1 // keep room for the tools button
        var shown = 0
        for (tool in ToolPrefs.toolbar) {
            val v = h.views[tool] ?: continue
            val fits = shown < capacity && v.wanted
            v.visibility = if (fits) View.VISIBLE else View.GONE
            if (fits) shown++
        }
    }

    private fun refresh(h: Host) {
        val lang = LanguageControls.language
        h.views[Tool.Language]?.setLabel(lang.label)
        h.views[Tool.EnglishLookup]?.apply {
            active = LanguageControls.englishLookup
            wanted = lang != LanguageControls.Language.English
        }
        h.views[Tool.Script]?.apply {
            val s = LanguageControls.scriptLabel
            setLabel(s ?: "繁")
            wanted = s != null
        }
        fit(h)
    }

    /** Runs a tool. Returns true if the tool opened its own window (so callers keep it). */
    fun run(tool: Tool): Boolean {
        val h = host ?: return false
        val bar = h.bar
        when (tool) {
            Tool.Language -> LanguageControls.cycleLanguage()
            Tool.EnglishLookup -> LanguageControls.toggleEnglishLookup()
            Tool.Script -> LanguageControls.toggleScript()
            Tool.Emoji -> { h.windowManager.attachWindow(PickerWindow.Key.Emoji); return true }
            Tool.Clipboard -> { bar.clipboardButton.performClick(); return true }
            Tool.Editing -> { bar.cursorMoveButton.performClick(); return true }
            Tool.Undo -> bar.undoButton.performClick()
            Tool.Redo -> bar.redoButton.performClick()
            Tool.Resize -> { h.windowManager.attachWindow(ResizeWindow()); return true }
            Tool.Theme -> AppUtil.launchMainToThemeList(h.ctx)
            Tool.Settings -> h.ctx.startActivity(
                Intent(h.ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Tool.More -> { bar.moreButton.performClick(); return true }
        }
        return false
    }

    /** Returns to the letters (used by the tools panel after running a tool). */
    fun backToKeyboard() {
        host?.windowManager?.attachWindow(KeyboardWindow)
    }

    private fun dp(ctx: Context, v: Int) = (ctx.resources.displayMetrics.density * v).toInt()

    /** A round toolbar button with an icon or a short label; filled when [active]. Material 3 style. */
    @SuppressLint("ViewConstructor")
    class ToolChip(ctx: Context, private val theme: Theme, tool: Tool?) : FrameLayout(ctx) {
        /** false when the tool does not apply right now (e.g. 繁/简 in English) */
        var wanted = true

        private val text = TextView(ctx).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(theme.altKeyTextColor)
            gravity = Gravity.CENTER
            isSingleLine = true
            visibility = View.GONE
        }
        private val image = ImageView(ctx).apply {
            imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (ctx.resources.displayMetrics.density * 9).toInt()
            setPadding(p, p, p, p)
        }
        private val pill = GradientDrawable().apply {
            cornerRadius = ctx.resources.displayMetrics.density * 20
        }

        var active: Boolean = false
            set(value) {
                field = value
                pill.setColor(if (value) theme.genericActiveBackgroundColor else 0)
                val fg = if (value) theme.genericActiveForegroundColor else theme.altKeyTextColor
                text.setTextColor(fg)
                image.imageTintList = ColorStateList.valueOf(fg)
            }

        init {
            isClickable = true
            isFocusable = true
            val inset = (ctx.resources.displayMetrics.density * 2).toInt()
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(inset, inset, inset, inset)
            }
            addView(FrameLayout(ctx).apply {
                background = pill
                addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }, lp)
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.keyPressHighlightColor, 0xFF)),
                null,
                GradientDrawable().apply {
                    cornerRadius = ctx.resources.displayMetrics.density * 20
                    setColor(0xFFFFFFFF.toInt())
                }
            )
            tool?.let { setIcon(it.icon) }
            active = false
        }

        fun setIcon(res: Int) {
            image.setImageResource(res)
            image.visibility = View.VISIBLE
            text.visibility = View.GONE
        }

        fun setLabel(label: String) {
            text.text = label
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 2) 11f else 16f)
            text.visibility = View.VISIBLE
            image.visibility = View.GONE
        }
    }
}
