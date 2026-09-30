
-keep class com.follow.clash.models.** { *; }

-keep class com.follow.clash.service.models.** { *; }

# Read from the profile with Gson.
-keep class com.follow.clash.service.direct.DirectTunnel { *; }

# Direct tunnels: WireGuardEngine calls GoBackend's JNI functions by name.
-keep class com.wireguard.android.backend.GoBackend { native <methods>; }
-keep class org.amnezia.awg.GoBackend { native <methods>; }
