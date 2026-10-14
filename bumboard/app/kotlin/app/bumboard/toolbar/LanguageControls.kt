package app.bumboard.toolbar

import app.bumboard.BumBoard
import app.bumboard.BumBoard.Schemas
import app.bumboard.core.InputMode
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.daemon.launchOnReady

/**
 * Language, English→Chinese and Traditional/Simplified switching.
 *
 * Rime runs as one fcitx input method; fcitx5-rime exposes each schema as an action in the
 * "fcitx-rime-im" menu and each two-state option as an action named
 * fcitx-rime-<schema>-<option>. These helpers find and trigger those actions.
 */
object LanguageControls {

    enum class Language(val label: String) { Cantonese("粵"), Mandarin("普"), English("EN") }

    val language: Language
        get() = when (BumBoard.mode) {
            InputMode.Cantonese, InputMode.EnglishToCantonese -> Language.Cantonese
            InputMode.Mandarin, InputMode.EnglishToMandarin -> Language.Mandarin
            InputMode.Other -> if (BumBoard.ime?.uniqueName == BumBoard.RIME) Language.Cantonese else Language.English
        }

    val englishLookup: Boolean get() = BumBoard.mode.englishLookup

    /** 粵 → 普 → EN → 粵 */
    fun cycleLanguage() = when (language) {
        Language.Cantonese -> select(Schemas.MANDARIN)
        Language.Mandarin -> activateEnglish()
        Language.English -> select(BumBoard.cantoneseSchema)
    }

    fun setLanguage(l: Language) = when (l) {
        Language.Cantonese -> select(BumBoard.cantoneseSchema)
        Language.Mandarin -> select(Schemas.MANDARIN)
        Language.English -> activateEnglish()
    }

    /** Type English, get Chinese words with that meaning (and back). */
    fun toggleEnglishLookup() = when (BumBoard.mode) {
        InputMode.Cantonese -> select(Schemas.EN_CANTONESE)
        InputMode.EnglishToCantonese -> select(BumBoard.cantoneseSchema)
        InputMode.Mandarin -> select(Schemas.EN_MANDARIN)
        InputMode.EnglishToMandarin -> select(Schemas.MANDARIN)
        InputMode.Other -> select(Schemas.EN_CANTONESE)
    }

    /** Label of the current 繁/简 state ("繁" or "简"), or null when the option is not available. */
    val scriptLabel: String?
        get() = scriptAction(BumBoard.statusActions)?.shortText?.takeIf { it.isNotBlank() }

    fun toggleScript() {
        BumBoard.connection().launchOnReady { api ->
            scriptAction(api.statusArea())?.let { api.activateAction(it.id) }
        }
    }

    private fun scriptAction(actions: Array<Action>): Action? = flatten(actions).firstOrNull {
        it.name.startsWith("fcitx-rime-") &&
            (it.name.endsWith("-simplification") || it.name.endsWith("-traditionalization"))
    }

    private fun activateEnglish() {
        BumBoard.connection().launchOnReady { api -> api.activateIme(BumBoard.KEYBOARD_US) }
    }

    /** Switches to Rime (if needed) and selects the schema with [name]. */
    fun select(name: String) {
        BumBoard.connection().launchOnReady { api -> api.selectSchema(name) }
    }

    private suspend fun FcitxAPI.selectSchema(name: String) {
        if (currentIme().uniqueName != BumBoard.RIME) activateIme(BumBoard.RIME)
        val menu = flatten(statusArea()).firstOrNull { it.name == "fcitx-rime-im" }?.menu ?: return
        menu.firstOrNull { it.shortText == name }?.let { activateAction(it.id) }
    }

    private fun flatten(actions: Array<Action>): List<Action> =
        actions.flatMap { listOf(it) + (it.menu?.let(::flatten) ?: emptyList()) }
}
