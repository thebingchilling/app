// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.readings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import helium314.keyboard.latin.utils.Log
import java.io.File

/** What Lychee knows about a word: its readings (numbered) and every English sense. */
class WordInfo(
    val word: String,
    /** Jyutping readings, most common first ("sik6 faan6") */
    val jyutping: List<String>,
    /** pinyin readings, most common first ("shi2 fan4") */
    val pinyin: List<String>,
    /** TypeDuck: part of speech to English */
    val typeduck: List<Pair<String, String>>,
    val cedict: List<String>,
    val canto: List<String>,
    /** for words not in the dictionary: the pieces the readings were built from */
    val parts: List<WordInfo> = emptyList(),
) {
    val hasMeaning: Boolean get() = typeduck.isNotEmpty() || cedict.isNotEmpty() || canto.isNotEmpty() || parts.any { it.hasMeaning }
}

/**
 * The readings-and-meanings database (built by readings/build_readings.py, shipped as the asset
 * lychee/readings.db). Words that are not in it are split into the longest known pieces.
 */
object Readings {
    private const val TAG = "Readings"
    private const val MAX_PIECE = 8

    @Volatile
    private var db: SQLiteDatabase? = null
    private val cache = object : LinkedHashMap<String, WordInfo?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordInfo?>?) = size > 2000
    }

    /** Copies the database out of the APK when needed and opens it. Blocking. */
    fun open(context: Context) {
        if (db != null) return
        synchronized(this) {
            if (db != null) return
            try {
                val file = File(context.filesDir, "lychee/readings.db")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val stamp = File(file.parentFile, "readings.stamp")
                if (!file.exists() || !stamp.exists() || stamp.readText() != "${info.lastUpdateTime}") {
                    file.parentFile?.mkdirs()
                    context.assets.open("lychee/readings.db").use { input ->
                        file.outputStream().use { input.copyTo(it, 1 shl 16) }
                    }
                    stamp.writeText("${info.lastUpdateTime}")
                }
                db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            } catch (e: Exception) {
                Log.e(TAG, "could not open the readings database", e)
            }
        }
    }

    val isOpen get() = db != null

    /** The word itself, or else built from its longest known pieces; null when nothing is known. */
    fun lookup(word: String): WordInfo? {
        if (word.isEmpty() || !word.any { isHan(it.code) || Character.isSurrogate(it) }) return null
        synchronized(cache) { if (cache.containsKey(word)) return cache[word] }
        val result = exact(word) ?: pieces(word)
        synchronized(cache) { cache[word] = result }
        return result
    }

    private fun exact(word: String): WordInfo? {
        val database = db ?: return null
        val sql = "SELECT word, jyutping, pinyin, typeduck, cedict, canto FROM entry WHERE word = ?"
        database.rawQuery(sql, arrayOf(word)).use { c ->
            if (c.moveToFirst()) return info(word, c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
        }
        database.rawQuery("SELECT target FROM alias WHERE word = ?", arrayOf(word)).use { c ->
            if (!c.moveToFirst()) return null
            val target = c.getString(0)
            database.rawQuery(sql, arrayOf(target)).use { e ->
                if (e.moveToFirst()) return info(word, e.getString(1), e.getString(2), e.getString(3), e.getString(4), e.getString(5))
            }
        }
        return null
    }

    private fun info(word: String, jp: String, py: String, td: String, ced: String, can: String) = WordInfo(
        word = word,
        jyutping = jp.split('/').filter { it.isNotBlank() },
        pinyin = py.split('/').filter { it.isNotBlank() },
        typeduck = td.lines().filter { it.isNotBlank() }.map { it.substringBefore('\t', "") to it.substringAfter('\t') },
        cedict = ced.lines().filter { it.isNotBlank() },
        canto = can.lines().filter { it.isNotBlank() },
    )

    /** Longest-match split of a word that is not in the database ("今天天气真好" -> 今天 天气 真 好). */
    private fun pieces(word: String): WordInfo? {
        val codePoints = word.codePoints().toArray()
        val parts = ArrayList<WordInfo>()
        var i = 0
        while (i < codePoints.size) {
            var found: WordInfo? = null
            var length = 1
            for (n in minOf(MAX_PIECE, codePoints.size - i) downTo 1) {
                if (n == codePoints.size && i == 0) continue // the whole word was already tried
                val piece = String(codePoints, i, n)
                found = exact(piece)
                if (found != null) { length = n; break }
            }
            if (found == null) {
                val piece = String(codePoints, i, 1)
                found = WordInfo(piece, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            }
            parts.add(found)
            i += length
        }
        if (parts.all { it.jyutping.isEmpty() && it.pinyin.isEmpty() }) return null
        fun joined(get: (WordInfo) -> List<String>): List<String> =
            if (parts.all { get(it).isNotEmpty() }) listOf(parts.joinToString(" ") { get(it).first() }) else emptyList()
        return WordInfo(word, joined { it.jyutping }, joined { it.pinyin }, emptyList(), emptyList(), emptyList(), parts)
    }

    fun isHan(cp: Int) = cp in 0x3400..0x9FFF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3134F || cp == 0x3007
}
