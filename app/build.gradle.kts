plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.tomlmbl.launcher"
    compileSdk = 37

    defaultConfig {
        // Final application ID: changing it would lose the installed configuration.
        applicationId = "io.github.tomlmbl.cobblehome"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    // Release key outside the repository: read from the environment at build time; without it, the release stays
    // unsigned (the GitHub Actions reproducibility build). See REPRODUCIBLE_BUILDS.md.
    val releaseStore = System.getenv("LAUNCHER_STORE_FILE")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("LAUNCHER_STORE_PASSWORD")
                keyAlias = System.getenv("LAUNCHER_KEY_ALIAS")
                keyPassword = System.getenv("LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        // Device builds: signed with the release key when it is provided, so they install over the release and over
        // each other without data loss; otherwise, the debug key.
        debug {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
        // Optimized build, for daily use and on-device measurements.
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
        // Store screenshots: a `perf` build with the displayed time frozen at 16:00, like the SystemUI demo mode status
        // bar, and sample weather (`ScreenshotWeather`).
        create("screenshots") {
            initWith(getByName("perf"))
            // `initWith` does not copy the signing config (the APK was signed with the debug key).
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
            buildConfigField("boolean", "SCREENSHOTS", "true")
        }
    }
    defaultConfig { buildConfigField("boolean", "SCREENSHOTS", "false") }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Encrypted dependency block for Google in the signature: unreadable outside Play and against reproducibility.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.datastore:datastore:1.3.0")

    testImplementation("junit:junit:4.13.2")
}
