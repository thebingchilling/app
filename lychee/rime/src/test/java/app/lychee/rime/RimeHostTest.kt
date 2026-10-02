// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Drives the real Rime through the JNI bridge on the build machine. Needs the host build from
 * rime/test/host_jni.sh, passed as the environment variable LYCHEE_HOST_RIME (skipped without).
 */
class RimeHostTest {
    companion object {
        private val hostDir = System.getenv("LYCHEE_HOST_RIME")?.let(::File)

        @BeforeClass
        @JvmStatic
        fun start() {
            assumeTrue("LYCHEE_HOST_RIME not set", hostDir != null)
            val user = Files.createTempDirectory("lychee-rime-user").toFile()
            Rime.startup(File(hostDir, "data/rime").path, user.path, true)
        }
    }

    private fun session(schema: String) = Rime.Session().also { assertTrue(it.selectSchema(schema)) }

    private fun type(s: Rime.Session, keys: String) = keys.forEach { s.processKey(it.code) }

    private fun pick(s: Rime.Session, word: String): String? {
        val index = s.candidates(0, 50).indexOfFirst { it.text == word }
        assertTrue("$word not among ${s.candidates(0, 10).map { it.text }}", index >= 0)
        assertTrue(s.selectCandidate(index))
        return s.getCommit()
    }

    @Test
    fun cantoneseYale() {
        val s = session("lychee_cantonese")
        type(s, "sihkfaahn")
        assertNotNull(s.composition)
        val candidate = s.candidates(0, 10).first { it.text == "食飯" }
        assertEquals("sik6 faan6", candidate.comment)
        assertEquals("食飯", pick(s, "食飯"))
        assertNull(s.composition)
        s.destroy()
    }

    @Test
    fun cantoneseOutsideBmp() {
        val s = session("lychee_cantonese")
        type(s, "lip")
        // 𨋢 (lift) is outside the BMP: it must come through as a surrogate pair
        assertTrue(s.candidates(0, 50).any { it.text == "𨋢" })
        s.destroy()
    }

    @Test
    fun cantoneseSimplified() {
        val s = session("lychee_cantonese")
        s.setOption("simplification", true)
        type(s, "sikfaan")
        assertEquals("食饭", pick(s, "食饭"))
        s.destroy()
    }

    @Test
    fun mandarinAndTraditional() {
        val s = session("lychee_mandarin")
        type(s, "nihao")
        assertEquals("ni hao", s.candidates(0, 1).first().comment)
        assertEquals("你好", pick(s, "你好"))
        s.setOption("traditionalization", true)
        type(s, "zhongguo")
        assertEquals("中國", pick(s, "中國"))
        s.destroy()
    }

    @Test
    fun backspaceAndClear() {
        val s = session("lychee_mandarin")
        type(s, "ni")
        s.processKey(Rime.KEY_BACKSPACE)
        assertEquals("n", s.input)
        s.processKey(Rime.KEY_BACKSPACE)
        assertNull(s.composition)
        type(s, "hao")
        s.clearComposition()
        assertNull(s.composition)
        s.destroy()
    }

    @Test
    fun partialSelection() {
        val s = session("lychee_mandarin")
        type(s, "nihaoma")
        // picking a word that covers only the start leaves the rest to compose
        val index = s.candidates(0, 50).indexOfFirst { it.text == "你好" }
        assertTrue(index >= 0)
        s.selectCandidate(index)
        assertNull(s.getCommit())
        assertNotNull(s.composition)
        assertTrue(s.candidates(0, 10).any { it.text == "吗" })
        s.destroy()
    }
}
