package dev.tidewall.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.tidewall.MainActivity
import dev.tidewall.R
import dev.tidewall.TidewallApp
import dev.tidewall.app
import dev.tidewall.core.Engine
import dev.tidewall.core.LogBuffer
import dev.tidewall.data.AppSettings
import dev.tidewall.data.PerAppMode
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.libcore.Libcore
import dev.tidewall.ui.common.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The VPN. Runs one of three engines on the TUN interface:
 * mihomo (Proxy profiles), AmneziaWG (Direct WireGuard) or OpenVPN 3 (Direct OpenVPN).
 */
class TidewallVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var statsJob: Job? = null
    private var engine: ProfileKind? = null
    private var openVpn: OpenVpnSession? = null
    private var lastTotals = 0L to 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { stopVpn() }
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START from the app, or a null/SERVICE_INTERFACE intent from always-on VPN.
                goForeground(buildNotification("Connecting…", null))
                val requested = intent?.getStringExtra(EXTRA_PROFILE_ID)
                scope.launch { startVpn(requested) }
            }
        }
        return START_STICKY
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private suspend fun startVpn(requestedId: String?) = lock.withLock {
        val settings = app.settings.current()
        val profile = app.profiles.get(requestedId ?: settings.selectedProfileId)
            ?: app.profiles.profiles.value.firstOrNull()
        if (profile == null) {
            fail("No profile selected. Add one on the Profiles tab.")
            return@withLock
        }
        if (requestedId != null && requestedId != settings.selectedProfileId) {
            app.settings.update { it.copy(selectedProfileId = requestedId) }
        }
        stopEngines()
        VpnStateHolder.set {
            VpnState(VpnStatus.CONNECTING, profile.id, profile.name, profile.kind, System.currentTimeMillis())
        }
        updateNotification(profile.name, "Connecting…")
        try {
            val text = app.profiles.content(profile)
            when (profile.kind) {
                ProfileKind.CLASH -> startProxy(profile, text, settings)
                ProfileKind.WIREGUARD -> startWireGuard(text, settings, profile)
                ProfileKind.OPENVPN -> startOpenVpn(profile, text, settings)
            }
            engine = profile.kind
            if (profile.kind != ProfileKind.OPENVPN) markConnected(null)
            startStats(profile)
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            LogBuffer.add("error", "Tidewall: ${e.message}")
            stopEngines()
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Builder with the settings shared by every engine. */
    fun newBuilder(settings: AppSettings, session: String): Builder {
        val b = Builder()
            .setSession(session)
            .setConfigureIntent(mainActivityIntent())
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        val own = packageName
        when (settings.perAppMode) {
            PerAppMode.INCLUDE -> {
                val pkgs = settings.perAppPackages - own
                if (pkgs.isEmpty()) b.addDisallowedApplication(own)
                pkgs.forEach { runCatching { b.addAllowedApplication(it) } }
            }
            PerAppMode.EXCLUDE, PerAppMode.OFF -> {
                // Our own traffic (proxy servers, WireGuard/OpenVPN sockets) must bypass the tunnel.
                b.addDisallowedApplication(own)
                if (settings.perAppMode == PerAppMode.EXCLUDE) {
                    (settings.perAppPackages - own).forEach {
                        try {
                            b.addDisallowedApplication(it)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                }
            }
        }
        return b
    }

    private fun startProxy(profile: Profile, yaml: String, settings: AppSettings) {
        val b = newBuilder(settings, profile.name)
            .addAddress(Libcore.TunAddress4, Libcore.TunPrefix4.toInt())
            .addDnsServer(Libcore.TunDNS4)
            .setMtu(settings.mtu)
        // mihomo's fake-ip range (198.18.0.0/16) is public space, so it stays routed with LAN bypass on.
        Routes.defaultRoutes4(settings.bypassLan).forEach { b.addRoute(it.address, it.length) }
        if (settings.ipv6) {
            b.addAddress(Libcore.TunAddress6, Libcore.TunPrefix6.toInt())
            Routes.defaultRoutes6(settings.bypassLan).forEach { b.addRoute(it.address, it.length) }
        }
        val pfd = b.establish() ?: throw IllegalStateException("VPN permission was revoked")
        Libcore.startProxy(pfd.detachFd().toLong(), yaml, settings.engineOptionsJson())
    }

    private fun startWireGuard(conf: String, settings: AppSettings, profile: Profile) {
        val info = Engine.inspectWireGuard(conf)
        // Go cannot use Android's resolver, so endpoints are resolved here, before the tunnel exists.
        val resolved = info.endpoints.orEmpty().associateWith { host ->
            val all = InetAddress.getAllByName(host)
            (all.firstOrNull { it is Inet4Address } ?: all.first()).hostAddress!!
        }
        val b = newBuilder(settings, profile.name).setMtu(info.mtu)
        info.addresses.orEmpty().forEach { val p = IpPrefix.parse(it); b.addAddress(p.address, p.length) }
        info.dns.orEmpty().forEach { b.addDnsServer(it) }
        info.search.orEmpty().forEach { b.addSearchDomain(it) }
        Routes.fromAllowedIps(info.routes.orEmpty(), settings.bypassLan).forEach { b.addRoute(it.address, it.length) }
        val pfd = b.establish() ?: throw IllegalStateException("VPN permission was revoked")
        val json = JsonObject(resolved.mapValues { JsonPrimitive(it.value) }).toString()
        Libcore.startWireGuard(pfd.detachFd().toLong(), conf, json)
    }

    private fun startOpenVpn(profile: Profile, text: String, settings: AppSettings) {
        dev.tidewall.ovpn3.OpenVpn3.load()
        val session = OpenVpnSession(this, profile, text, settings, object : OpenVpnSession.Listener {
            override fun onConnected(info: String) = markConnected(info)
            override fun onReconnecting() {
                VpnStateHolder.set { it.copy(status = VpnStatus.RECONNECTING) }
                updateNotification(profile.name, "Reconnecting…")
            }
            override fun onFatal(message: String) {
                scope.launch { lock.withLock { stopEngines() }; fail(message) }
            }
            override fun onDisconnected() {
                if (openVpn != null) scope.launch { stopVpn() }
            }
        })
        openVpn = session
        session.start()
    }

    private fun markConnected(detail: String?) {
        VpnStateHolder.set { it.copy(status = VpnStatus.CONNECTED, error = null, detail = detail, since = System.currentTimeMillis()) }
    }

    private fun startStats(profile: Profile) {
        statsJob?.cancel()
        lastTotals = 0L to 0L
        statsJob = scope.launch {
            while (isActive) {
                val t = when (engine) {
                    ProfileKind.CLASH -> Libcore.getTraffic().let { Traffic(it.up, it.down, it.upTotal, it.downTotal) }
                    ProfileKind.WIREGUARD -> Libcore.wireGuardStats().split(',').let { totalsToTraffic(it[1].toLong(), it[0].toLong()) }
                    ProfileKind.OPENVPN -> openVpn?.totals()?.let { (rx, tx) -> totalsToTraffic(tx, rx) } ?: Traffic()
                    null -> Traffic()
                }
                VpnStateHolder.setTraffic(t)
                if (VpnStateHolder.state.value.status == VpnStatus.CONNECTED) {
                    updateNotification(profile.name, "↑ ${formatSpeed(t.up)}   ↓ ${formatSpeed(t.down)}")
                }
                delay(1000)
            }
        }
    }

    private fun totalsToTraffic(upTotal: Long, downTotal: Long): Traffic {
        val (lu, ld) = lastTotals
        lastTotals = upTotal to downTotal
        return Traffic((upTotal - lu).coerceAtLeast(0), (downTotal - ld).coerceAtLeast(0), upTotal, downTotal)
    }

    private fun stopEngines() {
        statsJob?.cancel()
        statsJob = null
        when (engine) {
            ProfileKind.CLASH -> Libcore.stopProxy()
            ProfileKind.WIREGUARD -> Libcore.stopWireGuard()
            ProfileKind.OPENVPN -> Unit
            null -> Unit
        }
        openVpn?.let { s ->
            openVpn = null
            s.shutdown()
        }
        engine = null
        VpnStateHolder.setTraffic(Traffic())
    }

    suspend fun stopVpn() {
        lock.withLock {
            VpnStateHolder.set { it.copy(status = VpnStatus.STOPPING) }
            stopEngines()
            VpnStateHolder.set { VpnState(error = it.error) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        VpnStateHolder.set { VpnState(error = message) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        scope.launch { stopVpn() }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (engine != null || openVpn != null) {
            stopEngines()
            VpnStateHolder.set { VpnState() }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(title: String, text: String?): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TidewallVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, TidewallApp.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_stat_tidewall)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(mainActivityIntent())
            .addAction(0, "Disconnect", stop)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(title, text)) }
    }

    companion object {
        private const val TAG = "TidewallVpn"
        const val ACTION_START = "dev.tidewall.START"
        const val ACTION_STOP = "dev.tidewall.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: TidewallVpnService? = null
            private set
    }
}

/** Starting and stopping the VPN from the UI, tile, boot receiver and auto-connect. */
object VpnLauncher {
    /** True when the user still has to grant the VPN permission (needs an Activity). */
    fun needsPermission(context: Context): Boolean = VpnService.prepare(context) != null

    fun start(context: Context, profileId: String? = null) {
        val i = Intent(context, TidewallVpnService::class.java).setAction(TidewallVpnService.ACTION_START)
        if (profileId != null) i.putExtra(TidewallVpnService.EXTRA_PROFILE_ID, profileId)
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) {
        val service = TidewallVpnService.instance
        if (service != null) {
            context.app.scope.launch { service.stopVpn() }
        } else {
            VpnStateHolder.set { VpnState() }
        }
    }
}
