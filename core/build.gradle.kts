plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.blazify.buds.core"
        compileSdk = 37
        minSdk = 26
    }

    // Linux, Windows and macOS.
    jvm()

    // The iPhone app links this as a framework.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BudsCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
        }
        jvmMain.dependencies {
            // Linux and Windows Bluetooth sockets, called straight from the app.
            implementation(libs.jna)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
