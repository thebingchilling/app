package dev.tidewall.ovpn3;

/**
 * Loads the native OpenVPN 3 library. SWIG's generated classes call into
 * native code as soon as they are first used, so call {@link #load()} before
 * touching any ClientAPI_* class.
 */
public final class OpenVpn3 {
    static {
        System.loadLibrary("ovpn3");
    }

    private OpenVpn3() {}

    /** Ensures the native library is loaded (idempotent). */
    public static void load() {}

    /** Version and platform string reported by the OpenVPN 3 core. */
    public static String platform() {
        load();
        return ClientAPI_OpenVPNClientHelper.platform();
    }
}
