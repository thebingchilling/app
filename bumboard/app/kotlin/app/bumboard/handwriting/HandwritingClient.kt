package app.bumboard.handwriting

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * Talks to the handwriting add-on APK (bumboard/addons/handwriting), which holds ML Kit.
 * Its package is this app's id with ".handwriting" inserted (debug builds keep ".debug").
 */
class HandwritingClient(private val ctx: Context) {

    sealed class Result {
        data class Candidates(val list: List<String>) : Result()
        object Downloading : Result()
        object Failed : Result()
    }

    private var service: Messenger? = null
    private var bound = false
    private var nextId = 0
    private var latestId = -1
    var onResult: (Result) -> Unit = {}

    private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.arg1 != latestId) return // a newer request is on its way
            val d = msg.data
            val list = d.getStringArrayList("candidates")
            onResult(
                when {
                    list != null -> Result.Candidates(list)
                    d.getString("state") == "downloading" -> Result.Downloading
                    else -> Result.Failed
                }
            )
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { Messenger(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    val addOnPackage: String
        get() = ctx.packageName.replaceFirst("app.bumboard.android", "app.bumboard.android.handwriting")

    fun isInstalled(): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(addOnPackage, 0); true
    }.getOrDefault(false)

    fun bind(): Boolean {
        if (bound) return true
        bound = runCatching {
            ctx.bindService(
                Intent("app.bumboard.HANDWRITING").setPackage(addOnPackage),
                connection, Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        return bound
    }

    fun unbind() {
        if (bound) runCatching { ctx.unbindService(connection) }
        bound = false
        service = null
    }

    fun recognize(lang: String, strokes: List<List<FloatArray>>, width: Float, height: Float, preContext: String) {
        val s = service ?: return
        val lens = IntArray(strokes.size) { strokes[it].size }
        val n = lens.sum()
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ts = LongArray(n)
        var i = 0
        for (stroke in strokes) for (p in stroke) {
            xs[i] = p[0]; ys[i] = p[1]; ts[i] = p[2].toLong(); i++
        }
        latestId = nextId++
        val msg = Message.obtain(null, 1, latestId, 0).apply {
            data = Bundle().apply {
                putString("lang", lang)
                putIntArray("lens", lens)
                putFloatArray("xs", xs)
                putFloatArray("ys", ys)
                putLongArray("ts", ts)
                putFloat("w", width)
                putFloat("h", height)
                putString("pre", preContext)
            }
            replyTo = replies
        }
        runCatching { s.send(msg) }
    }
}
