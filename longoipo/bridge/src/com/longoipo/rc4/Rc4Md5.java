package com.longoipo.rc4;

import java.security.MessageDigest;

/** Shadowsocks "rc4-md5" primitives: EVP_BytesToKey(MD5) key derivation and the per-stream RC4 cipher. */
final class Rc4Md5 {
    static final int IV_LEN = 16;
    static final int KEY_LEN = 16;

    private Rc4Md5() {}

    /** OpenSSL EVP_BytesToKey with MD5, no salt, one round; yields the 16-byte master key. */
    static byte[] deriveKey(byte[] password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] out = new byte[KEY_LEN];
            byte[] prev = new byte[0];
            int have = 0;
            while (have < KEY_LEN) {
                md.reset();
                md.update(prev);
                md.update(password);
                prev = md.digest();
                int n = Math.min(prev.length, KEY_LEN - have);
                System.arraycopy(prev, 0, out, have, n);
                have += n;
            }
            return out;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A fresh RC4 stream for one direction: key = MD5(masterKey || iv). */
    static Rc4 stream(byte[] masterKey, byte[] iv, int ivOff) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(masterKey);
            md.update(iv, ivOff, IV_LEN);
            return new Rc4(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class Rc4 {
        private final int[] s = new int[256];
        private int i;
        private int j;

        Rc4(byte[] key) {
            for (int k = 0; k < 256; k++) s[k] = k;
            int jj = 0;
            for (int k = 0; k < 256; k++) {
                jj = (jj + s[k] + (key[k % key.length] & 0xff)) & 0xff;
                int t = s[k];
                s[k] = s[jj];
                s[jj] = t;
            }
        }

        /** XORs the keystream into buf[off, off+len) in place. */
        void crypt(byte[] buf, int off, int len) {
            int ii = i;
            int jj = j;
            for (int k = 0; k < len; k++) {
                ii = (ii + 1) & 0xff;
                jj = (jj + s[ii]) & 0xff;
                int t = s[ii];
                s[ii] = s[jj];
                s[jj] = t;
                buf[off + k] ^= (byte) s[(s[ii] + s[jj]) & 0xff];
            }
            i = ii;
            j = jj;
        }
    }
}
