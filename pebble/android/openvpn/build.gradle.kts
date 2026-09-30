// OpenVPN 2 built from OpenVPN for Android (ics-openvpn, GPL-2.0), pinned as
// a submodule in vendor/. Only the native engine: the service drives it over
// OpenVPN's management interface (see OpenVpnEngine in :service).
plugins {
    id("com.android.library")
}

// Must equal the Flutter target platforms, like :core.
val abiByPlatform =
    linkedMapOf("android-arm" to "armeabi-v7a", "android-arm64" to "arm64-v8a", "android-x64" to "x86_64")
val abis =
    (rootProject.findProperty("target-platform") as String?)
        ?.split(",")
        ?.map { abiByPlatform[it.trim()] ?: throw GradleException("No OpenVPN build for $it") }
        ?: abiByPlatform.values.toList()

android {
    namespace = "app.pebble.openvpn"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        ndk {
            abiFilters += abis
        }
        externalNativeBuild {
            cmake {
                targets("openvpn", "libovpnexec.so")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
