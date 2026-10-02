// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.readings

import app.lychee.chinese.ChineseInput.Language

/** The reading and meaning lines shown under a candidate. */
class CandidateLines(
    /** Cantonese reading as shown (Yale, or Jyutping when the user prefers it) */
    val cantonese: String?,
    /** Mandarin reading with tone marks */
    val mandarin: String?,
    /** a short English meaning */
    val english: String?,
    val info: WordInfo?,
) {
    companion object {
        private val skipSense = Regex(
            "^(CL: |Taiwan pr\\.|also pr\\.|also written|(old |archaic )?variant of|see |see also|used in |" +
                "abbr\\. for|surname |\\(Tw\\)|\\(old\\)|erhua variant)", RegexOption.IGNORE_CASE
        )
        private val leadingQualifiers = Regex("^(\\((bound form|literary|coll\\.|dialect|Cantonese|of [^)]*)\\)\\s*)+")

        /**
         * [comment] is Rime's comment for the candidate: the matched Jyutping for Cantonese, the
         * toneless pinyin for Mandarin. It picks the right reading of words with several.
         */
        fun of(text: String, comment: String, language: Language, jyutpingDisplay: Boolean): CandidateLines {
            val info = Readings.lookup(text) ?: return CandidateLines(null, null, null, null)
            val jyutping = if (language == Language.CANTONESE && Romanization.isJyutping(comment)) comment
                else info.jyutping.firstOrNull()
            val pinyin = if (language == Language.MANDARIN && comment.isNotBlank()) matchPinyin(text, info, comment)
                else info.pinyin.firstOrNull()
            return CandidateLines(
                cantonese = jyutping?.let { if (jyutpingDisplay) it else Romanization.jyutpingToYale(it) },
                mandarin = pinyin?.let { Romanization.pinyinToMarks(it) },
                english = shortMeaning(info, language),
                info = info,
            )
        }

        /** The reading whose toneless form is Rime's match, else built character by character. */
        private fun matchPinyin(text: String, info: WordInfo, toneless: String): String? {
            val want = toneless.trim().lowercase().replace("v", "ü")
            info.pinyin.firstOrNull { Romanization.tonelessPinyin(it) == want }?.let { return it }
            val syllables = want.split(Regex("\\s+"))
            val chars = text.codePoints().toArray().map { String(Character.toChars(it)) }
            if (chars.size == syllables.size) {
                val parts = chars.mapIndexed { i, ch ->
                    val readings = Readings.lookup(ch)?.pinyin.orEmpty()
                    readings.firstOrNull { Romanization.tonelessPinyin(it) == syllables[i] } ?: readings.firstOrNull()
                }
                if (parts.all { it != null }) return parts.joinToString(" ")
            }
            return info.pinyin.firstOrNull()
        }

        /** One short sense, from the source that suits the language best. */
        fun shortMeaning(info: WordInfo, language: Language): String? {
            if (info.parts.isNotEmpty() && info.typeduck.isEmpty() && info.cedict.isEmpty() && info.canto.isEmpty()) {
                val pieces = info.parts.map { shortMeaning(it, language) }
                return if (pieces.all { it == null }) null
                    else info.parts.zip(pieces).joinToString(" · ") { (part, meaning) -> meaning ?: part.word }
            }
            val senses = when (language) {
                Language.CANTONESE -> info.typeduck.map { it.second } + info.canto + info.cedict
                Language.MANDARIN -> info.cedict + info.typeduck.map { it.second } + info.canto
            }
            val sense = senses.firstOrNull { !skipSense.containsMatchIn(it) } ?: senses.firstOrNull() ?: return null
            return sense.replace(leadingQualifiers, "").ifBlank { sense }
        }
    }
}
