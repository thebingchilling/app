package app.bumboard

import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemeMonet

/**
 * BumBoard's defaults, applied once (from the settings app or the keyboard, whichever opens
 * first): Material 3 look — dynamic colour (Monet) following the system's light/dark mode,
 * bordered keys with rounded corners and ripples — and the toolbar visible, like Gboard.
 * Users can change all of these afterwards.
 */
object FirstRun {
    private const val DONE = "first_run_v1"

    @Synchronized
    fun apply() {
        val prefs = BumBoard.prefs
        if (prefs.getBoolean(DONE, false)) return
        runCatching {
            val theme = ThemeManager.prefs
            theme.followSystemDayNightTheme.setValue(true)
            theme.lightModeTheme.setValue(ThemeMonet.getLight())
            theme.darkModeTheme.setValue(ThemeMonet.getDark())
            theme.keyBorder.setValue(true)
            theme.keyRadius.setValue(12)
            theme.keyRippleEffect.setValue(true)
            AppPrefs.getInstance().keyboard.expandToolbarByDefault.setValue(true)
        }
        prefs.edit().putBoolean(DONE, true).apply()
    }
}
