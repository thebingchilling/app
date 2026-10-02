// SPDX-License-Identifier: GPL-3.0-only
package app.lychee

import android.content.Context
import app.lychee.chinese.ChineseInput
import helium314.keyboard.latin.utils.prefs

/** Lychee's own settings, stored with HeliBoard's (device-protected) preferences. */
object LycheePrefs {
    const val SHOW_YALE = "lychee_show_yale"
    const val SHOW_PINYIN = "lychee_show_pinyin"
    const val SHOW_ENGLISH = "lychee_show_english"
    const val WRAP_MEANINGS = "lychee_wrap_meanings"
    const val USE_JYUTPING = "lychee_use_jyutping"
    const val FULL_WIDTH_PUNCTUATION = "lychee_full_width_punctuation"
    const val EMOJI_CANDIDATES = "lychee_emoji_candidates"
    const val TRADITIONAL_MANDARIN = "lychee_traditional_mandarin"
    const val TRADITIONAL_CANTONESE = "lychee_traditional_cantonese"
    const val VOICE_FOLLOWS_KEYBOARD = "lychee_voice_follows_keyboard"
    const val WIFI_ONLY_DOWNLOADS = "lychee_wifi_only_downloads"
    const val SETUP_SHOWN = "lychee_setup_shown"

    fun showYale(c: Context) = c.prefs().getBoolean(SHOW_YALE, true)
    fun showPinyin(c: Context) = c.prefs().getBoolean(SHOW_PINYIN, true)
    fun showEnglish(c: Context) = c.prefs().getBoolean(SHOW_ENGLISH, true)
    fun wrapMeanings(c: Context) = c.prefs().getBoolean(WRAP_MEANINGS, false)
    fun useJyutping(c: Context) = c.prefs().getBoolean(USE_JYUTPING, false)
    fun fullWidthPunctuation(c: Context) = c.prefs().getBoolean(FULL_WIDTH_PUNCTUATION, true)
    fun emojiCandidates(c: Context) = c.prefs().getBoolean(EMOJI_CANDIDATES, true)
    fun voiceFollowsKeyboard(c: Context) = c.prefs().getBoolean(VOICE_FOLLOWS_KEYBOARD, false)
    fun wifiOnlyDownloads(c: Context) = c.prefs().getBoolean(WIFI_ONLY_DOWNLOADS, true)

    /** Any reading or meaning line under the candidates (the bar is taller then). */
    fun showsReadings(c: Context) = showYale(c) || showPinyin(c) || showEnglish(c)

    /** Mandarin defaults to Simplified, Cantonese to Traditional. */
    fun isTraditional(c: Context, language: ChineseInput.Language) = when (language) {
        ChineseInput.Language.MANDARIN -> c.prefs().getBoolean(TRADITIONAL_MANDARIN, false)
        ChineseInput.Language.CANTONESE -> c.prefs().getBoolean(TRADITIONAL_CANTONESE, true)
    }

    fun setTraditional(c: Context, language: ChineseInput.Language, traditional: Boolean) {
        val key = if (language == ChineseInput.Language.MANDARIN) TRADITIONAL_MANDARIN else TRADITIONAL_CANTONESE
        c.prefs().edit().putBoolean(key, traditional).apply()
    }
}
