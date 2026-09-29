package dev.tidewall.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.tidewall.app
import dev.tidewall.autoconnect.AutoConnectService
import kotlinx.coroutines.launch

/** Starts the VPN and/or auto-connect after boot, when enabled in Settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        context.app.scope.launch {
            try {
                val s = context.app.settings.current()
                if (s.autoConnect) {
                    AutoConnectService.start(context)
                } else if (s.startOnBoot && !VpnLauncher.needsPermission(context) && s.selectedProfileId != null &&
                    intent.action == Intent.ACTION_BOOT_COMPLETED
                ) {
                    VpnLauncher.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
