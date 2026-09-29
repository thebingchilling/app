package dev.tidewall

import dev.tidewall.vpn.IpPrefix
import dev.tidewall.vpn.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.net.InetAddress

class RoutesTest {
    private fun contains(p: IpPrefix, ip: String): Boolean {
        val a = BigInteger(1, InetAddress.getByName(ip).address)
        val b = BigInteger(1, p.address.address)
        val shift = p.bits - p.length
        return p.bits == InetAddress.getByName(ip).address.size * 8 && a.shiftRight(shift) == b.shiftRight(shift)
    }

    private fun covered(routes: List<IpPrefix>, ip: String) = routes.any { contains(it, ip) }

    @Test fun bypassLanExcludesPrivateRangesOnly() {
        val r = Routes.defaultRoutes4(bypassLan = true)
        listOf("10.1.2.3", "192.168.1.1", "172.16.0.5", "172.31.255.255", "127.0.0.1", "169.254.1.1", "224.0.0.251").forEach {
            assertFalse("$it should bypass the VPN", covered(r, it))
        }
        listOf("8.8.8.8", "1.1.1.1", "172.32.0.1", "198.18.0.2", "203.0.113.9", "11.0.0.1").forEach {
            assertTrue("$it should use the VPN", covered(r, it))
        }
    }

    @Test fun routesDoNotOverlap() {
        val r = Routes.defaultRoutes4(bypassLan = true)
        // Sum of sizes = 2^32 minus the excluded space.
        val total = r.sumOf { BigInteger.ONE.shiftLeft(32 - it.length) }
        val excluded = listOf(8, 10, 8, 16, 12, 16, 4, 4).sumOf { BigInteger.ONE.shiftLeft(32 - it) }
        assertEquals(BigInteger.ONE.shiftLeft(32) - excluded, total)
    }

    @Test fun noBypassIsDefaultRoute() {
        assertEquals(listOf(IpPrefix.parse("0.0.0.0/0")), Routes.defaultRoutes4(false))
        assertEquals(listOf(IpPrefix.parse("::/0")), Routes.defaultRoutes6(false))
    }

    @Test fun ipv6Bypass() {
        val r = Routes.defaultRoutes6(true)
        assertFalse(covered(r, "fe80::1"))
        assertFalse(covered(r, "fd00::1"))
        assertTrue(covered(r, "2001:4860:4860::8888"))
    }

    @Test fun allowedIpsExpansion() {
        val r = Routes.fromAllowedIps(listOf("0.0.0.0/0", "10.8.0.0/24"), bypassLan = true)
        assertTrue(r.contains(IpPrefix.parse("10.8.0.0/24")))
        assertFalse(r.contains(IpPrefix.parse("0.0.0.0/0")))
        assertEquals(listOf(IpPrefix.parse("0.0.0.0/0")), Routes.fromAllowedIps(listOf("0.0.0.0/0"), false))
    }

    // A WireGuard provider DNS on a private address must stay in the tunnel with Bypass LAN on.
    @Test fun providerDnsStaysInTunnel() {
        val allowed = listOf("0.0.0.0/0", "::/0")
        val routes = Routes.fromAllowedIps(allowed, bypassLan = true) +
            Routes.tunnelInternalRoutes(listOf("10.2.0.1", "fd00::1"), listOf("10.2.0.2/32", "10.8.0.5/24"), allowed)
        assertTrue(covered(routes, "10.2.0.1"))
        assertTrue(covered(routes, "fd00::1"))
        assertTrue("interface subnet", covered(routes, "10.8.0.1"))
        assertFalse("rest of the LAN still bypasses", covered(routes, "192.168.1.1"))
        assertFalse("rest of the LAN still bypasses", covered(routes, "10.9.0.1"))
        Routes.tunnelInternalRoutes(listOf("10.2.0.1"), listOf("10.8.0.5/24"), allowed).forEach {
            assertEquals("host bits must be clear for VpnService", it.network(), it)
        }
    }

    // Split tunnels: a DNS server outside AllowedIPs is not pulled into the tunnel.
    @Test fun dnsOutsideAllowedIpsIsLeftAlone() {
        val extra = Routes.tunnelInternalRoutes(listOf("1.1.1.1", "10.0.0.1"), emptyList(), listOf("10.0.0.0/24"))
        assertEquals(listOf(IpPrefix.parse("10.0.0.1/32")), extra)
    }
}
