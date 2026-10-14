package app.bumboard.toolbar

import androidx.annotation.DrawableRes
import app.bumboard.BumBoard
import org.fcitx.fcitx5.android.R

/**
 * Everything that can sit on the toolbar or in the tools panel (Gboard-style).
 * A tool either shows an icon or, for language/script, a short text label.
 */
enum class Tool(val id: String, val title: String, @DrawableRes val icon: Int) {
    Language("language", "Language", R.drawable.ms_language),
    EnglishLookup("en2zh", "EN→中", R.drawable.ms_translate),
    Script("script", "繁 / 简", R.drawable.ms_text_fields),
    Emoji("emoji", "Emoji", R.drawable.ms_emoji),
    Clipboard("clipboard", "Clipboard", R.drawable.ms_clipboard),
    Editing("editing", "Select & move", R.drawable.ms_text_select),
    Undo("undo", "Undo", R.drawable.ms_undo),
    Redo("redo", "Redo", R.drawable.ms_redo),
    Resize("resize", "Resize", R.drawable.ms_resize),
    Theme("theme", "Theme", R.drawable.ms_palette),
    Settings("settings", "Settings", R.drawable.ms_settings),
    More("more", "Input options", R.drawable.ms_more);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Which tools are pinned to the toolbar, and the order of all tools. Stored in BumBoard's prefs. */
object ToolPrefs {
    private const val ORDER = "tool_order"
    private const val PINNED = "tool_pinned"
    private val defaultPinned = listOf(Tool.Language, Tool.EnglishLookup, Tool.Script, Tool.Emoji, Tool.Clipboard, Tool.Editing)

    var order: List<Tool>
        get() {
            val saved = BumBoard.prefs.getString(ORDER, null)?.split(',')?.mapNotNull(Tool::of).orEmpty()
            return saved + Tool.entries.filter { it !in saved }
        }
        set(value) = BumBoard.prefs.edit().putString(ORDER, value.joinToString(",") { it.id }).apply()

    var pinned: Set<Tool>
        get() = BumBoard.prefs.getString(PINNED, null)?.split(',')?.mapNotNull(Tool::of)?.toSet()
            ?: defaultPinned.toSet()
        set(value) = BumBoard.prefs.edit().putString(PINNED, value.joinToString(",") { it.id }).apply()

    /** Pinned tools in panel order. */
    val toolbar: List<Tool> get() = pinned.let { p -> order.filter { it in p } }

    fun togglePin(tool: Tool) {
        val p = pinned
        pinned = if (tool in p) p - tool else p + tool
    }
}
