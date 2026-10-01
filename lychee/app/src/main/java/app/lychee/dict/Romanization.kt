/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package app.lychee.dict

import java.text.Normalizer

/** Display forms of the numbered readings stored in the dictionary. */
object Romanization {

    private const val MACRON = '̄'
    private const val ACUTE = '́'
    private const val GRAVE = '̀'
    private const val CARON = '̌'

    private val JYUTPING = Regex("^(gw|kw|ng|[bpmfdtnlgkhwzcsj])?([a-z]*?)([1-6])$")
    private val VOWELS = "aeiou"

    /** "sik6 faan6" -> "sihk faahn"; several readings stay "/"-separated. */
    fun jyutpingToYale(readings: String): String =
        readings.split('/').joinToString(" / ") { reading ->
            reading.trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { yaleSyllable(it) }
        }

    fun yaleSyllable(syllable: String): String {
        val m = JYUTPING.matchEntire(syllable.lowercase()) ?: return syllable
        var initial = m.groupValues[1]
        var final = m.groupValues[2]
        val tone = m.groupValues[3][0] - '0'
        // Syllabic nasals: m4 -> m̀h, ng5 -> ńgh
        if (final.isEmpty() && (initial == "m" || initial == "ng")) {
            return nfc(initial[0] + toneMark(tone) + initial.substring(1) + if (tone >= 4) "h" else "")
        }
        initial = when (initial) {
            "j" -> "y"
            "z" -> "j"
            "c" -> "ch"
            else -> initial
        }
        final = when {
            final == "aa" -> "a"
            final.startsWith("oe") -> "eu" + final.substring(2)   // oe, oeng, oek
            final.startsWith("eo") -> "eu" + final.substring(2)   // eoi, eon, eot
            else -> final
        }
        if (initial == "y" && final.startsWith("yu")) final = final.substring(1)
        // Tone mark on the first vowel; tones 4-6 add "h" after the vowels.
        val first = final.indexOfFirst { it in VOWELS }
        if (first < 0) return initial + final
        var end = first
        while (end < final.length && final[end] in VOWELS) end++
        val sb = StringBuilder(initial)
        sb.append(final, 0, first + 1).append(toneMark(tone)).append(final, first + 1, end)
        if (tone >= 4) sb.append('h')
        sb.append(final, end, final.length)
        return nfc(sb.toString())
    }

    private fun toneMark(tone: Int): String = when (tone) {
        1 -> MACRON.toString()
        2, 5 -> ACUTE.toString()
        4 -> GRAVE.toString()
        else -> ""
    }

    /** "chi1 fan4" -> "chī fàn"; several readings stay "/"-separated. */
    fun pinyinToMarks(readings: String): String =
        readings.split('/').joinToString(" / ") { reading ->
            reading.trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { pinyinSyllable(it) }
        }

    fun pinyinSyllable(syllable: String): String {
        val last = syllable.lastOrNull() ?: return syllable
        if (last !in '1'..'5') return syllable
        val tone = last - '0'
        val body = syllable.dropLast(1).replace('v', 'ü')
        if (tone == 5) return body
        val mark = when (tone) {
            1 -> MACRON
            2 -> ACUTE
            3 -> CARON
            else -> GRAVE
        }
        val lower = body.lowercase()
        val at = when {
            'a' in lower -> lower.indexOf('a')
            'e' in lower -> lower.indexOf('e')
            "ou" in lower -> lower.indexOf('o')
            else -> lower.indexOfLast { it in "aeiouü" }
        }
        if (at < 0) return body
        return nfc(body.substring(0, at + 1) + mark + body.substring(at + 1))
    }

    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
}
