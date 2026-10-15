package app.bumboard.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.bumboard.BumBoard
import app.bumboard.core.InputMode
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

/**
 * Voice typing in the keyboard, using the system speech recogniser (on-device where the phone
 * supports it) in the current language: Cantonese (yue-Hant-HK), Mandarin or English.
 * An offline model download (sherpa-onnx) can replace the recogniser later.
 */
class VoiceWindow : InputWindow.ExtendedInputWindow<VoiceWindow>() {

    private val theme by manager.theme()
    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()

    override val title: String get() = "Voice · ${languageName()}"

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private fun languageTag(): String = when (BumBoard.mode) {
        InputMode.Cantonese, InputMode.EnglishToCantonese -> "yue-Hant-HK"
        InputMode.Mandarin, InputMode.EnglishToMandarin -> "cmn-Hans-CN"
        InputMode.Other -> if (BumBoard.ime?.uniqueName == BumBoard.RIME) "yue-Hant-HK" else "en-US"
    }

    private fun languageName(): String = when (languageTag()) {
        "yue-Hant-HK" -> "粵語"
        "cmn-Hans-CN" -> "普通话"
        else -> "English"
    }

    private val d by lazy { context.resources.displayMetrics.density }

    private val status by lazy {
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.keyTextColor)
            gravity = Gravity.CENTER
        }
    }

    private val partial by lazy {
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(theme.keyTextColor)
            gravity = Gravity.CENTER
            maxLines = 3
        }
    }

    private val micPill by lazy {
        GradientDrawable().apply { shape = GradientDrawable.OVAL }
    }

    private val mic by lazy {
        ImageView(context).apply {
            setImageResource(R.drawable.ms_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (22 * d).toInt()
            setPadding(p, p, p, p)
            background = micPill
            setOnClickListener { if (listening) stop() else start() }
            contentDescription = "Start or stop listening"
        }
    }

    private val view by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = (16 * d).toInt()
            setPadding(p, p, p, p)
            addView(partial, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(FrameLayout(context).apply {
                addView(mic, FrameLayout.LayoutParams((84 * d).toInt(), (84 * d).toInt(), Gravity.CENTER))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (100 * d).toInt()))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showListening(on: Boolean) {
        listening = on
        micPill.setColor(if (on) theme.genericActiveBackgroundColor else ColorUtils.setAlphaComponent(theme.keyTextColor, 0x22))
        mic.imageTintList = ColorStateList.valueOf(if (on) theme.genericActiveForegroundColor else theme.keyTextColor)
        status.text = if (on) "Listening…  tap to stop" else "Tap the microphone to speak ${languageName()}"
    }

    override fun onCreateView(): View = view

    override fun onAttached() {
        partial.text = ""
        showListening(false)
        start()
    }

    override fun onDetached() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Allow the microphone, then tap to speak"
            context.startActivity(
                Intent(context, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status.text = "No speech recogniser on this phone"
            return
        }
        val r = recognizer ?: createRecognizer().also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
        showListening(true)
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)

    private fun stop() {
        recognizer?.stopListening()
        showListening(false)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 40f)
            mic.scaleX = scale
            mic.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = showListening(false)
        override fun onError(error: Int) {
            showListening(false)
            status.text = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — tap to try again"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Needs a connection for ${languageName()} on this phone"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone, then tap to speak"
                else -> "Voice typing isn't available right now"
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                partial.text = it
            }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                service.commitText(text)
                partial.text = ""
                windowManager.attachWindow(KeyboardWindow)
            } else {
                showListening(false)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
