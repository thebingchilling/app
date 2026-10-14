// Pure Kotlin logic shared by the keyboard: romanisation, reading/meaning selection.
// No Android dependencies, so it is unit-tested on the JVM.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}

dependencies {
    // tests read the real bumboard/data/out/assets/bumboard/readings.db when it has been built
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

tasks.test {
    systemProperty("bumboard.readingsDb",
        rootDir.resolve("../../data/out/assets/bumboard/readings.db").canonicalPath)
}
