package app.bumboard.keyboard

import android.content.Context
import android.content.res.Configuration
import app.bumboard.BumBoard
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.keyboard.AlphabetKey
import org.fcitx.fcitx5.android.input.keyboard.CommaKey
import org.fcitx.fcitx5.android.input.keyboard.ImagePickerSwitchKey
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.picker.PickerWindow

/**
 * Gboard-style layout options for the letters keyboard (patch 0005 passes upstream's layout
 * through [text]): an always-on number row, and a separate emoji key next to the comma
 * (otherwise emoji stay on the comma key's long-press menu, as upstream has it).
 */
object Layouts {
    const val NUMBER_ROW = "number_row"
    const val EMOJI_KEY = "emoji_key"

    private val digits = listOf("1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
        "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")")

    fun text(upstream: List<List<KeyDef>>): List<List<KeyDef>> {
        val prefs = BumBoard.prefs
        var rows = upstream
        if (prefs.getBoolean(EMOJI_KEY, false)) {
            rows = rows.mapIndexed { i, row ->
                if (i != rows.lastIndex) row else row.flatMap { key ->
                    if (key is CommaKey) listOf(key, ImagePickerSwitchKey(R.drawable.ms_emoji, PickerWindow.Key.Emoji))
                    else listOf(key)
                }
            }
        }
        if (prefs.getBoolean(NUMBER_ROW, false)) {
            // in Cantonese the digits also type tones (nei5)
            rows = listOf(digits.map { (d, s) -> AlphabetKey(d, s) }) + rows
        }
        return rows
    }
}

/** One-handed mode: the keyboard shrinks to one side (patch 0005 hooks InputView). */
object OneHanded {
    const val KEY = "one_handed"

    enum class Side { Off, Left, Right }

    var onChange: (() -> Unit)? = null

    var side: Side
        get() = runCatching { Side.valueOf(BumBoard.prefs.getString(KEY, null) ?: "Off") }.getOrDefault(Side.Off)
        set(value) {
            BumBoard.prefs.edit().putString(KEY, value.name).apply()
            onChange?.invoke()
        }

    /** Off → Right → Left → Off */
    fun cycle() {
        side = when (side) {
            Side.Off -> Side.Right
            Side.Right -> Side.Left
            Side.Left -> Side.Off
        }
    }

    /** Left and right padding in px, given upstream's symmetric side padding. */
    fun padding(ctx: Context, sidePadding: Int): Pair<Int, Int> {
        val landscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) return sidePadding to sidePadding
        val shift = (ctx.resources.displayMetrics.widthPixels * 0.22f).toInt()
        return when (side) {
            Side.Off -> sidePadding to sidePadding
            Side.Left -> sidePadding to sidePadding + shift
            Side.Right -> sidePadding + shift to sidePadding
        }
    }
}
