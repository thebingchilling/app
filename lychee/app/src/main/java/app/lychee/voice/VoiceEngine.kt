// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import app.lychee.downloads.Downloads
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import helium314.keyboard.latin.utils.Log
import java.util.concurrent.Executors
import kotlin.math.sqrt

/**
 * Offline voice typing: SenseVoice-Small (Mandarin, Cantonese, English, mixed) run by sherpa-onnx,
 * with the Silero voice activity detector cutting speech at pauses. Each piece is transcribed
 * as soon as the speaker pauses, with punctuation and numbers as digits.
 */
object VoiceEngine {
    private const val TAG = "VoiceEngine"
    private const val SAMPLE_RATE = 16000
    private const val WINDOW = 512 // Silero's window

    interface Listener {
        fun onLevel(level: Float)
        fun onSpeaking(speaking: Boolean)
        /** [language] is SenseVoice's guess: "zh", "yue", "en"… */
        fun onText(text: String, language: String)
        fun onError(message: String)
        fun onStopped()
    }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "lychee-voice") }
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var loadedLanguage: String? = null
    @Volatile private var recording = false
    private val unload = Runnable { worker.execute { release() } }

    fun isInstalled(context: Context) = Downloads.isInstalled(context, Downloads.Item.VOICE)

    /**
     * Starts listening; [language] is "auto", "zh", "yue" or "en". Models load on first use
     * (a second or two); they are released a minute after the last use.
     */
    fun start(context: Context, language: String, listener: Listener) {
        if (recording) return
        recording = true
        main.removeCallbacks(unload)
        val appContext = context.applicationContext
        worker.execute {
            try {
                load(appContext, language)
                record(listener)
            } catch (e: Throwable) {
                Log.e(TAG, "voice typing failed", e)
                recording = false
                main.post { listener.onError(e.message ?: e.javaClass.simpleName) }
            }
            main.post { listener.onStopped() }
            main.postDelayed(unload, 60_000)
        }
    }

    /** Stops listening; what was said before is still transcribed. */
    fun stop() {
        recording = false
    }

    val isRecording get() = recording

    private fun load(context: Context, language: String) {
        val folder = Downloads.folder(context, Downloads.Item.VOICE)
        if (recognizer == null) {
            val model = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$folder/model.int8.onnx",
                    language = language,
                    useInverseTextNormalization = true,
                ),
                tokens = "$folder/tokens.txt",
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            recognizer = OfflineRecognizer(null, OfflineRecognizerConfig(featConfig = FeatureConfig(SAMPLE_RATE, 80, 0f), modelConfig = model))
            loadedLanguage = language
        } else if (loadedLanguage != language) {
            val r = recognizer!!
            val config = r.config
            config.modelConfig.senseVoice.language = language
            r.setConfig(config)
            loadedLanguage = language
        }
        if (vad == null) {
            vad = Vad(null, VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "$folder/silero_vad.onnx",
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW,
                    maxSpeechDuration = 20f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            ))
        }
    }

    @SuppressLint("MissingPermission") // checked by the caller (MicPermissionActivity)
    private fun record(listener: Listener) {
        val vad = vad!!
        vad.reset()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, WINDOW * 8))
        if (audio.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("microphone unavailable")
        val shorts = ShortArray(WINDOW)
        val floats = FloatArray(WINDOW)
        var speaking = false
        audio.startRecording()
        try {
            while (recording) {
                val n = audio.read(shorts, 0, WINDOW)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    floats[i] = shorts[i] / 32768f
                    sum += floats[i] * floats[i]
                }
                val level = sqrt(sum / n).toFloat()
                main.post { listener.onLevel(level) }
                vad.acceptWaveform(if (n == WINDOW) floats else floats.copyOf(n))
                val now = vad.isSpeechDetected()
                if (now != speaking) {
                    speaking = now
                    main.post { listener.onSpeaking(now) }
                }
                drain(vad, listener)
            }
        } finally {
            audio.stop()
            audio.release()
        }
        vad.flush()
        drain(vad, listener)
        main.post { listener.onSpeaking(false) }
    }

    private fun drain(vad: Vad, listener: Listener) {
        val r = recognizer ?: return
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            val stream = r.createStream()
            stream.acceptWaveform(segment.samples, SAMPLE_RATE)
            r.decode(stream)
            val result = r.getResult(stream)
            stream.release()
            val text = result.text.trim()
            val lang = result.lang.removePrefix("<|").removeSuffix("|>")
            if (text.isNotEmpty()) main.post { listener.onText(text, lang) }
        }
    }

    private fun release() {
        if (recording) return
        recognizer?.release()
        recognizer = null
        vad?.release()
        vad = null
        loadedLanguage = null
    }
}
