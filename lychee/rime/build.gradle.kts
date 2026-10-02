// Lychee: librime (prebuilt, static) behind a small JNI bridge.
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "app.lychee.rime"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = 23
        ndk { abiFilters.add("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    // the app's extra build types
    buildTypes {
        create("nouserlib")
        create("runTests")
        create("debugNoMinify") { isJniDebuggable = false }
    }

    testOptions {
        unitTests.all {
            // the host build of the JNI bridge (test/host_jni.sh), when there is one
            System.getenv("LYCHEE_HOST_RIME")?.let { dir ->
                it.environment("LYCHEE_HOST_RIME", dir)
                it.jvmArgs("-Djava.library.path=$dir")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
