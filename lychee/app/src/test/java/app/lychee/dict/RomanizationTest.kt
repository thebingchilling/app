/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package app.lychee.dict

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanizationTest {

    @Test
    fun yaleSyllables() {
        mapOf(
            "sik6" to "sihk", "faan6" to "faahn", "nei5" to "néih", "hou2" to "hóu",
            "hoeng1" to "hēung", "gong2" to "góng", "m4" to "m̀h", "ng5" to "ńgh",
            "jyu4" to "yùh", "syu1" to "syū", "zung1" to "jūng", "cin4" to "chìhn",
            "seoi2" to "séui", "jat1" to "yāt", "gwong2" to "gwóng", "aa3" to "a",
            "maa1" to "mā", "ceot1" to "chēut", "jyun4" to "yùhn", "deoi3" to "deui",
            "keoi5" to "kéuih", "lei6" to "leih", "ngo5" to "ngóh", "aai1" to "āai",
            "zoek3" to "jeuk", "joeng4" to "yèuhng", "hai6" to "haih", "m6" to "mh",
        ).forEach { (jyutping, yale) -> assertEquals(jyutping, yale, Romanization.yaleSyllable(jyutping)) }
    }

    @Test
    fun yaleReadings() {
        assertEquals("sihk faahn", Romanization.jyutpingToYale("sik6 faan6"))
        assertEquals("hàhng / hòhng", Romanization.jyutpingToYale("hang4/hong4"))
    }

    @Test
    fun pinyinSyllables() {
        mapOf(
            "chi1" to "chī", "fan4" to "fàn", "ni3" to "nǐ", "hao3" to "hǎo", "xiu1" to "xiū",
            "gui4" to "guì", "lü4" to "lǜ", "nv3" to "nǚ", "de5" to "de", "zhuang4" to "zhuàng",
            "lou2" to "lóu", "xue2" to "xué", "er2" to "ér", "Zhong1" to "Zhōng",
        ).forEach { (numbered, marked) -> assertEquals(numbered, marked, Romanization.pinyinSyllable(numbered)) }
        assertEquals("chī fàn / chī fan", Romanization.pinyinToMarks("chi1 fan4/chi1 fan5"))
    }
}
