package app.bumboard

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import app.bumboard.core.Annotator
import app.bumboard.core.CantoneseRomanization
import app.bumboard.core.DisplayPrefs
import app.bumboard.core.InputMode
import app.bumboard.core.ReadingsDb
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

/**
 * BumBoard's state, shared by the candidate cells, word card and toolbar.
 *
 * Upstream fcitx5-android calls into BumBoard only through the small hooks in
 * bumboard/patches; everything else lives here.
 */
object BumBoard {

    const val RIME = "rime"
    const val KEYBOARD_US = "keyboard-us"

    /** Rime schema names (schema/name in the schema files under bumboard/data/rime). */
    object Schemas {
        const val CANTONESE = "粵語"
        const val CANTONESE_YALE = "粵語 (Yale)"
        const val MANDARIN = "普通话"
        const val EN_CANTONESE = "英→粵"
        const val EN_MANDARIN = "英→普"
    }

    val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("bumboard", Context.MODE_PRIVATE)
    }

    object Keys {
        const val CANTONESE_DISPLAY = "cantonese_display"
        const val YALE_TONE_MARKS = "yale_tone_marks"
        const val PINYIN_TONE_MARKS = "pinyin_tone_marks"
        const val SHOW_OTHER_LANGUAGE = "show_other_language"
        const val SHOW_MEANING = "show_meaning"
        const val YALE_INPUT = "yale_input"
        const val DEFAULT_IMS_DONE = "default_ims_done_v1"
    }

    fun displayPrefs(): DisplayPrefs {
        val p = prefs
        return DisplayPrefs(
            cantonese = runCatching {
                CantoneseRomanization.valueOf(p.getString(Keys.CANTONESE_DISPLAY, null) ?: "Jyutping")
            }.getOrDefault(CantoneseRomanization.Jyutping),
            yaleToneMarks = p.getBoolean(Keys.YALE_TONE_MARKS, true),
            pinyinToneMarks = p.getBoolean(Keys.PINYIN_TONE_MARKS, true),
            showOtherLanguage = p.getBoolean(Keys.SHOW_OTHER_LANGUAGE, false),
            showMeaning = p.getBoolean(Keys.SHOW_MEANING, true),
        )
    }

    /** Cantonese is typed in Yale instead of Jyutping. */
    val yaleInput: Boolean get() = prefs.getBoolean(Keys.YALE_INPUT, false)

    val cantoneseSchema: String
        get() = if (yaleInput) Schemas.CANTONESE_YALE else Schemas.CANTONESE

    /** Height of the candidate bar: one line for the word plus one per reading/meaning line. */
    val barHeightDp: Int
        get() {
            val p = displayPrefs()
            val cantonese = (if (p.cantonese == CantoneseRomanization.Both) 2 else 1) +
                (if (p.showOtherLanguage) 1 else 0)
            val mandarin = 1 + (if (p.showOtherLanguage) 1 else 0)
            val lines = maxOf(cantonese, mandarin) + (if (p.showMeaning) 1 else 0)
            return 36 + lines * 13
        }

    // ------------------------------------------------------------------ dictionary

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bumboard-io").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var annotator: Annotator? = null
    @Volatile
    private var opening = false

    /** The annotator, or null while readings.db is still being prepared (first launch). */
    fun annotatorOrNull(): Annotator? {
        annotator?.let { return it }
        openDictionary()
        return null
    }

    @Synchronized
    fun openDictionary() {
        if (annotator != null || opening) return
        opening = true
        io.execute {
            try {
                val db = SQLiteDatabase.openDatabase(
                    prepareReadingsDb().path, null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                annotator = Annotator(ReadingsDb(AndroidSql(db)))
                main.post { notifyChanged() }
            } catch (e: Exception) {
                Timber.e(e, "BumBoard: cannot open readings.db")
            } finally {
                opening = false
            }
        }
    }

    /** Copies readings.db out of the APK once per app version (SQLite needs a real file). */
    private fun prepareReadingsDb(): File {
        val ctx = appContext
        val dir = File(ctx.filesDir, "bumboard").apply { mkdirs() }
        val db = File(dir, "readings.db")
        val stamp = File(dir, "readings.version")
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (db.exists() && stamp.exists() && stamp.readText() == version) return db
        val tmp = File(dir, "readings.db.tmp")
        ctx.assets.open("bumboard/readings.db").use { input ->
            tmp.outputStream().use { input.copyTo(it, 1 shl 16) }
        }
        tmp.renameTo(db)
        stamp.writeText(version)
        return db
    }

    // ------------------------------------------------------------------ fcitx state

    @Volatile
    var mode: InputMode = InputMode.Other
        private set

    @Volatile
    var ime: InputMethodEntry? = null
        private set

    @Volatile
    var statusActions: Array<Action> = emptyArray()
        private set

    private var connection: FcitxConnection? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** Called on the main thread whenever the language, schema or options change. */
    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun removeListener(l: () -> Unit) {
        listeners -= l
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    /** Starts following fcitx; safe to call repeatedly (from UI setup). */
    fun attach() {
        if (connection != null) return
        val c = FcitxDaemon.connect("bumboard")
        connection = c
        openDictionary()
        c.lifecycleScope.launch {
            c.runImmediately { eventFlow }.collect { e ->
                when (e) {
                    is FcitxEvent.IMChangeEvent -> update(e.data, null)
                    is FcitxEvent.StatusAreaEvent -> update(e.data.im, e.data.actions)
                    else -> {}
                }
            }
        }
        c.runIfReady {
            ensureDefaultInputMethods()
            update(inputMethodEntryCached, statusAreaActionsCached)
        }
    }

    fun connection(): FcitxConnection {
        attach()
        return connection!!
    }

    private fun update(entry: InputMethodEntry, actions: Array<Action>?) {
        ime = entry
        if (actions != null) statusActions = actions
        mode = modeOf(entry)
        main.post { notifyChanged() }
    }

    fun modeOf(entry: InputMethodEntry): InputMode {
        if (entry.uniqueName != RIME) return InputMode.Other
        return when (entry.subMode.name) {
            Schemas.CANTONESE, Schemas.CANTONESE_YALE -> InputMode.Cantonese
            Schemas.MANDARIN -> InputMode.Mandarin
            Schemas.EN_CANTONESE -> InputMode.EnglishToCantonese
            Schemas.EN_MANDARIN -> InputMode.EnglishToMandarin
            else -> InputMode.Other
        }
    }

    /** First run: Chinese (Rime) and English, with Rime active. */
    private suspend fun FcitxAPI.ensureDefaultInputMethods() {
        if (prefs.getBoolean(Keys.DEFAULT_IMS_DONE, false)) return
        val enabled = enabledIme().map { it.uniqueName }
        if (RIME !in enabled || KEYBOARD_US !in enabled) {
            setEnabledIme((listOf(RIME, KEYBOARD_US) + (enabled - setOf(RIME, KEYBOARD_US))).toTypedArray())
        }
        activateIme(RIME)
        // Gboard-like: tools visible on the bar by default
        AppPrefs.getInstance().keyboard.expandToolbarByDefault.setValue(true)
        prefs.edit().putBoolean(Keys.DEFAULT_IMS_DONE, true).apply()
    }

    fun onPrefsChanged() {
        annotator?.clearCache()
        notifyChanged()
    }
}
