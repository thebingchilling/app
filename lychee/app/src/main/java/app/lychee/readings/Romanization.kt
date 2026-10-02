// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.readings

import java.text.Normalizer

/** Jyutping to Yale, and numbered pinyin to tone marks. */
object Romanization {
    private val jyutpingSyllable = Regex("^(ng|gw|kw|[bpmfdtnlgkhwzcsj])?([aeiou]+|yu)?(ng|[iuptkmn])?([1-6])$")
    private val vowels = "aeiouü"

    /** "sik6 faan6" -> "sihk faahn"; anything that is not Jyutping comes back unchanged. */
    fun jyutpingToYale(jyutping: String): String =
        jyutping.trim().split(Regex("\\s+")).joinToString(" ") { yaleSyllable(it) ?: it }

    private fun yaleSyllable(syllable: String): String? {
        val m = jyutpingSyllable.matchEntire(syllable.lowercase()) ?: return null
        var initial = m.groupValues[1]
        var nucleus = m.groupValues[2]
        var coda = m.groupValues[3]
        val tone = m.groupValues[4].toInt()

        // syllabic m and ng ("m4" -> "m̀h", "ng5" -> "ńgh")
        if (nucleus.isEmpty()) {
            val consonant = when {
                initial.isEmpty() -> coda
                coda.isEmpty() -> initial
                else -> return null
            }
            if (consonant != "m" && consonant != "ng") return null
            val marked = mark(consonant[0], tone) + consonant.substring(1)
            return marked + if (tone >= 4) "h" else ""
        }

        initial = when (initial) {
            "j" -> "y"
            "z" -> "j"
            "c" -> "ch"
            else -> initial
        }
        nucleus = when (nucleus) {
            "oe", "eo" -> "eu"
            "eoi" -> "eui"
            "aa" -> if (coda.isEmpty()) "a" else "aa"
            else -> nucleus
        }
        // Jyutping "jyu" is Yale "yu": the y is already there
        if (initial == "y" && nucleus.startsWith("yu")) initial = ""

        // tone mark on the first vowel of the nucleus ("y" of "yu" is not a vowel)
        val markAt = nucleus.indexOfFirst { it in vowels }
        val markedNucleus = if (markAt < 0) nucleus
            else nucleus.substring(0, markAt) + mark(nucleus[markAt], tone) + nucleus.substring(markAt + 1)
        // low tones (4, 5, 6) add an h after the vowels
        val h = if (tone >= 4) "h" else ""
        // Yale writes final i/u of the nucleus as part of the vowels, before the h
        if (coda == "i" || coda == "u") return initial + markedNucleus + coda + h
        return initial + markedNucleus + h + coda
    }

    private fun mark(c: Char, tone: Int): String {
        val combining = when (tone) {
            1 -> "̄" // high level: macron
            2, 5 -> "́" // rising: acute
            4 -> "̀" // low falling: grave
            else -> "" // 3 and 6: no mark
        }
        return Normalizer.normalize(c + combining, Normalizer.Form.NFC)
    }

    /** "shi2 fan4" -> "shí fàn", "lu:4"/"lv4" -> "lǜ"; tone 5 (neutral) has no mark. */
    fun pinyinToMarks(pinyin: String): String =
        pinyin.trim().split(Regex("\\s+")).joinToString(" ") { pinyinSyllable(it) }

    private fun pinyinSyllable(syllable: String): String {
        val s = syllable.lowercase().replace("u:", "ü").replace("v", "ü")
        val tone = s.lastOrNull()?.digitToIntOrNull() ?: return s
        val body = s.dropLast(1)
        if (tone !in 1..4) return body
        val index = when {
            'a' in body -> body.indexOf('a')
            'e' in body -> body.indexOf('e')
            "ou" in body -> body.indexOf('o')
            else -> body.indexOfLast { it in vowels }
        }
        if (index < 0) { // syllabic m, n, ng ("m2", "ng4")
            val at = body.indexOfFirst { it == 'm' || it == 'n' }
            if (at < 0) return body
            return body.substring(0, at) + markPinyin(body[at], tone) + body.substring(at + 1)
        }
        return body.substring(0, index) + markPinyin(body[index], tone) + body.substring(index + 1)
    }

    private fun markPinyin(c: Char, tone: Int): String {
        val combining = when (tone) {
            1 -> "̄"
            2 -> "́"
            3 -> "̌"
            4 -> "̀"
            else -> ""
        }
        return Normalizer.normalize(c + combining, Normalizer.Form.NFC)
    }

    /** "shi2 fan4" -> "shi fan" (for matching Rime's toneless pinyin). */
    fun tonelessPinyin(pinyin: String) =
        pinyin.lowercase().replace(Regex("[1-5]"), "").replace("u:", "ü").replace("v", "ü").trim()

    fun isJyutping(text: String) =
        text.isNotBlank() && text.trim().split(Regex("\\s+")).all { jyutpingSyllable.matches(it) }
}
