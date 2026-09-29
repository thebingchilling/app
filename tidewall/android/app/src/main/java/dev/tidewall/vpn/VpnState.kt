package dev.tidewall.vpn

import dev.tidewall.data.ProfileKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VpnStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, STOPPING }

data class VpnState(
    val status: VpnStatus = VpnStatus.IDLE,
    val profileId: String? = null,
    val profileName: String? = null,
    val kind: ProfileKind? = null,
    val since: Long = 0,
    val error: String? = null,
    val detail: String? = null,
) {
    val active: Boolean get() = status != VpnStatus.IDLE
}

/** Bytes/second rates and session totals. */
data class Traffic(val up: Long = 0, val down: Long = 0, val upTotal: Long = 0, val downTotal: Long = 0)

/** Process-wide VPN state shared by the service, UI, tile and auto-connect. */
object VpnStateHolder {
    private val _state = MutableStateFlow(VpnState())
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    fun set(transform: (VpnState) -> VpnState) = _state.update(transform)
    fun setTraffic(t: Traffic) { _traffic.value = t }
}
