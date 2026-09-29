package dev.tidewall.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tidewall.TidewallApp
import dev.tidewall.core.Engine
import dev.tidewall.data.AppSettings
import dev.tidewall.data.NeedsCredentialsException
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.data.RoutingMode
import dev.tidewall.libcore.Libcore
import dev.tidewall.vpn.VpnLauncher
import dev.tidewall.vpn.VpnStateHolder
import dev.tidewall.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An OpenVPN import waiting for the user's username/password. */
data class CredentialRequest(val text: String, val name: String, val existing: Profile? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TidewallApp
    val settings: StateFlow<AppSettings> = app.settings.settings
    val profiles: StateFlow<List<Profile>> = app.profiles.profiles
    val vpn = VpnStateHolder.state
    val traffic = VpnStateHolder.traffic

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _credentials = MutableStateFlow<CredentialRequest?>(null)
    val credentials: StateFlow<CredentialRequest?> = _credentials.asStateFlow()

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    private fun work(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
                success?.let(::toast)
            } catch (e: NeedsCredentialsException) {
                _credentials.value = CredentialRequest(e.text, e.suggestedName)
            } catch (e: Exception) {
                toast(e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    fun selectedProfile(): Profile? = app.profiles.get(settings.value.selectedProfileId)

    fun selectProfile(p: Profile) {
        viewModelScope.launch {
            app.settings.update { it.copy(selectedProfileId = p.id) }
            val s = vpn.value
            if (s.active && s.profileId != p.id) VpnLauncher.start(app, p.id)
        }
    }

    fun setMode(mode: RoutingMode) {
        updateSettings { it.copy(mode = mode) }
        if (vpn.value.status == VpnStatus.CONNECTED && vpn.value.kind == ProfileKind.CLASH) {
            runCatching { Libcore.setMode(mode.id) }
        }
    }

    fun connect() {
        if (profiles.value.isEmpty()) {
            toast("Add a profile first")
            return
        }
        VpnLauncher.start(app)
    }

    fun disconnect() = VpnLauncher.stop(app)

    // --- Import ----------------------------------------------------------------

    private suspend fun afterImport(p: Profile) {
        if (settings.value.selectedProfileId == null || app.profiles.get(settings.value.selectedProfileId) == null) {
            app.settings.update { it.copy(selectedProfileId = p.id) }
        }
        toast("Added ${p.name}")
    }

    fun importText(text: String, name: String? = null, fileName: String? = null) = work {
        afterImport(app.profiles.importText(text, name, fileName))
    }

    fun importUrl(url: String, name: String? = null) = work {
        afterImport(app.profiles.importUrl(url, name))
    }

    fun importUri(uri: Uri) = work {
        val (text, fileName) = withContext(Dispatchers.IO) {
            val resolver = app.contentResolver
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
            val body = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not read the file")
            if (body.size > 8 * 1024 * 1024) throw IllegalArgumentException("File is too large")
            String(body, Charsets.UTF_8) to name
        }
        afterImport(app.profiles.importText(text, null, fileName))
    }

    fun dismissCredentials() {
        _credentials.value = null
    }

    fun provideCredentials(user: String, pass: String) {
        val req = _credentials.value ?: return
        _credentials.value = null
        if (req.existing != null) {
            work("Saved login for ${req.existing.name}") { app.profiles.setCredentials(req.existing, user, pass) }
        } else {
            work { afterImport(app.profiles.importText(req.text, req.name, null, user, pass)) }
        }
    }

    fun editCredentials(p: Profile) {
        _credentials.value = CredentialRequest("", p.name, existing = p)
    }

    // --- Profile actions ----------------------------------------------------------

    fun updateProfile(p: Profile) = work("Updated ${p.name}") {
        val updated = app.profiles.update(p)
        val s = vpn.value
        if (s.status == VpnStatus.CONNECTED && s.profileId == p.id && Libcore.isProxyRunning()) {
            withContext(Dispatchers.IO) {
                Libcore.reloadProfile(app.profiles.content(updated), settings.value.engineOptionsJson())
            }
        }
    }

    fun deleteProfile(p: Profile) = work("Deleted ${p.name}") {
        if (vpn.value.profileId == p.id && vpn.value.active) VpnLauncher.stop(app)
        app.profiles.delete(p)
        if (settings.value.selectedProfileId == p.id) {
            app.settings.update { it.copy(selectedProfileId = app.profiles.profiles.value.firstOrNull()?.id) }
        }
    }

    fun renameProfile(p: Profile, name: String) = work { app.profiles.rename(p, name) }

    fun setUpdateInterval(p: Profile, hours: Int) = work { app.profiles.setUpdateInterval(p, hours) }

    fun convertToProxy(p: Profile) = work {
        val np = app.profiles.convertToProxy(p)
        toast("Created ${np.name}")
    }

    suspend fun content(p: Profile): String = app.profiles.content(p)

    /** Validates and saves edited profile text; returns an error message or null. */
    suspend fun saveContent(p: Profile, text: String): String? = try {
        app.profiles.saveContent(p, text)
        null
    } catch (e: Exception) {
        e.message ?: "Invalid profile"
    }

    /** Adds one proxy (as a JSON/YAML mapping) to [target] or a new profile. */
    suspend fun addProxy(target: Profile?, proxy: String, newProfileName: String): String? = try {
        val p = app.profiles.addProxy(target, proxy, newProfileName)
        if (settings.value.selectedProfileId == null) app.settings.update { it.copy(selectedProfileId = p.id) }
        null
    } catch (e: Exception) {
        e.message ?: "Invalid proxy"
    }

    val engineVersion: String get() = runCatching { Engine.version }.getOrDefault("?")
}
