package com.longoipo.rc4;

import org.json.JSONArray;
import org.json.JSONObject;

/** Plain-main unit tests (no framework needed): crypto vectors from OpenSSL and config rewrite cases. */
public final class BridgeUnitTest {
    private static int failures;

    public static void main(String[] args) {
        testKeyDerivation();
        testRc4Md5Keystream();
        testClassicRc4();
        testRewriteAndUntouchedCases();
        if (failures > 0) {
            System.out.println("FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("ALL UNIT TESTS PASSED");
    }

    private static void testKeyDerivation() {
        // openssl enc -rc4 -md md5 -nosalt -pass pass:testpass -P
        check("EVP_BytesToKey(md5) testpass", "179ad45c6ce2cb97cf1029e212046e81",
                hex(Rc4Md5.deriveKey("testpass".getBytes())));
    }

    private static void testRc4Md5Keystream() {
        // keystream of RC4(MD5(masterKey || iv)) over 32 zero bytes, iv = 00..0f, generated with openssl
        byte[] iv = new byte[16];
        for (int i = 0; i < 16; i++) iv[i] = (byte) i;
        byte[] buf = new byte[32];
        Rc4Md5.stream(Rc4Md5.deriveKey("testpass".getBytes()), iv, 0).crypt(buf, 0, buf.length);
        check("rc4-md5 keystream", "a75bbe0aadd017f66d86630249cbcbc43d89c84511f0882618e54c9a5156df85", hex(buf));
    }

    private static void testClassicRc4() {
        // Well-known RC4 vector: key "Key", plaintext "Plaintext"
        byte[] data = "Plaintext".getBytes();
        new Rc4Md5.Rc4("Key".getBytes()).crypt(data, 0, data.length);
        check("classic RC4", "bbf316e8d940af0ad3", hex(data));
    }

    private static void testRewriteAndUntouchedCases() {
        String noRc4 = "{\"outbounds\":[{\"protocol\":\"vmess\",\"tag\":\"proxy\"}]}";
        check("no rc4 -> same instance", true, Rc4Bridge.rewrite(noRc4) == noRc4);
        check("null passes through", true, Rc4Bridge.rewrite(null) == null);

        String bad = "not json but says rc4-md5";
        check("malformed -> unchanged", true, Rc4Bridge.rewrite(bad) == bad);

        String aead = ss("aes-256-gcm", "\"streamSettings\":{\"network\":\"tcp\"}");
        check("aead ss untouched", true, Rc4Bridge.rewrite(aead) == aead);

        String ws = ss("rc4-md5", "\"streamSettings\":{\"network\":\"ws\"}");
        check("non-tcp transport untouched", true, Rc4Bridge.rewrite(ws) == ws);

        String tls = ss("rc4-md5", "\"streamSettings\":{\"network\":\"tcp\",\"security\":\"tls\"}");
        check("tls untouched", true, Rc4Bridge.rewrite(tls) == tls);

        String plain = ss("RC4-MD5", "\"streamSettings\":{\"network\":\"tcp\",\"security\":\"none\"}");
        String out = Rc4Bridge.rewrite(plain);
        check("rc4 ss rewritten", true, out != plain);
        JSONObject s = new JSONObject(out).getJSONArray("outbounds").getJSONObject(1)
                .getJSONObject("settings").getJSONArray("servers").getJSONObject(0);
        check("address is loopback", "127.0.0.1", s.getString("address"));
        check("method is none", "none", s.getString("method"));
        check("port is a bridge port", true, s.getInt("port") > 0 && s.getInt("port") != 8388);
        JSONArray obs = new JSONObject(out).getJSONArray("outbounds");
        check("tag kept", "proxy", obs.getJSONObject(1).getString("tag"));
        check("other outbound untouched", "freedom", obs.getJSONObject(0).getString("protocol"));

        String again = Rc4Bridge.rewrite(plain);
        int p1 = new JSONObject(out).getJSONArray("outbounds").getJSONObject(1).getJSONObject("settings")
                .getJSONArray("servers").getJSONObject(0).getInt("port");
        int p2 = new JSONObject(again).getJSONArray("outbounds").getJSONObject(1).getJSONObject("settings")
                .getJSONArray("servers").getJSONObject(0).getInt("port");
        check("same node reuses bridge port", p1, p2);
    }

    private static String ss(String method, String extra) {
        return "{\"outbounds\":[{\"protocol\":\"freedom\",\"tag\":\"direct\"},"
                + "{\"protocol\":\"shadowsocks\",\"tag\":\"proxy\"," + extra + ","
                + "\"settings\":{\"servers\":[{\"address\":\"203.0.113.9\",\"port\":8388,"
                + "\"method\":\"" + method + "\",\"password\":\"pw\"}]}}]}";
    }

    private static void check(String name, Object want, Object got) {
        if (want.equals(got)) {
            System.out.println("ok   " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name + ": want " + want + " got " + got);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
