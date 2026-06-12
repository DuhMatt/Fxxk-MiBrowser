import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read release signing credentials from signing/release-keystore.properties.
// The file is .gitignore'd and must NEVER be committed.
val keystorePropsFile = rootProject.file("signing/release-keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(keystorePropsFile.inputStream())
    }
}

android {
    namespace = "com.hyperosfix.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hyperosfix.browser"
        minSdk = 34
        targetSdk = 35
        versionCode = 13
        versionName = "1.2.3"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
                println("[Fxxk-MiBrowser] Release build will be signed with: " +
                    keystoreProps.getProperty("storeFile"))
            } else {
                println("[Fxxk-MiBrowser] WARNING: signing/release-keystore.properties not found — " +
                    "release APK will be UNSIGNED")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // LSPosed / Xposed API — compileOnly, not bundled into APK
    compileOnly("de.robv.android.xposed:api:82")
}
