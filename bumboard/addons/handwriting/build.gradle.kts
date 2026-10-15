// BumBoard handwriting add-on: a separate APK that holds Google's ML Kit Digital Ink
// (closed source), so the LGPL keyboard itself stays free of it. The keyboard talks to it
// through HandwritingService (Messenger IPC); only apps signed with the same key are served.
plugins {
    id("org.fcitx.fcitx5.android.app-convention")
}

android {
    namespace = "app.bumboard.handwriting"

    defaultConfig {
        applicationId = "app.bumboard.android.handwriting"
    }

    buildFeatures {
        resValues = true
    }

    buildTypes {
        release {
            resValue("string", "app_name", "BumBoard Handwriting")
        }
        debug {
            resValue("string", "app_name", "BumBoard Handwriting Debug")
        }
    }
}

dependencies {
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation(libs.kotlinx.coroutines)
}
