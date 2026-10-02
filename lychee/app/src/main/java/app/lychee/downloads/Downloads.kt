// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.lychee.LycheePrefs
import app.lychee.chinese.RimeData
import app.lychee.chinese.SentenceModel
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Lychee's optional downloads (voice model, Mandarin sentence model). Every file has a fixed
 * address and SHA-256: a damaged or different file is thrown away. Downloads resume where they
 * stopped and only run over Wi-Fi unless the user allows mobile data. Nothing is fetched in
 * the background or checked for updates: a model only changes with a new Lychee version.
 * (Handwriting models come through ML Kit's own downloader, see HandwritingModels.)
 */
object Downloads {
    private const val TAG = "LycheeDownloads"

    class RemoteFile(val name: String, val url: String, val sha256: String, val size: Long)

    enum class Item(val files: List<RemoteFile>) {
        VOICE(listOf(
            RemoteFile("model.int8.onnx",
                "$SENSEVOICE/model.int8.onnx",
                "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d", 237_115_547),
            RemoteFile("tokens.txt",
                "$SENSEVOICE/tokens.txt",
                "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc", 315_894),
            RemoteFile("silero_vad.onnx",
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
                "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", 643_854),
        )),
        SENTENCE_MODEL(listOf(
            RemoteFile(SentenceModel.FILE_NAME,
                "https://github.com/amzxyz/RIME-LMDG/releases/download/LTS/${SentenceModel.FILE_NAME}",
                "873cbbb359fcf4df8b200183683ddc8be7b321eac4c864f8d2c7fc3136d4279f", 409_412_652),
        ));

        val size get() = files.sumOf { it.size }
    }

    sealed class State {
        data object NotInstalled : State()
        data class Downloading(val done: Long, val total: Long) : State()
        data object Verifying : State()
        data object Installed : State()
        data class Failed(val reason: String) : State()
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "lychee-downloads") }
    private val states = Item.entries.associateWith { MutableStateFlow<State>(State.NotInstalled) }
    @Volatile private var cancelled: Item? = null

    fun folder(context: Context, item: Item): File = when (item) {
        Item.VOICE -> File(context.filesDir, "lychee/voice")
        Item.SENTENCE_MODEL -> RimeData.userDir(context)
    }

    fun file(context: Context, item: Item, name: String) = File(folder(context, item), name)

    fun isInstalled(context: Context, item: Item) =
        item.files.all { file(context, item, it.name).let { f -> f.isFile && f.length() == it.size } }

    /** Current state for the settings screen; refreshes the installed / not installed part. */
    fun state(context: Context, item: Item): StateFlow<State> {
        val flow = states.getValue(item)
        if (flow.value is State.NotInstalled || flow.value is State.Installed)
            flow.value = if (isInstalled(context, item)) State.Installed else State.NotInstalled
        return flow
    }

    fun isOnUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Starts (or resumes) the download; returns false if Wi-Fi only is on and there is no Wi-Fi. */
    fun start(context: Context, item: Item): Boolean {
        val appContext = context.applicationContext
        if (LycheePrefs.wifiOnlyDownloads(appContext) && !isOnUnmeteredNetwork(appContext)) return false
        val flow = states.getValue(item)
        if (flow.value is State.Downloading || flow.value is State.Verifying) return true
        cancelled = null
        flow.value = State.Downloading(0, item.size)
        executor.execute { download(appContext, item, flow) }
        return true
    }

    fun cancel(item: Item) {
        cancelled = item
    }

    fun delete(context: Context, item: Item) {
        cancel(item)
        executor.execute {
            item.files.forEach { file(context, item, it.name).delete(); partFile(context, item, it).delete() }
            states.getValue(item).value = State.NotInstalled
            if (item == Item.SENTENCE_MODEL) RimeData.redeploy(context)
        }
    }

    private fun partFile(context: Context, item: Item, f: RemoteFile) = File(folder(context, item), "${f.name}.part")

    private fun download(context: Context, item: Item, flow: MutableStateFlow<State>) {
        try {
            folder(context, item).mkdirs()
            var before = 0L
            for (f in item.files) {
                val target = file(context, item, f.name)
                if (target.isFile && target.length() == f.size) { before += f.size; continue }
                val part = partFile(context, item, f)
                fetch(f, part) { done -> flow.value = State.Downloading(before + done, item.size) }
                flow.value = State.Verifying
                val hash = sha256(part)
                if (hash != f.sha256) {
                    part.delete()
                    throw IOException("${f.name}: checksum does not match (got $hash)")
                }
                if (!part.renameTo(target)) throw IOException("could not move ${f.name} into place")
                before += f.size
            }
            flow.value = State.Installed
            if (item == Item.SENTENCE_MODEL) RimeData.redeploy(context)
        } catch (e: CancelledException) {
            flow.value = if (isInstalled(context, item)) State.Installed else State.NotInstalled
        } catch (e: Exception) {
            Log.w(TAG, "download of $item failed", e)
            flow.value = State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private class CancelledException : IOException("cancelled")

    /** Downloads [f] into [part], resuming what is already there. */
    private fun fetch(f: RemoteFile, part: File, progress: (Long) -> Unit) {
        var url = URL(f.url)
        var have = if (part.exists()) part.length() else 0L
        if (have > f.size) { part.delete(); have = 0 }
        if (have == f.size) return
        repeat(5) { // follow redirects, also across hosts (GitHub and Hugging Face send them)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            val code = conn.responseCode
            if (code in 300..399) {
                url = URL(url, conn.getHeaderField("Location") ?: throw IOException("redirect without location"))
                conn.disconnect()
                return@repeat
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !append) throw IOException("HTTP $code for ${f.name}")
            if (!append) have = 0
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        if (cancelled != null) throw CancelledException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        if (have - lastReport > 512 * 1024) { progress(have); lastReport = have }
                    }
                }
            }
            conn.disconnect()
            if (have != f.size) throw IOException("${f.name}: got $have of ${f.size} bytes")
            progress(have)
            return
        }
        throw IOException("too many redirects for ${f.name}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private const val SENSEVOICE =
    "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/355f4d4884d8afd08aef04b9007a8556d7b463b2"
