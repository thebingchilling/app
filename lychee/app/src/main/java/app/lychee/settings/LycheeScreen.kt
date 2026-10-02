// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lychee.LycheePrefs
import app.lychee.downloads.Downloads
import app.lychee.handwriting.HandwritingModels
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference

/** Lychee's settings: readings and meanings, Chinese typing, voice, downloads, credits. */
@Composable
fun LycheeScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.lychee_settings_title), settings = emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PreferenceCategory(stringResource(R.string.lychee_settings_readings))
            SwitchPreference(stringResource(R.string.lychee_show_yale), key = LycheePrefs.SHOW_YALE, default = true,
                description = stringResource(R.string.lychee_show_yale_summary))
            SwitchPreference(stringResource(R.string.lychee_show_pinyin), key = LycheePrefs.SHOW_PINYIN, default = true,
                description = stringResource(R.string.lychee_show_pinyin_summary))
            SwitchPreference(stringResource(R.string.lychee_show_english), key = LycheePrefs.SHOW_ENGLISH, default = true,
                description = stringResource(R.string.lychee_show_english_summary))
            SwitchPreference(stringResource(R.string.lychee_wrap_meanings), key = LycheePrefs.WRAP_MEANINGS, default = false,
                description = stringResource(R.string.lychee_wrap_meanings_summary))
            SwitchPreference(stringResource(R.string.lychee_use_jyutping), key = LycheePrefs.USE_JYUTPING, default = false,
                description = stringResource(R.string.lychee_use_jyutping_summary))

            PreferenceCategory(stringResource(R.string.lychee_settings_typing))
            SwitchPreference(stringResource(R.string.lychee_full_width), key = LycheePrefs.FULL_WIDTH_PUNCTUATION, default = true,
                description = stringResource(R.string.lychee_full_width_summary))
            SwitchPreference(stringResource(R.string.lychee_emoji_candidates), key = LycheePrefs.EMOJI_CANDIDATES, default = true)

            PreferenceCategory(stringResource(R.string.voice))
            SwitchPreference(stringResource(R.string.lychee_voice_follows_keyboard), key = LycheePrefs.VOICE_FOLLOWS_KEYBOARD, default = false,
                description = stringResource(R.string.lychee_voice_follows_keyboard_summary))

            DownloadsSection()

            PreferenceCategory(stringResource(R.string.lychee_settings_credits))
            Text(stringResource(R.string.lychee_credits), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** The optional downloads: voice, better handwriting, the Mandarin sentence model. */
@Composable
fun DownloadsSection() {
    val context = LocalContext.current
    PreferenceCategory(stringResource(R.string.lychee_settings_downloads))
    SwitchPreference(stringResource(R.string.lychee_wifi_only), key = LycheePrefs.WIFI_ONLY_DOWNLOADS, default = true)
    DownloadRow(context, Downloads.Item.VOICE, R.string.lychee_download_voice, R.string.lychee_download_voice_summary)
    for (model in HandwritingModels.Model.entries) HandwritingRow(context, model)
    DownloadRow(context, Downloads.Item.SENTENCE_MODEL, R.string.lychee_download_sentence, R.string.lychee_download_sentence_summary)
}

private fun mb(bytes: Long) = "${(bytes + 500_000) / 1_000_000} MB"

@Composable
private fun DownloadRow(context: Context, item: Downloads.Item, title: Int, summary: Int) {
    val state by Downloads.state(context, item).collectAsState()
    val status = when (val s = state) {
        is Downloads.State.NotInstalled -> stringResource(R.string.lychee_download_size, mb(item.size))
        is Downloads.State.Downloading -> stringResource(R.string.lychee_downloading, mb(s.done), mb(s.total))
        is Downloads.State.Verifying -> stringResource(R.string.lychee_verifying)
        is Downloads.State.Installed -> stringResource(R.string.lychee_installed)
        is Downloads.State.Failed -> stringResource(R.string.lychee_download_failed, s.reason)
    }
    Preference(name = stringResource(title), description = stringResource(summary) + "\n" + status, onClick = {}) {
        when (state) {
            is Downloads.State.Installed -> TextButton({ Downloads.delete(context, item) }) { Text(stringResource(R.string.lychee_delete)) }
            is Downloads.State.Downloading, is Downloads.State.Verifying ->
                TextButton({ Downloads.cancel(item) }) { Text(stringResource(android.R.string.cancel)) }
            else -> TextButton({
                if (!Downloads.start(context, item)) Toast.makeText(context, R.string.lychee_needs_wifi, Toast.LENGTH_LONG).show()
            }) { Text(stringResource(R.string.lychee_download)) }
        }
    }
}

@Composable
private fun HandwritingRow(context: Context, model: HandwritingModels.Model) {
    val state by HandwritingModels.state(model).collectAsState()
    val title = when (model) {
        HandwritingModels.Model.CANTONESE -> R.string.lychee_download_handwriting_cantonese
        HandwritingModels.Model.MANDARIN -> R.string.lychee_download_handwriting_mandarin
        HandwritingModels.Model.ENGLISH -> R.string.lychee_download_handwriting_english
    }
    val status = when (val s = state) {
        is HandwritingModels.State.Unknown -> ""
        is HandwritingModels.State.NotInstalled -> stringResource(R.string.lychee_download_size, "20 MB")
        is HandwritingModels.State.Downloading -> stringResource(R.string.lychee_downloading_unknown)
        is HandwritingModels.State.Installed -> stringResource(R.string.lychee_installed)
        is HandwritingModels.State.Failed -> stringResource(R.string.lychee_download_failed, s.reason)
    }
    Preference(name = stringResource(title), description = stringResource(R.string.lychee_download_handwriting_summary) + "\n" + status, onClick = {}) {
        when (state) {
            is HandwritingModels.State.Installed -> TextButton({ HandwritingModels.delete(model) }) { Text(stringResource(R.string.lychee_delete)) }
            is HandwritingModels.State.Downloading, is HandwritingModels.State.Unknown -> {}
            else -> TextButton({ HandwritingModels.download(context, model) }) { Text(stringResource(R.string.lychee_download)) }
        }
    }
}
