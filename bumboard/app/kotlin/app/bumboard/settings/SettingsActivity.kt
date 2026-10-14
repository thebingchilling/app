@file:OptIn(ExperimentalMaterial3Api::class)

package app.bumboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bumboard.BumBoard
import app.bumboard.BumBoard.Keys
import app.bumboard.core.Annotator
import app.bumboard.core.CantoneseRomanization
import app.bumboard.core.Lang
import app.bumboard.core.Resolved
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.keyboard.SwipeSymbolDirection
import org.fcitx.fcitx5.android.input.popup.EmojiModifier
import org.fcitx.fcitx5.android.ui.main.MainActivity

/** BumBoard's settings app (launcher and the keyboard's settings entry). */
class SettingsActivity : ComponentActivity() {

    private val resumed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        BumBoard.openDictionary()
        setContent {
            BumTheme { SettingsApp(resumed.intValue) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed.intValue++
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) resumed.intValue++
    }
}

enum class Screen(val title: String) {
    Home("BumBoard"),
    Readings("Readings & meanings"),
    Typing("Languages & typing"),
    Layout("Layout & size"),
    Feedback("Sound & vibration"),
    About("About & licences"),
}

@Composable
private fun SettingsApp(resumeTick: Int) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != Screen.Home) {
                        IconButton(onClick = { screen = Screen.Home }) {
                            Icon(painterResource(R.drawable.ic_baseline_arrow_back_24), contentDescription = "Back")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column {
                        when (s) {
                            Screen.Home -> HomeScreen(resumeTick) { screen = it }
                            Screen.Readings -> ReadingsScreen()
                            Screen.Typing -> TypingScreen()
                            Screen.Layout -> LayoutScreen()
                            Screen.Feedback -> FeedbackScreen()
                            Screen.About -> AboutScreen()
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home

private fun Context.keyboardEnabled(): Boolean =
    getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == packageName }

private fun Context.keyboardSelected(): Boolean =
    Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.startsWith("$packageName/") == true

@Composable
private fun HomeScreen(resumeTick: Int, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val enabled = remember(resumeTick) { ctx.keyboardEnabled() }
    val selected = remember(resumeTick) { ctx.keyboardSelected() }

    if (!enabled || !selected) {
        SetupCard(enabled, selected)
    } else {
        var text by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Try BumBoard here") },
            placeholder = { Text("neihou · nihao · EN→中: fridge") },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    Group("Learning") {
        row { NavRow(it, "Readings & meanings", "Jyutping, Yale, pinyin and English under candidates", R.drawable.ms_school) { open(Screen.Readings) } }
        row { NavRow(it, "Languages & typing", "Cantonese, Mandarin, English; Jyutping or Yale input", R.drawable.ms_translate) { open(Screen.Typing) } }
    }
    Group("Keyboard") {
        row { NavRow(it, "Layout & size", "Height, padding, keys, emoji", R.drawable.ms_resize) { open(Screen.Layout) } }
        row { NavRow(it, "Sound & vibration", "Key sounds, haptics and their strength", R.drawable.ms_vibration) { open(Screen.Feedback) } }
        row {
            NavRow(it, "Themes & more", "Themes, clipboard, candidates and advanced options", R.drawable.ms_palette) {
                ctx.startActivity(Intent(ctx, MainActivity::class.java))
            }
        }
    }
    Group {
        row { NavRow(it, "About & licences", null, R.drawable.ms_info) { open(Screen.About) } }
    }
}

@Composable
private fun SetupCard(enabled: Boolean, selected: Boolean) {
    val ctx = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFF0F6E5F), modifier = Modifier.size(48.dp)) {
                    Image(painterResource(R.drawable.bumboard_icon_foreground), contentDescription = null)
                }
                Spacer(Modifier.width(16.dp))
                Text("Set up BumBoard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Readings and meanings under every Chinese candidate, for Cantonese and Mandarin learners.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!enabled) {
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("1. Turn on BumBoard")
                    }
                }
                FilledTonalButton(
                    enabled = enabled,
                    onClick = { ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (enabled) "Switch to BumBoard" else "2. Switch to BumBoard") }
            }
        }
    }
}

// ---------------------------------------------------------------- readings

private val samples = listOf(
    Triple("你好", Resolved(Lang.Cantonese, "nei5 hou2", "ni3 hao3", "hello", emptyList(), false),
        Resolved(Lang.Mandarin, "ni3 hao3", "nei5 hou2", "hello", emptyList(), false)),
    Triple("雪櫃", Resolved(Lang.Cantonese, "syut3 gwai6", "xue3 gui4", "fridge", emptyList(), false),
        Resolved(Lang.Mandarin, "bing1 xiang1", "bing1 soeng1", "refrigerator", emptyList(), false)),
    Triple("學習", Resolved(Lang.Cantonese, "hok6 zaap6", "xue2 xi2", "to study", emptyList(), false),
        Resolved(Lang.Mandarin, "xue2 xi2", "hok6 zaap6", "to learn", emptyList(), false)),
)

@Composable
private fun ReadingsScreen() {
    val display = rememberString(Keys.CANTONESE_DISPLAY, CantoneseRomanization.Jyutping.name)
    val yaleMarks = rememberBool(Keys.YALE_TONE_MARKS, true)
    val pinyinMarks = rememberBool(Keys.PINYIN_TONE_MARKS, true)
    val other = rememberBool(Keys.SHOW_OTHER_LANGUAGE, false)
    val meaning = rememberBool(Keys.SHOW_MEANING, true)
    val prefs = BumBoard.displayPrefs().copy(
        cantonese = runCatching { CantoneseRomanization.valueOf(display.value) }.getOrDefault(CantoneseRomanization.Jyutping),
        yaleToneMarks = yaleMarks.value, pinyinToneMarks = pinyinMarks.value,
        showOtherLanguage = other.value, showMeaning = meaning.value,
    )

    SamplePreview("Cantonese", samples.map { it.first to it.second }, prefs)
    SamplePreview("Mandarin", listOf("你好" to samples[0].third, "冰箱" to samples[1].third, "学习" to samples[2].third), prefs)

    Group("Cantonese") {
        row {
            ChoiceRow(it, "Show", null,
                listOf("Jyutping" to "Jyutping", "Yale" to "Yale", "Both" to "Both"),
                display.value) { v -> display.value = v }
        }
        row { SwitchRow(it, "Yale with tone marks", "néih hóu instead of nei5 hou2", yaleMarks.value) { v -> yaleMarks.value = v } }
    }
    Group("Mandarin") {
        row { SwitchRow(it, "Pinyin with tone marks", "nǐ hǎo instead of ni3 hao3", pinyinMarks.value) { v -> pinyinMarks.value = v } }
    }
    Group("Both") {
        row { SwitchRow(it, "Show the other language too", "Pinyin under Cantonese, Jyutping under Mandarin", other.value) { v -> other.value = v } }
        row { SwitchRow(it, "English meaning", "A short meaning under each candidate; long-press for the full entry", meaning.value) { v -> meaning.value = v } }
    }
    Group {
        row { TextRow(it, "Bar height", "Changes to the number of lines apply the next time the keyboard opens.") }
    }
}

@Composable
private fun SamplePreview(title: String, items: List<Pair<String, Resolved>>, prefs: app.bumboard.core.DisplayPrefs) {
    Group(title) {
        row { shape ->
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                ) {
                    items.forEach { (word, r) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(word, style = MaterialTheme.typography.headlineSmall)
                            Annotator.lines(r, prefs).forEach { line ->
                                Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                            if (prefs.showMeaning && r.meaning != null) {
                                Text(r.meaning!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- typing

@Composable
private fun TypingScreen() {
    val yaleInput = rememberBool(Keys.YALE_INPUT, false)
    Group("Cantonese input") {
        row {
            ChoiceRow(it, "Type Cantonese in", "Tones are optional; type them as digits (nei5) or, in Yale, with h for low tones (yuht)",
                listOf(false to "Jyutping", true to "Yale"), yaleInput.value) { v -> yaleInput.value = v }
        }
    }
    Group("Switching") {
        row { TextRow(it, "Language button", "The 粵 / 普 / EN button on the toolbar cycles Cantonese, Mandarin and English.") }
        row { TextRow(it, "EN→中", "Turn it on and type an English word (fridge, apple, to eat) to get Chinese words with that meaning.") }
        row { TextRow(it, "繁 / 简", "Switches between Traditional and Simplified characters. Cantonese starts in Traditional, Mandarin in Simplified.") }
    }
    val keyboard = AppPrefs.getInstance().keyboard
    val langKey = rememberManaged(keyboard.showLangSwitchKey)
    Group("Keys") {
        row { SwitchRow(it, "Language switch key", "Show the globe key next to the space bar", langKey.value) { v -> langKey.value = v } }
    }
}

// ---------------------------------------------------------------- layout

@Composable
private fun LayoutScreen() {
    val k = AppPrefs.getInstance().keyboard
    val height = rememberManaged(k.keyboardHeightPercent)
    val heightLand = rememberManaged(k.keyboardHeightPercentLandscape)
    val side = rememberManaged(k.keyboardSidePadding)
    val bottom = rememberManaged(k.keyboardBottomPadding)
    val longPress = rememberManaged(k.longPressDelay)
    val swipe = rememberManaged(k.swipeSymbolDirection)
    val popup = rememberManaged(k.popupOnKeyPress)
    val numRow = rememberManaged(k.toolbarNumRowOnPassword)
    val skin = rememberManaged(AppPrefs.getInstance().symbols.defaultEmojiSkinTone)

    Group("Size") {
        row { SliderRow(it, "Height (portrait)", height.value, 10..90, format = { v -> "$v%" }) { v -> height.value = v } }
        row { SliderRow(it, "Height (landscape)", heightLand.value, 10..90, format = { v -> "$v%" }) { v -> heightLand.value = v } }
        row { SliderRow(it, "Side padding", side.value, 0..120, format = { v -> "$v dp" }) { v -> side.value = v } }
        row { SliderRow(it, "Bottom padding", bottom.value, 0..60, format = { v -> "$v dp" }) { v -> bottom.value = v } }
    }
    Group("Keys") {
        row { SwitchRow(it, "Key preview", "Show a large letter while pressing a key", popup.value) { v -> popup.value = v } }
        row { SliderRow(it, "Long-press delay", longPress.value, 100..700, step = 10, format = { v -> "$v ms" }) { v -> longPress.value = v } }
        row {
            ChoiceRow(it, "Swipe on a key for its symbol", null,
                listOf(SwipeSymbolDirection.Up to "Up", SwipeSymbolDirection.Down to "Down", SwipeSymbolDirection.Disabled to "Off"),
                swipe.value) { v -> swipe.value = v }
        }
        row { SwitchRow(it, "Number row on password fields", null, numRow.value) { v -> numRow.value = v } }
    }
    Group("Emoji") {
        row {
            ChoiceRow(it, "Default skin tone", null,
                listOf(
                    EmojiModifier.SkinTone.Default to "👋", EmojiModifier.SkinTone.Type_1_2 to "👋🏻",
                    EmojiModifier.SkinTone.Type_3 to "👋🏼", EmojiModifier.SkinTone.Type_4 to "👋🏽",
                    EmojiModifier.SkinTone.Type_5 to "👋🏾", EmojiModifier.SkinTone.Type_6 to "👋🏿",
                ),
                skin.value) { v -> skin.value = v }
        }
    }
}

// ---------------------------------------------------------------- sound & vibration

@Composable
private fun FeedbackScreen() {
    val k = AppPrefs.getInstance().keyboard
    val haptic = rememberManaged(k.hapticOnKeyPress)
    val hapticMs = rememberManaged(k.buttonPressVibrationMilliseconds)
    val hapticLongMs = rememberManaged(k.buttonLongPressVibrationMilliseconds)
    val hapticUp = rememberManaged(k.hapticOnKeyUp)
    val sound = rememberManaged(k.soundOnKeyPress)
    val volume = rememberManaged(k.soundOnKeyPressVolume)
    val modes = listOf(InputFeedbackMode.FollowingSystem to "System", InputFeedbackMode.Enabled to "On", InputFeedbackMode.Disabled to "Off")

    Group("Vibration") {
        row { ChoiceRow(it, "Vibrate on key press", null, modes, haptic.value) { v -> haptic.value = v } }
        if (haptic.value != InputFeedbackMode.Disabled) {
            row { SliderRow(it, "Strength (key press)", hapticMs.value, 0..100, format = { v -> if (v == 0) "System" else "$v ms" }) { v -> hapticMs.value = v } }
            row { SliderRow(it, "Strength (long press)", hapticLongMs.value, 0..100, format = { v -> if (v == 0) "System" else "$v ms" }) { v -> hapticLongMs.value = v } }
            row { SwitchRow(it, "Also vibrate on key release", null, hapticUp.value) { v -> hapticUp.value = v } }
        }
    }
    Group("Sound") {
        row { ChoiceRow(it, "Sound on key press", null, modes, sound.value) { v -> sound.value = v } }
        if (sound.value != InputFeedbackMode.Disabled) {
            row { SliderRow(it, "Volume", volume.value, 0..100, format = { v -> if (v == 0) "System" else "$v%" }) { v -> volume.value = v } }
        }
    }
}

// ---------------------------------------------------------------- about

@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    val dataLicences = remember {
        runCatching { ctx.assets.open("bumboard/data-licenses.txt").bufferedReader().readText() }.getOrDefault("")
    }
    Group {
        row { TextRow(it, "BumBoard ${BuildConfig.VERSION_NAME}", "A keyboard for Cantonese and Mandarin learners. Placeholder name.") }
    }
    Group("Open source") {
        row {
            TextRow(it, "Based on Fcitx5 for Android",
                "BumBoard is a modified version of Fcitx5 for Android (LGPL-2.1-or-later), with Rime (BSD-3-Clause) " +
                    "and OpenCC (Apache-2.0). The source of the modified Fcitx5 for Android parts is available from the developer.")
        }
        row {
            NavRow(it, "Open-source licences", "Libraries used by the app", R.drawable.ms_info) {
                ctx.startActivity(Intent(ctx, MainActivity::class.java))
            }
        }
    }
    Group("Dictionary data") {
        row { TextRow(it, "Sources and licences", dataLicences) }
    }
}
