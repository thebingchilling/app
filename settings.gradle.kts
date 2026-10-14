pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "quartz"

include(
    ":app",
    ":core:ui",
    ":core:caps",
    ":core:settings",
    ":core:media",
    ":core:camera",
)
