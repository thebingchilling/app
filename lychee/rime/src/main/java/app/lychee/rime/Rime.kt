// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.rime

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Thin wrapper over librime's C API (see rime_jni.cpp). Call [startup] once before using
 * sessions. A redeploy ends all sessions: session calls made meanwhile do nothing (they never
 * wait for it), and sessions from before it report [Session.isValid] false.
 */
object Rime {
    init {
        System.loadLibrary("lychee_rime")
    }

    // X11 keysyms Rime understands
    const val KEY_BACKSPACE = 0xff08
    const val KEY_RETURN = 0xff0d
    const val KEY_ESCAPE = 0xff1b
    const val KEY_LEFT = 0xff51
    const val KEY_RIGHT = 0xff53
    const val KEY_SPACE = 0x20

    class Candidate(val text: String, val comment: String)

    class Composition(val preedit: String, val commitPreview: String?)

    @Volatile
    var isReady = false
        private set

    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var generation = 0

    /** Initializes Rime and deploys anything out of date. Blocking: run off the main thread. */
    fun startup(sharedDir: String, userDir: String, fullCheck: Boolean) {
        lock.writeLock().lock()
        try {
            nativeStartup(sharedDir, userDir, fullCheck)
            generation++
            isReady = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    /** Re-deploys after a change to the configuration (e.g. a downloaded sentence model). Blocking. */
    fun redeploy() {
        isReady = false
        lock.writeLock().lock()
        try {
            nativeRedeploy()
            generation++
            isReady = true
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun syncUserData() = guarded(Unit) { nativeSyncUserData() }

    /** Runs [block] unless a startup or redeploy is under way (then returns [default] at once). */
    private inline fun <T> guarded(default: T, block: () -> T): T {
        if (!lock.readLock().tryLock()) return default
        try {
            return if (isReady) block() else default
        } finally {
            lock.readLock().unlock()
        }
    }

    class Session {
        private val sessionGeneration = generation
        private var id = guarded(0L) { nativeCreateSession() }
        val isValid get() = id != 0L && sessionGeneration == generation

        private inline fun <T> call(default: T, block: (Long) -> T): T =
            guarded(default) { if (isValid) block(id) else default }

        fun destroy() {
            call(Unit) { nativeDestroySession(it) }
            id = 0
        }

        fun selectSchema(schemaId: String) = call(false) { nativeSelectSchema(it, schemaId) }
        val currentSchema: String? get() = call(null) { nativeCurrentSchema(it) }
        fun processKey(keycode: Int, mask: Int = 0) = call(false) { nativeProcessKey(it, keycode, mask) }
        fun setInput(input: String) = call(false) { nativeSetInput(it, input) }
        fun clearComposition() = call(Unit) { nativeClearComposition(it) }
        fun commitComposition() = call(false) { nativeCommitComposition(it) }
        fun getCommit(): String? = call(null) { nativeGetCommit(it) }
        val input: String get() = call(null) { nativeGetInput(it) } ?: ""
        val composition: Composition?
            get() = call(null) { nativeGetComposition(it) }?.let { Composition(it[0] ?: "", it[1]) }

        fun candidates(start: Int = 0, max: Int = 50): List<Candidate> {
            val raw = call(emptyArray()) { nativeGetCandidates(it, start, max) }
            return List(raw.size / 2) { Candidate(raw[2 * it] ?: "", raw[2 * it + 1] ?: "") }
        }

        fun selectCandidate(index: Int) = call(false) { nativeSelectCandidate(it, index) }
        fun deleteCandidate(index: Int) = call(false) { nativeDeleteCandidate(it, index) }
        fun setOption(name: String, value: Boolean) = call(Unit) { nativeSetOption(it, name, value) }
        fun getOption(name: String) = call(false) { nativeGetOption(it, name) }
    }

    /**
     * Converts [text] with an OpenCC config (a path such as shared/opencc/s2hk.json).
     * Works without Rime being started; returns null when the config cannot be opened.
     */
    fun openccConvert(configPath: String, text: String): String? = nativeOpenccConvert(configPath, text)

    @JvmStatic private external fun nativeOpenccConvert(config: String, text: String): String?
    @JvmStatic private external fun nativeStartup(shared: String, user: String, fullCheck: Boolean)
    @JvmStatic private external fun nativeRedeploy()
    @JvmStatic private external fun nativeShutdown()
    @JvmStatic private external fun nativeSyncUserData()
    @JvmStatic private external fun nativeCreateSession(): Long
    @JvmStatic private external fun nativeDestroySession(s: Long)
    @JvmStatic private external fun nativeSelectSchema(s: Long, id: String): Boolean
    @JvmStatic private external fun nativeCurrentSchema(s: Long): String?
    @JvmStatic private external fun nativeProcessKey(s: Long, keycode: Int, mask: Int): Boolean
    @JvmStatic private external fun nativeSetInput(s: Long, input: String): Boolean
    @JvmStatic private external fun nativeClearComposition(s: Long)
    @JvmStatic private external fun nativeCommitComposition(s: Long): Boolean
    @JvmStatic private external fun nativeGetCommit(s: Long): String?
    @JvmStatic private external fun nativeGetInput(s: Long): String?
    @JvmStatic private external fun nativeGetComposition(s: Long): Array<String?>?
    @JvmStatic private external fun nativeGetCandidates(s: Long, start: Int, max: Int): Array<String?>
    @JvmStatic private external fun nativeSelectCandidate(s: Long, index: Int): Boolean
    @JvmStatic private external fun nativeDeleteCandidate(s: Long, index: Int): Boolean
    @JvmStatic private external fun nativeSetOption(s: Long, name: String, value: Boolean)
    @JvmStatic private external fun nativeGetOption(s: Long, name: String): Boolean
}
