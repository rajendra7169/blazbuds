import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.settings)
}

val appVersion = "0.1.0"

compose.desktop {
    application {
        mainClass = "com.blazify.buds.desktop.MainKt"
        // A small app: a small heap the collector gives back.
        jvmArgs("-Xmx160m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Blazbuds"
            packageVersion = appVersion
            description = "Control your wireless earbuds"
            vendor = "Rajendra Pandey"
            modules("jdk.unsupported")
            linux {
                packageName = "blazbuds"
                menuGroup = "AudioVideo"
                iconFile.set(project.file("packaging/blazbuds.png"))
            }
            windows {
                menuGroup = "Blazbuds"
                iconFile.set(project.file("packaging/blazbuds.ico"))
                shortcut = true
                perUserInstall = true
                upgradeUuid = "3f0a6b0e-6f0c-4d33-9a43-6a1c2e9b7d41"
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Draws the app with the real earbuds, off screen: ./gradlew :desktop:shot -Pargs="folder 412 915 dark 6"
tasks.register<JavaExec>("shot") {
    mainClass.set("com.blazify.buds.desktop.ShotKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = (project.findProperty("args") as String? ?: "").split(" ").filter { it.isNotBlank() }
}
