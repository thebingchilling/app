// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.chinese

import android.content.Context
import java.io.File

/**
 * The optional Mandarin sentence model (Rime's octagram plugin). When its file is in Rime's
 * user folder, a patch turns it on for the Mandarin scheme; otherwise the patch is removed.
 */
object SentenceModel {
    const val LANGUAGE = "wanxiang-lts-zh-hans"
    const val FILE_NAME = "$LANGUAGE.gram"

    fun file(context: Context) = File(RimeData.userDir(context), FILE_NAME)

    fun isInstalled(context: Context) = file(context).let { it.isFile && it.length() > 0 }

    /** Writes or removes lychee_mandarin.custom.yaml to match whether the model is installed. */
    fun applyConfig(context: Context) {
        val patch = File(RimeData.userDir(context).apply { mkdirs() }, "lychee_mandarin.custom.yaml")
        if (isInstalled(context)) {
            patch.writeText(
                """
                |# Written by Lychee: the Mandarin sentence model is installed.
                |# Values from rime-ice's grammar recipe (others/recipes/grammar.recipe.yaml).
                |patch:
                |  grammar:
                |    language: $LANGUAGE
                |    collocation_max_length: 6
                |    collocation_min_length: 3
                |    collocation_penalty: -14
                |    non_collocation_penalty: -6
                |    weak_collocation_penalty: -100
                |    rear_penalty: -20
                |  translator/contextual_suggestions: false
                |  translator/max_homophones: 8
                |""".trimMargin()
            )
        } else {
            patch.delete()
        }
    }
}
