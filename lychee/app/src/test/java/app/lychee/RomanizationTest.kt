// SPDX-License-Identifier: GPL-3.0-only
package app.lychee

import app.lychee.readings.Romanization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizationTest {
    private fun yale(jp: String) = Romanization.jyutpingToYale(jp)

    @Test
    fun yaleBasics() {
        assertEquals("sihk faahn", yale("sik6 faan6"))
        assertEquals("néih hóu", yale("nei5 hou2"))
        assertEquals("hēung góng", yale("hoeng1 gong2"))
        assertEquals("gwóng jāu", yale("gwong2 zau1"))
        assertEquals("chāan", yale("caan1"))
        assertEquals("chē", yale("ce1"))
    }

    @Test
    fun yaleLowTonesAddH() {
        assertEquals("gàih", yale("gai4"))      // h after the vowels, i is one of them
        assertEquals("houh", yale("hou6"))
        assertEquals("jeuhng", yale("zoeng6"))
        assertEquals("yìuh", yale("jiu4"))
        assertEquals("deuih", yale("deoi6"))
        assertEquals("náh", yale("naa5"))       // aa without a final is written a
    }

    @Test
    fun yaleInitialsAndYu() {
        assertEquals("yāt", yale("jat1"))
        assertEquals("yuht", yale("jyut6"))
        assertEquals("yùh", yale("jyu4"))
        assertEquals("syū", yale("syu1"))
        assertEquals("séui", yale("seoi2"))
        assertEquals("kéuih", yale("keoi5"))
    }

    @Test
    fun yaleSyllabicNasals() {
        assertEquals("m̀h", yale("m4"))
        assertEquals("ńgh", yale("ng5"))
    }

    @Test
    fun yaleLeavesOtherTextAlone() {
        assertEquals("abc", yale("abc"))
    }

    @Test
    fun pinyinMarks() {
        assertEquals("shí fàn", Romanization.pinyinToMarks("shi2 fan4"))
        assertEquals("nǐ hǎo", Romanization.pinyinToMarks("ni3 hao3"))
        assertEquals("xiōng", Romanization.pinyinToMarks("xiong1"))
        assertEquals("guì", Romanization.pinyinToMarks("gui4"))
        assertEquals("liú", Romanization.pinyinToMarks("liu2"))
        assertEquals("lǜ", Romanization.pinyinToMarks("lv4"))
        assertEquals("nǚ", Romanization.pinyinToMarks("nü3"))
        assertEquals("dōu", Romanization.pinyinToMarks("dou1"))
        assertEquals("de", Romanization.pinyinToMarks("de5"))
    }

    @Test
    fun toneless() {
        assertEquals("shi fan", Romanization.tonelessPinyin("shi2 fan4"))
        assertEquals("lü", Romanization.tonelessPinyin("lv4"))
        assertTrue(Romanization.isJyutping("sik6 faan6"))
        assertFalse(Romanization.isJyutping("ni hao"))
        assertFalse(Romanization.isJyutping(""))
    }
}
