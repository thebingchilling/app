package dev.tidewall

import dev.tidewall.autoconnect.AutoAction
import dev.tidewall.autoconnect.AutoConnectRules
import dev.tidewall.autoconnect.CurrentNetwork
import dev.tidewall.autoconnect.NetworkType
import dev.tidewall.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoConnectRulesTest {
    private val s = AppSettings(autoConnect = true, trustedSsids = setOf("Home"))

    @Test fun trustedWifiDisconnects() {
        assertEquals(AutoAction.DISCONNECT, AutoConnectRules.decide(CurrentNetwork(NetworkType.WIFI, "Home"), s))
        assertEquals(AutoAction.NONE, AutoConnectRules.decide(CurrentNetwork(NetworkType.WIFI, "Home"), s.copy(disconnectOnTrusted = false)))
    }

    @Test fun untrustedAndUnknownWifiConnect() {
        assertEquals(AutoAction.CONNECT, AutoConnectRules.decide(CurrentNetwork(NetworkType.WIFI, "Cafe"), s))
        assertEquals(AutoAction.CONNECT, AutoConnectRules.decide(CurrentNetwork(NetworkType.WIFI, null), s))
        assertEquals(AutoAction.NONE, AutoConnectRules.decide(CurrentNetwork(NetworkType.WIFI, "Cafe"), s.copy(connectOnUntrustedWifi = false)))
    }

    @Test fun mobileData() {
        assertEquals(AutoAction.CONNECT, AutoConnectRules.decide(CurrentNetwork(NetworkType.CELLULAR), s))
        assertEquals(AutoAction.NONE, AutoConnectRules.decide(CurrentNetwork(NetworkType.CELLULAR), s.copy(connectOnMobile = false)))
        assertEquals(AutoAction.NONE, AutoConnectRules.decide(CurrentNetwork(NetworkType.NONE), s))
    }

    @Test fun ssidCleaning() {
        assertEquals("Home", AutoConnectRules.cleanSsid("\"Home\""))
        assertNull(AutoConnectRules.cleanSsid("<unknown ssid>"))
        assertNull(AutoConnectRules.cleanSsid(null))
    }
}
