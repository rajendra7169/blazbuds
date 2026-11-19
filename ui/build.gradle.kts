plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.blazify.buds.ui"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
    }

    // Linux, Windows and macOS.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
            implementation(libs.settings)
            implementation(libs.settings.no.arg)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.settings.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "com.blazify.buds.ui.resources"
}
