// SPDX-License-Identifier: GPL-3.0-only
package app.lychee

import android.content.Context
import app.lychee.chinese.ChineseInput.Language
import app.lychee.chinese.RimeData
import app.lychee.rime.Rime
import java.io.File

/** Traditional/Simplified conversion with OpenCC (Rime's copy), for text that does not come from Rime. */
object Script {
    /** Converts [text] to the script the user wants for [language]. */
    fun forLanguage(context: Context, text: String, language: Language): String {
        val traditional = LycheePrefs.isTraditional(context, language)
        val config = when (language) {
            Language.CANTONESE -> if (traditional) "s2hk.json" else "hk2s.json"
            Language.MANDARIN -> if (traditional) "s2t.json" else "t2s.json"
        }
        return convert(context, text, config)
    }

    fun convert(context: Context, text: String, config: String): String {
        if (text.none { it.code >= 0x2E80 }) return text // nothing Chinese in it
        val file = File(RimeData.sharedDir(context), "opencc/$config")
        if (!file.exists()) return text
        return Rime.openccConvert(file.path, text) ?: text
    }
}
