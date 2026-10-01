/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package app.lychee.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/** What the candidate bar shows under a word. Readings are display-ready. */
data class Gloss(val yale: String, val pinyin: String, val english: String) {
    val isEmpty get() = yale.isEmpty() && pinyin.isEmpty() && english.isEmpty()
}

/**
 * Read-only lookup in the dictionary built by `dictionary/build_dict.py`
 * (bundled as the asset [ASSET]). The asset is copied out once per app
 * version because SQLite can't open files inside the APK.
 */
object LycheeDictionary {

    private const val ASSET = "lychee/dict.db"
    private const val MAX_WORD = 8
    private const val MAX_READINGS = 2

    @Volatile
    private var db: SQLiteDatabase? = null
    private val cache = LruCache<String, Any>(4096)
    private val NONE = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (db != null) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching { open(appContext) }
                .onSuccess { db = it }
                .onFailure { Timber.e(it, "Lychee dictionary unavailable") }
        }
    }

    private fun open(context: Context): SQLiteDatabase {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val dir = File(context.noBackupFilesDir, "lychee")
        val file = File(dir, "dict-$version.db")
        if (!file.exists()) {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "dict.tmp")
            context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        }
        return SQLiteDatabase.openDatabase(
            file.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    /**
     * @param jyutpingHint the reading the Cantonese engine matched (if any),
     *   e.g. "hang4" for 行 typed as "hong" vs "hang"; shown instead of the
     *   dictionary's most common reading.
     */
    fun lookup(word: String, jyutpingHint: String? = null): Gloss? {
        if (word.none { isHan(it) }) return null
        val key = if (jyutpingHint.isNullOrEmpty()) word else "$word\u0000$jyutpingHint"
        cache.get(key)?.let { return if (it === NONE) null else it as Gloss }
        val database = db ?: return null
        val gloss = runCatching { compute(database, word, jyutpingHint) }
            .onFailure { Timber.w(it, "lookup failed for $word") }
            .getOrNull()
        cache.put(key, gloss ?: NONE)
        return gloss
    }

    private class Row(val jyutping: String, val pinyin: String, val english: String)

    private fun row(db: SQLiteDatabase, word: String): Row? {
        val sql = "SELECT jyutping, pinyin, english FROM entry WHERE word = ?1 " +
                "UNION ALL SELECT e.jyutping, e.pinyin, e.english FROM alias a " +
                "JOIN entry e ON e.word = a.target WHERE a.word = ?1 LIMIT 1"
        db.rawQuery(sql, arrayOf(word)).use { c ->
            return if (c.moveToFirst()) Row(c.getString(0), c.getString(1), c.getString(2)) else null
        }
    }

    private fun compute(db: SQLiteDatabase, word: String, hint: String?): Gloss? {
        row(db, word)?.let { r ->
            val jyutping = hint?.takeIf { looksLikeJyutping(it) } ?: firstReadings(r.jyutping)
            return Gloss(
                Romanization.jyutpingToYale(jyutping),
                Romanization.pinyinToMarks(firstReadings(r.pinyin)),
                r.english
            ).takeUnless { it.isEmpty }
        }
        // Phrases the dictionary doesn't list (Rime sentences, rare words):
        // longest known pieces, left to right.
        val jyutping = mutableListOf<String>()
        val pinyin = mutableListOf<String>()
        val english = mutableListOf<String>()
        var i = 0
        var found = 0
        while (i < word.length) {
            var len = minOf(MAX_WORD, word.length - i)
            var piece: Row? = null
            while (len > 0) {
                piece = row(db, word.substring(i, i + len))
                if (piece != null) break
                len--
            }
            if (piece == null) {
                i += Character.charCount(word.codePointAt(i))
                continue
            }
            found++
            piece.jyutping.substringBefore('/').takeIf { it.isNotEmpty() }?.let { jyutping += it }
            piece.pinyin.substringBefore('/').takeIf { it.isNotEmpty() }?.let { pinyin += it }
            piece.english.substringBefore("; ").takeIf { it.isNotEmpty() }?.let { english += it }
            i += len
        }
        if (found == 0) return null
        val jp = hint?.takeIf { looksLikeJyutping(it) } ?: jyutping.joinToString(" ")
        return Gloss(
            Romanization.jyutpingToYale(jp),
            Romanization.pinyinToMarks(pinyin.joinToString(" ")),
            english.joinToString(" + ")
        ).takeUnless { it.isEmpty }
    }

    private fun firstReadings(readings: String) =
        readings.split('/').take(MAX_READINGS).joinToString("/")

    private val JYUTPING = Regex("^([a-z]+[1-6])( [a-z]+[1-6])*$")

    fun looksLikeJyutping(s: String) = JYUTPING.matches(s)

    fun isHan(c: Char) = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN ||
            Character.isSurrogate(c)
}
