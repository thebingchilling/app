package dev.tidewall.vpn

import java.math.BigInteger
import java.net.InetAddress

/** An IP prefix such as 10.0.0.0/8, for VPN route calculations. */
data class IpPrefix(val address: InetAddress, val length: Int) {
    val bits: Int get() = address.address.size * 8
    override fun toString() = "${address.hostAddress}/$length"

    /** This prefix with the host bits cleared (VpnService rejects routes that have them set). */
    fun network(): IpPrefix {
        val raw = address.address.copyOf()
        for (i in raw.indices) {
            val keep = (length - i * 8).coerceIn(0, 8)
            raw[i] = (raw[i].toInt() and (0xFF shl (8 - keep))).toByte()
        }
        return IpPrefix(InetAddress.getByAddress(raw), length)
    }

    companion object {
        fun parse(cidr: String): IpPrefix {
            val (a, l) = cidr.trim().split('/').let { it[0] to it.getOrNull(1) }
            val addr = InetAddress.getByName(a)
            return IpPrefix(addr, l?.toInt() ?: (addr.address.size * 8))
        }
    }
}

object Routes {
    /** Private, link-local and multicast ranges kept off the tunnel when "Bypass LAN" is on. */
    val lan4 = listOf("10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4")
    val lan6 = listOf("fc00::/7", "fe80::/10", "ff00::/8")

    private fun toBig(a: InetAddress) = BigInteger(1, a.address)

    private fun toAddr(v: BigInteger, bytes: Int): InetAddress {
        val raw = v.toByteArray()
        val out = ByteArray(bytes)
        val src = if (raw.size > bytes) raw.copyOfRange(raw.size - bytes, raw.size) else raw
        System.arraycopy(src, 0, out, bytes - src.size, src.size)
        return InetAddress.getByAddress(out)
    }

    fun contains(outer: IpPrefix, inner: IpPrefix): Boolean {
        if (outer.bits != inner.bits || inner.length < outer.length) return false
        val shift = outer.bits - outer.length
        return toBig(outer.address).shiftRight(shift) == toBig(inner.address).shiftRight(shift)
    }

    /** Splits [prefix] into its two halves. */
    private fun halves(prefix: IpPrefix): Pair<IpPrefix, IpPrefix> {
        val len = prefix.length + 1
        val base = toBig(prefix.address)
        val bytes = prefix.address.address.size
        val high = base.setBit(prefix.bits - len)
        return IpPrefix(toAddr(base, bytes), len) to IpPrefix(toAddr(high, bytes), len)
    }

    /** Returns [from] minus every prefix in [exclude], as a minimal list of prefixes. */
    fun subtract(from: IpPrefix, exclude: List<IpPrefix>): List<IpPrefix> {
        val relevant = exclude.filter { it.bits == from.bits }
        if (relevant.any { contains(it, from) }) return emptyList()
        if (relevant.none { contains(from, it) }) return listOf(from)
        val (a, b) = halves(from)
        return subtract(a, relevant) + subtract(b, relevant)
    }

    fun defaultRoutes4(bypassLan: Boolean): List<IpPrefix> =
        if (bypassLan) subtract(IpPrefix.parse("0.0.0.0/0"), lan4.map(IpPrefix::parse)) else listOf(IpPrefix.parse("0.0.0.0/0"))

    fun defaultRoutes6(bypassLan: Boolean): List<IpPrefix> =
        if (bypassLan) subtract(IpPrefix.parse("::/0"), lan6.map(IpPrefix::parse)) else listOf(IpPrefix.parse("::/0"))

    /**
     * Expands WireGuard AllowedIPs into routes, replacing catch-all entries by
     * the LAN-excluding set when [bypassLan] is on.
     */
    fun fromAllowedIps(allowed: List<String>, bypassLan: Boolean): List<IpPrefix> = allowed.flatMap {
        val p = IpPrefix.parse(it)
        when {
            p.length == 0 && p.bits == 32 -> defaultRoutes4(bypassLan)
            p.length == 0 && p.bits == 128 -> defaultRoutes6(bypassLan)
            else -> listOf(p.network())
        }
    }.distinct()

    /**
     * Routes that keep a tunnel's own DNS servers and interface subnets inside
     * the tunnel. VPN providers put their DNS resolver on a private address
     * (10.2.0.1, 10.64.0.1, 172.16.0.1, ...), which "Bypass LAN" would
     * otherwise send to the phone's Wi-Fi, so every lookup fails even though
     * the tunnel itself is up. Only destinations the tunnel carries
     * ([allowed], its AllowedIPs) are added.
     */
    fun tunnelInternalRoutes(dns: List<String>, addresses: List<String>, allowed: List<String>): List<IpPrefix> {
        val allowedPrefixes = allowed.mapNotNull { runCatching { IpPrefix.parse(it) }.getOrNull() }
        val wanted = dns.mapNotNull { runCatching { IpPrefix.parse(it) }.getOrNull() } +
            addresses.mapNotNull { runCatching { IpPrefix.parse(it).network() }.getOrNull() }.filter { it.length < it.bits }
        return wanted.filter { w -> allowedPrefixes.any { contains(it, w) } }.distinct()
    }
}
