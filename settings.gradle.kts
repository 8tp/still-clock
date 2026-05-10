// Root Gradle settings for Still Clock.
// Mirrors still-launcher / still-notes / still-cal: one repo declaration, single :app module.
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

rootProject.name = "still-clock"
include(":app")
