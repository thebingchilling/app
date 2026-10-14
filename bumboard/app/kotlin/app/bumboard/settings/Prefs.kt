package app.bumboard.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.bumboard.BumBoard
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference

/** Compose state backed by BumBoard's SharedPreferences; writes notify the keyboard. */
@Composable
fun rememberBool(key: String, default: Boolean): MutableState<Boolean> {
    val prefs = BumBoard.prefs
    return remember(key) {
        object : MutableState<Boolean> {
            private val state = mutableStateOf(prefs.getBoolean(key, default))
            override var value: Boolean
                get() = state.value
                set(v) {
                    state.value = v
                    prefs.edit().putBoolean(key, v).apply()
                    BumBoard.onPrefsChanged()
                }
            override operator fun component1() = value
            override operator fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberString(key: String, default: String): MutableState<String> {
    val prefs = BumBoard.prefs
    return remember(key) {
        object : MutableState<String> {
            private val state = mutableStateOf(prefs.getString(key, null) ?: default)
            override var value: String
                get() = state.value
                set(v) {
                    state.value = v
                    prefs.edit().putString(key, v).apply()
                    BumBoard.onPrefsChanged()
                }
            override operator fun component1() = value
            override operator fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Compose state over one of upstream fcitx5-android's preferences (AppPrefs). */
@Composable
fun <T : Any> rememberManaged(pref: ManagedPreference<T>): MutableState<T> = remember(pref.key) {
    object : MutableState<T> {
        private val state = mutableStateOf(pref.getValue())
        override var value: T
            get() = state.value
            set(v) {
                state.value = v
                pref.setValue(v)
            }
        override operator fun component1() = value
        override operator fun component2(): (T) -> Unit = { value = it }
    }
}
