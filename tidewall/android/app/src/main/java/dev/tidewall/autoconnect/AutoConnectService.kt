package dev.tidewall.autoconnect

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.tidewall.MainActivity
import dev.tidewall.R
import dev.tidewall.TidewallApp
import dev.tidewall.app
import dev.tidewall.core.LogBuffer
import dev.tidewall.vpn.VpnLauncher
import dev.tidewall.vpn.VpnStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that watches the physical network and connects or
 * disconnects the VPN according to the auto-connect rules (trusted Wi-Fi,
 * untrusted Wi-Fi, mobile data).
 */
class AutoConnectService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val networks = ConcurrentHashMap<Network, NetworkCapabilities>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var evaluateJob: Job? = null
    private var lastAction: AutoAction? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = NotificationCompat.Builder(this, TidewallApp.CHANNEL_AUTO)
            .setSmallIcon(R.drawable.ic_stat_tidewall)
            .setContentTitle("Auto-connect is on")
            .setContentText("Tidewall connects on untrusted networks")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 2, n, type)
        register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleEvaluate()
        return START_STICKY
    }

    private fun register() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val cb = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
                override fun onLost(network: Network) = onLostNetwork(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
                override fun onLost(network: Network) = onLostNetwork(network)
            }
        }
        cm.registerNetworkCallback(request, cb)
        callback = cb
    }

    private fun onCaps(network: Network, caps: NetworkCapabilities) {
        networks[network] = caps
        scheduleEvaluate()
    }

    private fun onLostNetwork(network: Network) {
        networks.remove(network)
        scheduleEvaluate()
    }

    @SuppressLint("MissingPermission")
    private fun ssidOf(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= 31) {
            (caps.transportInfo as? WifiInfo)?.ssid?.let { return AutoConnectRules.cleanSsid(it) }
        }
        @Suppress("DEPRECATION")
        return AutoConnectRules.cleanSsid(getSystemService(WifiManager::class.java)?.connectionInfo?.ssid)
    }

    private fun current(): CurrentNetwork {
        val all = networks.values
        all.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }?.let {
            return CurrentNetwork(NetworkType.WIFI, ssidOf(it))
        }
        if (all.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }) return CurrentNetwork(NetworkType.CELLULAR)
        if (all.any { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }) return CurrentNetwork(NetworkType.ETHERNET)
        return CurrentNetwork(NetworkType.NONE)
    }

    private fun scheduleEvaluate() {
        evaluateJob?.cancel()
        evaluateJob = scope.launch {
            delay(2000) // let network transitions settle
            val net = current()
            _network.value = net
            val settings = app.settings.current()
            if (!settings.autoConnect) return@launch
            val action = AutoConnectRules.decide(net, settings)
            if (action == lastAction) return@launch
            lastAction = action
            val active = VpnStateHolder.state.value.active
            when (action) {
                AutoAction.CONNECT -> if (!active) {
                    if (VpnLauncher.needsPermission(this@AutoConnectService)) {
                        LogBuffer.add("warning", "Auto-connect: open Tidewall once to grant the VPN permission")
                    } else {
                        LogBuffer.add("info", "Auto-connect: connecting on ${describe(net)}")
                        VpnLauncher.start(this@AutoConnectService)
                    }
                }
                AutoAction.DISCONNECT -> if (active) {
                    LogBuffer.add("info", "Auto-connect: disconnecting on trusted network ${net.ssid}")
                    VpnLauncher.stop(this@AutoConnectService)
                }
                AutoAction.NONE -> Unit
            }
        }
    }

    private fun describe(n: CurrentNetwork) = when (n.type) {
        NetworkType.WIFI -> "Wi-Fi ${n.ssid ?: "(unknown name)"}"
        NetworkType.CELLULAR -> "mobile data"
        NetworkType.ETHERNET -> "Ethernet"
        NetworkType.NONE -> "no network"
    }

    override fun onDestroy() {
        callback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _network = MutableStateFlow<CurrentNetwork?>(null)

        /** The last physical network seen, for the auto-connect settings screen. */
        val network: StateFlow<CurrentNetwork?> = _network.asStateFlow()

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AutoConnectService::class.java)) }
                .onFailure { LogBuffer.add("warning", "Auto-connect could not start: ${it.message}") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoConnectService::class.java))
        }
    }
}
