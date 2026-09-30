import javax.inject.Inject

// AmneziaWG's own Android engine (amneziawg-android, Apache-2.0), built from
// the pinned submodule in vendor/: its config parser and Go backend. The
// official WireGuard library already ships a libwg-go.so, so this one is
// built as libawg-go.so and loaded by that name.
plugins {
    id("com.android.library")
}

val vendorTunnel = file("../vendor/amneziawg-android/tunnel")

// Must equal the Flutter target platforms, like :core.
val abiByPlatform =
    linkedMapOf("android-arm" to "armeabi-v7a", "android-arm64" to "arm64-v8a", "android-x64" to "x86_64")
val abis =
    (rootProject.findProperty("target-platform") as String?)
        ?.split(",")
        ?.map { abiByPlatform[it.trim()] ?: throw GradleException("No AmneziaWG build for $it") }
        ?: abiByPlatform.values.toList()

/** Copies the parts of the library Pebble uses into a generated source folder. */
abstract class SyncAmneziaWgSources : DefaultTask() {
    @get:InputDirectory
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun sync() {
        files.sync {
            from(source)
            // Only the parser, keys and JNI entry points: the library's
            // VpnService and root backends are not used.
            include(
                "org/amnezia/awg/GoBackend.java",
                "org/amnezia/awg/config/**",
                "org/amnezia/awg/crypto/**",
                "org/amnezia/awg/util/NonNullForAll.java",
            )
            into(output)
        }
    }
}

val syncSources =
    tasks.register<SyncAmneziaWgSources>("syncAmneziaWgSources") {
        source.set(vendorTunnel.resolve("src/main/java"))
        output.set(layout.buildDirectory.dir("generated/amneziawg/java"))
    }

android {
    namespace = "app.pebble.amneziawg"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += abis
        }
        externalNativeBuild {
            cmake {
                targets("libawg-go.so")
                arguments(
                    "-DGRADLE_USER_HOME=${gradle.gradleUserHomeDir}",
                    "-DANDROID_PACKAGE_NAME=app.pebble.android",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
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

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(syncSources, SyncAmneziaWgSources::output)
    }
}

dependencies {
    implementation(libs.annotation.jvm)
    compileOnly(libs.jsr305)
}
