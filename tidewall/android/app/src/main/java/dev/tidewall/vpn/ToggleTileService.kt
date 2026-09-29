package dev.tidewall.vpn

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.tidewall.MainActivity
import dev.tidewall.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Quick Settings tile that connects/disconnects the selected profile. */
class ToggleTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(VpnStateHolder.state, app.profiles.profiles, app.settings.settings) { s, profiles, settings ->
                Triple(s, profiles.firstOrNull { it.id == settings.selectedProfileId }?.name, profiles.isEmpty())
            }.collect { (s, selected, empty) -> render(s, selected, empty) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    private fun render(s: VpnState, selected: String?, empty: Boolean) {
        val tile = qsTile ?: return
        tile.state = when {
            empty -> Tile.STATE_UNAVAILABLE
            s.active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "Tidewall"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (s.status) {
                VpnStatus.CONNECTING -> "Connecting…"
                VpnStatus.RECONNECTING -> "Reconnecting…"
                VpnStatus.STOPPING -> "Stopping…"
                VpnStatus.CONNECTED -> s.profileName
                VpnStatus.IDLE -> selected ?: "No profile"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        if (VpnStateHolder.state.value.active) {
            VpnLauncher.stop(this)
            return
        }
        if (VpnLauncher.needsPermission(this)) {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(MainActivity.ACTION_CONNECT)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
            return
        }
        VpnLauncher.start(this)
    }
}
