package app.bumboard.handwriting

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

/**
 * Handwriting recognition for BumBoard over Messenger IPC.
 *
 * Request [MSG_RECOGNIZE] data: "lang" (BCP-47, e.g. zh-Hani-HK), "lens" (IntArray, points per
 * stroke), "xs"/"ys" (FloatArray), "ts" (LongArray), "w"/"h" (writing area), "pre" (text before).
 * Reply [MSG_RESULT]: "candidates" (ArrayList<String>), or "state" = "downloading"/"failed".
 */
class HandwritingService : Service() {

    companion object {
        const val MSG_RECOGNIZE = 1
        const val MSG_RESULT = 2
    }

    private val manager = RemoteModelManager.getInstance()
    private val recognizers = HashMap<String, DigitalInkRecognizer>()
    private val downloading = HashSet<String>()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!trusted(msg.sendingUid)) return
            if (msg.what == MSG_RECOGNIZE) recognize(msg.data, msg.replyTo, msg.arg1)
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    /** Only the BumBoard keyboard (same signing key) may use this service. */
    private fun trusted(uid: Int): Boolean =
        uid == Process.myUid() || packageManager.checkSignatures(uid, Process.myUid()) == PackageManager.SIGNATURE_MATCH

    private fun reply(to: Messenger?, id: Int, data: Bundle) {
        to ?: return
        runCatching { to.send(Message.obtain(null, MSG_RESULT, id, 0).apply { this.data = data }) }
    }

    private fun recognize(data: Bundle, replyTo: Messenger?, id: Int) {
        val lang = data.getString("lang") ?: "zh-Hani-CN"
        val identifier = runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(lang) }.getOrNull()
        if (identifier == null) {
            reply(replyTo, id, Bundle().apply { putString("state", "failed") })
            return
        }
        val model = DigitalInkRecognitionModel.builder(identifier).build()
        manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
            if (!downloaded) {
                reply(replyTo, id, Bundle().apply { putString("state", "downloading") })
                if (downloading.add(lang)) {
                    manager.download(model, DownloadConditions.Builder().build())
                        .addOnCompleteListener { downloading.remove(lang) }
                }
                return@addOnSuccessListener
            }
            val recognizer = recognizers.getOrPut(lang) {
                DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            }
            val ink = buildInk(data)
            val context = RecognitionContext.builder()
                .setPreContext(data.getString("pre") ?: "")
                .setWritingArea(WritingArea(data.getFloat("w"), data.getFloat("h")))
                .build()
            recognizer.recognize(ink, context)
                .addOnSuccessListener { result ->
                    reply(replyTo, id, Bundle().apply {
                        putStringArrayList("candidates", ArrayList(result.candidates.map { it.text }))
                    })
                }
                .addOnFailureListener { reply(replyTo, id, Bundle().apply { putString("state", "failed") }) }
        }
    }

    private fun buildInk(data: Bundle): Ink {
        val lens = data.getIntArray("lens") ?: IntArray(0)
        val xs = data.getFloatArray("xs") ?: FloatArray(0)
        val ys = data.getFloatArray("ys") ?: FloatArray(0)
        val ts = data.getLongArray("ts") ?: LongArray(0)
        val ink = Ink.builder()
        var i = 0
        for (len in lens) {
            val stroke = Ink.Stroke.builder()
            repeat(len) {
                if (i < xs.size) stroke.addPoint(Ink.Point.create(xs[i], ys[i], ts.getOrElse(i) { 0L }))
                i++
            }
            ink.addStroke(stroke.build())
        }
        return ink.build()
    }

    override fun onDestroy() {
        recognizers.values.forEach { it.close() }
        super.onDestroy()
    }
}
