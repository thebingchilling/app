// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.handwriting

import android.content.Context
import app.lychee.LycheePrefs
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google ML Kit's handwriting models (about 20 MB each), downloaded through ML Kit's own
 * downloader when the user asks for them. Until one is installed, Chinese handwriting uses the
 * built-in [HanziLookup].
 */
object HandwritingModels {
    enum class Model(val tag: String) {
        CANTONESE("zh-Hani-HK"),
        MANDARIN("zh-Hani-CN"),
        ENGLISH("en-US");

        val mlKit: DigitalInkRecognitionModel by lazy {
            DigitalInkRecognitionModel.builder(DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)!!).build()
        }
    }

    sealed class State {
        data object Unknown : State()
        data object NotInstalled : State()
        data object Downloading : State()
        data object Installed : State()
        data class Failed(val reason: String) : State()
    }

    private val states = Model.entries.associateWith { MutableStateFlow<State>(State.Unknown) }
    private val manager by lazy { RemoteModelManager.getInstance() }

    fun state(model: Model): StateFlow<State> {
        val flow = states.getValue(model)
        if (flow.value !is State.Downloading) refresh(model)
        return flow
    }

    /** Last known: whether the model is there (refreshed in the background). */
    fun isInstalled(model: Model) = states.getValue(model).value is State.Installed

    fun refresh(model: Model) {
        manager.isModelDownloaded(model.mlKit).addOnSuccessListener { installed ->
            val flow = states.getValue(model)
            if (flow.value !is State.Downloading) flow.value = if (installed) State.Installed else State.NotInstalled
        }
    }

    fun download(context: Context, model: Model) {
        val flow = states.getValue(model)
        flow.value = State.Downloading
        val conditions = DownloadConditions.Builder().apply {
            if (LycheePrefs.wifiOnlyDownloads(context)) requireWifi()
        }.build()
        manager.download(model.mlKit, conditions)
            .addOnSuccessListener { flow.value = State.Installed }
            .addOnFailureListener { flow.value = State.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun delete(model: Model) {
        manager.deleteDownloadedModel(model.mlKit)
            .addOnCompleteListener { states.getValue(model).value = State.NotInstalled }
    }
}
