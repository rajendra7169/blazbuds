import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.blazify.buds"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blazify.buds"
        minSdk = 26
        targetSdk = 36
        versionCode = 10300
        versionName = "1.3.0"
    }

    // The release key lives outside the repo; keystore.properties (never in git) says where. Without it, as on a
    // fresh clone, release builds are signed with the debug key so they still install for testing.
    val keys = rootProject.file("android/keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keys != null) {
            create("release") {
                storeFile = file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(libs.coroutines.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.settings)
    implementation(libs.settings.no.arg)
}
