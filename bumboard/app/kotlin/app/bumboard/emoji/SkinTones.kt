package app.bumboard.emoji

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build
import app.bumboard.BumBoard
import org.fcitx.fcitx5.android.input.picker.PickerPageUi
import org.fcitx.fcitx5.android.input.popup.EmojiModifier

/**
 * Gboard-style skin tones for emoji with two people (🧑‍🤝‍🧑, 💑, 👩‍❤️‍👨, 🤝…): every combination
 * of the two people's tones, row = first person, column = second person (a 5×5 popup).
 * Hooked into upstream's EmojiModifier.produceSkinTones by patch 0004.
 */
object SkinTones {

    private val tones = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)
    private const val ZWJ = 0x200D

    /** Single-code-point couples whose mixed-tone forms are ZWJ sequences. */
    private val expanded = mapOf(
        0x1F46B to intArrayOf(0x1F469, ZWJ, 0x1F91D, ZWJ, 0x1F468), // 👫
        0x1F46C to intArrayOf(0x1F468, ZWJ, 0x1F91D, ZWJ, 0x1F468), // 👬
        0x1F46D to intArrayOf(0x1F469, ZWJ, 0x1F91D, ZWJ, 0x1F469), // 👭
        0x1F48F to intArrayOf(0x1F9D1, ZWJ, 0x2764, 0xFE0F, ZWJ, 0x1F48B, ZWJ, 0x1F9D1), // 💏
        0x1F491 to intArrayOf(0x1F9D1, ZWJ, 0x2764, 0xFE0F, ZWJ, 0x1F9D1), // 💑
        0x1F91D to intArrayOf(0x1FAF1, ZWJ, 0x1FAF2), // 🤝
    )

    /** The 25 tone pairs, or null if [emoji] does not show two people / hands. */
    fun pairs(emoji: String): Array<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        var cps = emoji.codePoints().toArray()
        if (cps.size == 1 || (cps.size == 2 && cps[1] == 0xFE0F)) cps = expanded[cps[0]] ?: return null
        val modifiable = cps.indices.filter { i ->
            UCharacter.hasBinaryProperty(cps[i], UProperty.EMOJI_MODIFIER_BASE) &&
                // the handshake between two people is not toned
                !(cps[i] == 0x1F91D && cps.size > 3)
        }
        if (modifiable.size != 2) return null
        val out = ArrayList<String>(25)
        for (a in tones) for (b in tones) {
            val s = buildString {
                cps.forEachIndexed { i, cp ->
                    appendCodePoint(cp)
                    if (i == modifiable[0]) appendCodePoint(a)
                    if (i == modifiable[1]) appendCodePoint(b)
                }
            }
            if (EmojiModifier.isValidEmoji(s)) out += s
        }
        return if (out.size >= 5) out.toTypedArray() else null
    }
}

/** Emoji size in the emoji picker (Settings → Layout & size). */
object EmojiSize {
    const val KEY = "emoji_size"

    fun density(): PickerPageUi.Density = when (BumBoard.prefs.getString(KEY, "medium")) {
        "small" -> PickerPageUi.Density.EmojiSmall
        "large" -> PickerPageUi.Density.EmojiLarge
        else -> PickerPageUi.Density.Medium
    }
}
