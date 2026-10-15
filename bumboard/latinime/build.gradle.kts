plugins {
    alias(libs.plugins.android.application)
}

android {
    // LatinIME keeps its Java package (the native code registers JNI methods by
    // class name); only the installed application id is BumBoard's.
    namespace = "com.android.inputmethod.latin"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "app.bumboard"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("BUILD_VERSION_NAME") ?: "0.1.0-dev"
        vectorDrawables.useSupportLibrary = false
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("java/AndroidManifest.xml")
            java.srcDir("java/src")
            java.srcDir("common/src")
            res.srcDir("java/res")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("native/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    androidResources {
        // Dictionaries are mmapped by the native code, so they must stay uncompressed.
        noCompress += "dict"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "java/proguard.flags")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.annotation)
    compileOnly(libs.jsr305)
}
