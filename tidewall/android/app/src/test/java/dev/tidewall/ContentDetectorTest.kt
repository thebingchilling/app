package dev.tidewall

import dev.tidewall.data.ContentDetector
import dev.tidewall.data.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class ContentDetectorTest {
    @Test fun detectsClashYaml() {
        assertEquals(ContentType.CLASH_YAML, ContentDetector.detect("mixed-port: 7890\nproxies:\n  - name: a\n"))
        assertEquals(ContentType.CLASH_YAML, ContentDetector.detect("proxy-providers:\n  sub:\n    type: http\n"))
    }

    @Test fun detectsLinksAndBase64Subscriptions() {
        val links = "ss://cmM0LW1kNTpwdw@1.2.3.4:8388#rc4\nvless://id@host:443?security=reality#x"
        assertEquals(ContentType.LINKS, ContentDetector.detect(links))
        val b64 = Base64.getEncoder().encodeToString(links.toByteArray())
        assertEquals(ContentType.LINKS, ContentDetector.detect(b64))
        assertEquals(links, ContentDetector.decodeBase64(b64))
    }

    @Test fun detectsOpenVpnAndWireGuard() {
        assertEquals(ContentType.OPENVPN, ContentDetector.detect("client\ndev tun\nremote vpn.example.com 1194\n<ca>\n</ca>"))
        assertEquals(ContentType.OPENVPN, ContentDetector.detect("anything", "office.ovpn"))
        val wg = "[Interface]\nPrivateKey = abc=\nAddress = 10.0.0.2/32\n\n[Peer]\nPublicKey = def=\n"
        assertEquals(ContentType.WIREGUARD, ContentDetector.detect(wg))
    }

    @Test fun unknownText() {
        assertEquals(ContentType.UNKNOWN, ContentDetector.detect("hello world"))
        assertEquals(ContentType.UNKNOWN, ContentDetector.detect("   "))
    }

    @Test fun subscriptionUserInfo() {
        val info = ContentDetector.parseSubscriptionUserInfo("upload=100; download=2.5E3; total=10737418240; expire=1767225600")!!
        assertEquals(100L, info.upload)
        assertEquals(2500L, info.download)
        assertEquals(10737418240L, info.total)
        assertEquals(1767225600L, info.expire)
        assertNull(ContentDetector.parseSubscriptionUserInfo(""))
    }

    @Test fun contentDisposition() {
        assertEquals("My Sub.yaml", ContentDetector.fileNameFromDisposition("attachment; filename*=UTF-8''My%20Sub.yaml"))
        assertEquals("sub.yaml", ContentDetector.fileNameFromDisposition("attachment; filename=\"sub.yaml\""))
        assertEquals("office", ContentDetector.nameFromFile("/storage/office.ovpn"))
    }

    @Test fun installConfigLinks() {
        val (url, name) = ContentDetector.installConfigUrl("clash://install-config?url=https%3A%2F%2Fexample.com%2Fsub%3Ftoken%3D1&name=Work")!!
        assertEquals("https://example.com/sub?token=1", url)
        assertEquals("Work", name)
        assertNull(ContentDetector.installConfigUrl("clash://install-config"))
    }

    @Test fun ovpnClientCertDetection() {
        val userPassOnly = "client\ndev tun\nremote vpn.example.com 1194\nauth-user-pass\n<ca>\nMIIB\n</ca>\n"
        assertFalse(ContentDetector.ovpnHasClientCert(userPassOnly))
        assertTrue(ContentDetector.ovpnHasClientCert(userPassOnly + "<cert>\nMIIC\n</cert>\n<key>\nMIIE\n</key>\n"))
        assertTrue(ContentDetector.ovpnHasClientCert(userPassOnly + "cert client.crt\nkey client.key\n"))
        assertTrue(ContentDetector.ovpnHasClientCert(userPassOnly + "pkcs12 client.p12\n"))
        // key-direction and tls-auth keys are not client certificates.
        assertFalse(ContentDetector.ovpnHasClientCert(userPassOnly + "key-direction 1\n<tls-auth>\nabc\n</tls-auth>\n"))
    }
}
