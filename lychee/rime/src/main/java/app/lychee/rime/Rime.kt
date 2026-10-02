// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.rime

/** Thin wrapper over librime's C API (see rime_jni.cpp). Call [startup] once before using sessions. */
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

    /** Initializes Rime and deploys anything out of date. Blocking: run off the main thread. */
    @Synchronized
    fun startup(sharedDir: String, userDir: String, fullCheck: Boolean) {
        nativeStartup(sharedDir, userDir, fullCheck)
        isReady = true
    }

    /** Re-deploys after a change to the configuration (e.g. a downloaded sentence model). Blocking. */
    @Synchronized
    fun redeploy() {
        isReady = false
        nativeRedeploy()
        isReady = true
    }

    fun syncUserData() = nativeSyncUserData()

    class Session {
        private var id = nativeCreateSession()
        val isValid get() = id != 0L

        fun destroy() {
            if (id != 0L) nativeDestroySession(id)
            id = 0
        }

        fun selectSchema(schemaId: String) = nativeSelectSchema(id, schemaId)
        val currentSchema: String? get() = nativeCurrentSchema(id)
        fun processKey(keycode: Int, mask: Int = 0) = nativeProcessKey(id, keycode, mask)
        fun setInput(input: String) = nativeSetInput(id, input)
        fun clearComposition() = nativeClearComposition(id)
        fun commitComposition() = nativeCommitComposition(id)
        fun getCommit(): String? = nativeGetCommit(id)
        val input: String get() = nativeGetInput(id) ?: ""
        val composition: Composition?
            get() = nativeGetComposition(id)?.let { Composition(it[0] ?: "", it[1]) }

        fun candidates(start: Int = 0, max: Int = 50): List<Candidate> {
            val raw = nativeGetCandidates(id, start, max)
            return List(raw.size / 2) { Candidate(raw[2 * it] ?: "", raw[2 * it + 1] ?: "") }
        }

        fun selectCandidate(index: Int) = nativeSelectCandidate(id, index)
        fun deleteCandidate(index: Int) = nativeDeleteCandidate(id, index)
        fun setOption(name: String, value: Boolean) = nativeSetOption(id, name, value)
        fun getOption(name: String) = nativeGetOption(id, name)
    }

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
