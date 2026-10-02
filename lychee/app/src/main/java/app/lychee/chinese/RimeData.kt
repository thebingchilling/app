// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.chinese

import android.content.Context
import app.lychee.rime.Rime
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Rime's files: the shared data (schemes, compiled dictionaries) is copied out of the APK's
 * "rime" assets once per install or update, the user data (learned words) lives beside it.
 * [prepare] starts Rime on a background thread; [whenReady] runs code on that thread once
 * Rime is ready.
 */
object RimeData {
    private const val TAG = "RimeData"
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "lychee-rime") }

    @Volatile
    private var started = false

    fun sharedDir(context: Context) = File(context.filesDir, "rime/shared")
    fun userDir(context: Context) = File(context.filesDir, "rime/user")

    @Synchronized
    fun prepare(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        executor.execute {
            val start = System.currentTimeMillis()
            try {
                val shared = sharedDir(appContext)
                val user = userDir(appContext).apply { mkdirs() }
                val stamp = installStamp(appContext)
                val stampFile = File(shared, ".lychee-installed")
                val fresh = !stampFile.exists() || stampFile.readText() != stamp
                if (fresh) {
                    shared.deleteRecursively()
                    copyAssets(appContext, "rime", shared)
                    stampFile.writeText(stamp)
                }
                SentenceModel.applyConfig(appContext)
                Rime.startup(shared.absolutePath, user.absolutePath, fresh)
                Log.i(TAG, "Rime ready in ${System.currentTimeMillis() - start} ms (fresh copy: $fresh)")
            } catch (e: Throwable) {
                Log.e(TAG, "Rime failed to start", e)
            }
        }
    }

    /** Runs [block] on Rime's thread after startup (or a redeploy) has finished. */
    fun whenReady(block: () -> Unit) = executor.execute(block)

    /** Re-deploys on Rime's thread, e.g. after the sentence model was downloaded or deleted. */
    fun redeploy(context: Context, then: (() -> Unit)? = null) = executor.execute {
        SentenceModel.applyConfig(context.applicationContext)
        Rime.redeploy()
        then?.invoke()
    }

    fun syncUserData() = executor.execute { if (Rime.isReady) Rime.syncUserData() }

    private fun installStamp(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.lastUpdateTime}"
    }

    private fun copyAssets(context: Context, path: String, target: File) {
        val assets = context.assets
        val children = assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input -> target.outputStream().use { input.copyTo(it, 1 shl 16) } }
            return
        }
        target.mkdirs()
        for (child in children) copyAssets(context, "$path/$child", File(target, child))
    }
}
