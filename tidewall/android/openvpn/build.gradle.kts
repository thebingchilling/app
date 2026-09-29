// Direct OpenVPN engine: the official OpenVPN 3 core (MPL-2.0) built with the
// NDK, exposed to Kotlin through SWIG-generated JNI bindings.
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

val deps = layout.projectDirectory.dir("deps")
val swigOut = layout.buildDirectory.dir("generated/swig")
val swigJava = swigOut.map { it.dir("java") }
val swigCxx = swigOut.map { it.file("cxx/ovpncli_wrap.cxx") }

val fetchDeps = tasks.register<Exec>("fetchOpenVpn3Deps") {
    description = "Downloads OpenVPN 3 and its dependencies"
    inputs.file("fetch-deps.sh")
    outputs.dir(deps)
    commandLine("bash", "fetch-deps.sh")
}

/** Runs SWIG on OpenVPN 3's ovpncli.i, producing Java sources and the C++ JNI wrapper. */
abstract class SwigTask : DefaultTask() {
    @get:Inject abstract val execOps: ExecOperations
    @get:InputFile abstract val interfaceFile: RegularFileProperty
    @get:Internal abstract val openvpn3Dir: DirectoryProperty
    @get:OutputDirectory abstract val javaDir: DirectoryProperty
    @get:OutputFile abstract val cxxFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val java = javaDir.get().asFile
        java.deleteRecursively()
        val pkgDir = java.resolve("dev/tidewall/ovpn3").apply { mkdirs() }
        cxxFile.get().asFile.parentFile.mkdirs()
        val ovpn3 = openvpn3Dir.get().asFile
        execOps.exec {
            commandLine(
                "swig", "-c++", "-java",
                "-package", "dev.tidewall.ovpn3",
                "-DOPENVPN_PLATFORM_ANDROID",
                "-outdir", pkgDir.path,
                "-o", cxxFile.get().asFile.path,
                "-I${ovpn3.resolve("client")}", "-I$ovpn3",
                interfaceFile.get().asFile.path,
            )
        }
    }
}

val swig = tasks.register<SwigTask>("swigOpenVpn3") {
    description = "Generates the JNI wrapper for the OpenVPN 3 client API"
    dependsOn(fetchDeps)
    interfaceFile.set(deps.file("openvpn3/client/ovpncli.i"))
    openvpn3Dir.set(deps.dir("openvpn3"))
    javaDir.set(swigJava)
    cxxFile.set(swigCxx)
}

val abis = providers.gradleProperty("tidewall.abis").getOrElse("arm64-v8a").split(",").map { it.trim() }

android {
    namespace = "dev.tidewall.ovpn3"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += abis }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DOVPN3_SWIG_CXX=${swigCxx.get().asFile.path}",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(swig, SwigTask::javaDir)
    }
}

tasks.named("preBuild") { dependsOn(swig) }
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(swig)
}
