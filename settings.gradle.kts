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
        // Xposed API
        maven { url = uri("https://api.xposed.info/") }
        // JitPack (fallback)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Fxxk-MiBrowser"
include(":app")
