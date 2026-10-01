/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 * SPDX-FileCopyrightText: Copyright 2026 Lychee contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import app.lychee.dict.Gloss
import app.lychee.dict.LycheeDictionary
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.gravityCenterHorizontal

/**
 * One candidate. Lychee adds two small lines under Chinese words:
 * "Yale · pinyin" and the English meaning (see [LycheeDictionary]).
 */
class CandidateItemUi(override val ctx: Context, val theme: Theme) : Ui {

    private val prefs = AppPrefs.getInstance().lychee
    private val showYale by prefs.showYale
    private val showPinyin by prefs.showPinyin
    private val showEnglish by prefs.showEnglish

    private val text = view(::AutoScaleTextView) {
        scaleMode = AutoScaleTextView.Mode.Proportional
        textSize = 20f // sp
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(theme.candidateTextColor)
    }

    private val reading = textView {
        textSize = 11f // sp
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = gravityCenter
        includeFontPadding = false
        setTextColor(theme.candidateCommentColor)
    }

    private val english = textView {
        textSize = 10f // sp
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = gravityCenter
        includeFontPadding = false
        maxWidth = dp(150)
        setTextColor(theme.candidateCommentColor)
    }

    private val column = verticalLayout {
        gravity = gravityCenter
        add(text, lParams(wrapContent, wrapContent) { gravity = gravityCenterHorizontal })
        add(reading, lParams(wrapContent, wrapContent) { gravity = gravityCenterHorizontal })
        add(english, lParams(wrapContent, wrapContent) { gravity = gravityCenterHorizontal })
    }

    override val root = view(::CustomGestureView) {
        background = pressHighlightDrawable(theme.keyPressHighlightColor)

        /**
         * candidate long press feedback is handled by [org.fcitx.fcitx5.android.input.BaseInputView.showCandidateActionMenu]
         */
        longPressFeedbackEnabled = false

        add(column, lParams(wrapContent, matchParent) {
            gravity = gravityCenter
        })
    }

    /** Pinyin gets a second colour so the two readings are easy to tell apart. */
    private val pinyinColor = ColorUtils.blendARGB(theme.candidateCommentColor, theme.accentKeyBackgroundColor, 0.55f)

    fun updateCandidate(candidate: CandidateWord) {
        val fg = theme.candidateTextColor
        val altFg = theme.candidateCommentColor
        // Rime's comment is the Jyutping it matched; it becomes the Yale line.
        val hint = candidate.comment.trim().takeIf { LycheeDictionary.looksLikeJyutping(it) }
        val gloss = if (showYale || showPinyin || showEnglish) LycheeDictionary.lookup(candidate.text, hint) else null
        text.text = buildSpannedString {
            color(fg) {
                append(candidate.text)
            }
            if (hint == null && candidate.comment.isNotBlank()) {
                if (candidate.spaceBetweenComment) {
                    append(" ")
                }
                color(altFg) {
                    append(candidate.comment)
                }
            }
        }
        showGloss(gloss)
    }

    private fun showGloss(gloss: Gloss?) {
        val yale = gloss?.yale.takeIf { showYale }.orEmpty()
        val pinyin = gloss?.pinyin.takeIf { showPinyin }.orEmpty()
        reading.text = buildSpannedString {
            append(yale)
            if (yale.isNotEmpty() && pinyin.isNotEmpty()) append(" · ")
            color(pinyinColor) { append(pinyin) }
        }
        reading.visibility = if (reading.text.isEmpty()) View.GONE else View.VISIBLE
        english.text = gloss?.english.takeIf { showEnglish }.orEmpty()
        english.visibility = if (english.text.isEmpty()) View.GONE else View.VISIBLE
    }
}
