import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal apksig front end used by patch.py when Android build-tools (apksigner) are not available.
 *
 *   sign   IN.apk OUT.apk KEYSTORE ALIAS   (passwords come from env KS_PASS / KEY_PASS)
 *   verify APK
 */
public final class SigTool {
    public static void main(String[] a) throws Exception {
        if (a.length >= 5 && a[0].equals("sign")) {
            sign(new File(a[1]), new File(a[2]), a[3], a[4]);
        } else if (a.length == 2 && a[0].equals("verify")) {
            System.exit(verify(new File(a[1])) ? 0 : 1);
        } else {
            System.err.println("usage: sign IN OUT KEYSTORE ALIAS | verify APK");
            System.exit(2);
        }
    }

    private static void sign(File in, File out, String ksPath, String alias) throws Exception {
        String ksPass = System.getenv("KS_PASS");
        String keyPass = System.getenv("KEY_PASS");
        if (keyPass == null || keyPass.isEmpty()) keyPass = ksPass;
        KeyStore ks = KeyStore.getInstance(ksPath.toLowerCase().endsWith(".jks") ? "JKS" : "PKCS12");
        try (FileInputStream f = new FileInputStream(ksPath)) {
            ks.load(f, ksPass.toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
        if (key == null) throw new IllegalStateException("alias not found in keystore: " + alias);
        List<X509Certificate> certs = new ArrayList<>();
        for (java.security.cert.Certificate c : ks.getCertificateChain(alias)) certs.add((X509Certificate) c);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("longoipo", key, certs).build();
        new ApkSigner.Builder(Arrays.asList(cfg))
                .setInputApk(in)
                .setOutputApk(out)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();
        System.out.println("signed " + out);
    }

    private static boolean verify(File apk) throws Exception {
        ApkVerifier.Result r = new ApkVerifier.Builder(apk).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme()
                + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
        for (X509Certificate c : r.getSignerCertificates()) {
            System.out.println("signer=" + c.getSubjectX500Principal() + " sha256="
                    + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())));
        }
        for (ApkVerifier.IssueWithParams i : r.getErrors()) System.out.println("ERROR " + i);
        for (ApkVerifier.IssueWithParams i : r.getWarnings()) System.out.println("WARN " + i);
        return r.isVerified();
    }
}
