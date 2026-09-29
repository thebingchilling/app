package dev.tidewall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.tidewall.autoconnect.AutoConnectService
import dev.tidewall.core.Engine
import dev.tidewall.data.ProfileRepository
import dev.tidewall.data.SettingsRepository
import dev.tidewall.work.SubscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TidewallApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: SettingsRepository
        private set
    lateinit var profiles: ProfileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Engine.init(this)
        settings = SettingsRepository(this, scope)
        profiles = ProfileRepository(this)
        createChannels()
        SubscriptionWorker.schedule(this)

        scope.launch {
            settings.settings.map { it.logLevel }.distinctUntilChanged().collect { Engine.setLogLevel(it) }
        }
        scope.launch {
            settings.settings.map { it.autoConnect }.distinctUntilChanged().collect { enabled ->
                if (enabled) AutoConnectService.start(this@TidewallApp) else AutoConnectService.stop(this@TidewallApp)
            }
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VPN, "VPN status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Tidewall is connected"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AUTO, "Auto-connect", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shows while Tidewall watches networks for auto-connect"
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_VPN = "vpn"
        const val CHANNEL_AUTO = "auto_connect"
    }
}

val Context.app: TidewallApp get() = applicationContext as TidewallApp
