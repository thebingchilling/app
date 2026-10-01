/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package app.lychee.handwriting

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
import kotlinx.coroutines.tasks.await

/**
 * Google ML Kit Digital Ink, on device. The model for a language is
 * downloaded from Google once (about 20 MB) and kept by ML Kit.
 */
class HandwritingRecognizer(val languageTag: String) {

    private val model = DigitalInkRecognitionModel.builder(
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: error("no handwriting model for $languageTag")
    ).build()

    private var recognizer: DigitalInkRecognizer? = null

    suspend fun isDownloaded(): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(model).await()

    suspend fun download() {
        RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build()).await()
    }

    /** Best guesses first. [preContext] is the text before the cursor. */
    suspend fun recognize(strokes: List<List<InkView.Point>>, width: Float, height: Float, preContext: String): List<String> {
        val ink = Ink.builder().apply {
            strokes.forEach { stroke ->
                addStroke(Ink.Stroke.builder().apply {
                    stroke.forEach { addPoint(Ink.Point.create(it.x, it.y, it.t)) }
                }.build())
            }
        }.build()
        val r = recognizer ?: DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build()
        ).also { recognizer = it }
        val context = RecognitionContext.builder()
            .setPreContext(preContext.takeLast(20))
            .setWritingArea(WritingArea(width, height))
            .build()
        return r.recognize(ink, context).await().candidates.map { it.text }
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }

    companion object {
        /** Cantonese -> Hong Kong characters, Mandarin -> mainland, else English. */
        fun languageFor(inputMethod: String) = when (inputMethod) {
            "rime" -> "zh-Hani-HK"
            "pinyin" -> "zh-Hani-CN"
            else -> "en-US"
        }
    }
}
