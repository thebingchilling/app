/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */
package org.fcitx.fcitx5.android.input.handwriting

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lychee.handwriting.HandwritingRecognizer
import app.lychee.handwriting.InkView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import timber.log.Timber

/**
 * Lychee: write a character, pick it from the candidate row (which shows
 * Yale · pinyin and English like the keyboard does). Writing the next
 * character after a pause commits the best guess for the previous one.
 */
class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val theme by manager.theme()

    private var recognizer: HandwritingRecognizer? = null
    private val strokes = mutableListOf<List<InkView.Point>>()
    private var candidates = listOf<String>()
    private var lastStrokeEnd = 0L
    private var recognizeJob: Job? = null

    override val title: String by lazy { context.getString(R.string.lychee_handwriting) }

    private val adapter = object : RecyclerView.Adapter<CandidateViewHolder>() {
        override fun getItemCount() = candidates.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
            val ui = CandidateItemUi(parent.context, theme)
            ui.root.apply {
                minimumWidth = dp(48)
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
            }
            return CandidateViewHolder(ui)
        }

        override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
            val text = candidates[position]
            holder.update(position, CandidateWord("", text, ""))
            holder.itemView.setOnClickListener { commit(text) }
        }
    }

    private val ink by lazy {
        InkView(context).apply {
            inkPaint.color = theme.keyTextColor
            hintPaint.color = theme.altKeyTextColor
            onStrokeStart = {
                // A new character after a pause: keep the best guess for the last one.
                if (candidates.isNotEmpty() && System.currentTimeMillis() - lastStrokeEnd > AUTO_COMMIT_MS) {
                    commit(candidates.first())
                }
            }
            onStrokeEnd = { points ->
                strokes += points
                lastStrokeEnd = System.currentTimeMillis()
                scheduleRecognition()
            }
        }
    }

    private fun button(text: String, onClick: () -> Unit) = context.textView {
        this.text = text
        textSize = 16f
        gravity = gravityCenter
        setTextColor(theme.altKeyTextColor)
        setOnClickListener { onClick() }
    }

    private val candidateList by lazy {
        context.recyclerView {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@HandwritingWindow.adapter
        }
    }

    private val view by lazy {
        context.verticalLayout {
            add(candidateList, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)))
            add(ink, lParams(matchParent, 0) { weight = 1f })
            add(horizontalLayout {
                add(button("✕") { clearInk() }, lParams(0, matchParent) { weight = 1f })
                add(button("␣") { commit(candidates.firstOrNull() ?: " ") }, lParams(0, matchParent) { weight = 2f })
                add(button("⌫") { backspace() }, lParams(0, matchParent) { weight = 1f })
                add(button("⏎") { enter() }, lParams(0, matchParent) { weight = 1f })
            }, lParams(matchParent, dp(48)))
        }
    }

    override fun onCreateView(): View = view

    override fun onAttached() {
        ink.hint = context.getString(R.string.lychee_handwriting_hint)
        service.postFcitxJob {
            val language = HandwritingRecognizer.languageFor(currentIme().uniqueName)
            service.lifecycleScope.launch { prepare(language) }
        }
    }

    override fun onDetached() {
        recognizeJob?.cancel()
        recognizer?.close()
        recognizer = null
    }

    private suspend fun prepare(language: String) {
        val r = runCatching { HandwritingRecognizer(language) }.getOrElse {
            ink.hint = context.getString(R.string.lychee_handwriting_unavailable)
            return
        }
        recognizer = r
        runCatching {
            if (!r.isDownloaded()) {
                ink.hint = context.getString(R.string.lychee_handwriting_downloading)
                r.download()
            }
            ink.hint = context.getString(R.string.lychee_handwriting_hint)
        }.onFailure {
            Timber.w(it, "handwriting model download failed")
            ink.hint = context.getString(R.string.lychee_handwriting_download_failed)
        }
    }

    private fun scheduleRecognition() {
        val r = recognizer ?: return
        recognizeJob?.cancel()
        recognizeJob = service.lifecycleScope.launch {
            delay(RECOGNIZE_DELAY_MS)
            val before = service.currentInputConnection?.getTextBeforeCursor(20, 0)?.toString().orEmpty()
            val result = runCatching {
                r.recognize(strokes.toList(), ink.width.toFloat(), ink.height.toFloat(), before)
            }.onFailure { Timber.w(it, "handwriting recognition failed") }.getOrDefault(emptyList())
            showCandidates(result)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showCandidates(list: List<String>) {
        candidates = list.distinct().take(MAX_CANDIDATES)
        adapter.notifyDataSetChanged()
        candidateList.scrollToPosition(0)
    }

    private fun commit(text: String) {
        service.commitText(text)
        clearInk()
    }

    private fun clearInk() {
        recognizeJob?.cancel()
        strokes.clear()
        ink.clear()
        showCandidates(emptyList())
    }

    private fun backspace() {
        if (!ink.isEmpty) clearInk()
        else service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun enter() {
        if (candidates.isNotEmpty()) commit(candidates.first())
        else service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    companion object {
        private const val RECOGNIZE_DELAY_MS = 250L
        private const val AUTO_COMMIT_MS = 900L
        private const val MAX_CANDIDATES = 10
    }
}
