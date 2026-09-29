# gomobile bindings: native code looks these up by name.
-keep class go.** { *; }
-keep class dev.tidewall.libcore.** { *; }
# OpenVPN 3 SWIG bindings (also in the openvpn module's consumer rules).
-keep class dev.tidewall.ovpn3.** { *; }
# Our subclass of the SWIG director is called from native code.
-keep class dev.tidewall.vpn.OpenVpnSession { *; }
-keep class dev.tidewall.vpn.OpenVpnSession$* { *; }
