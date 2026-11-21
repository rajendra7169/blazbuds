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
    testImplementation(libs.kotlin.test)
}

val appVersion = "1.2.0"

compose.desktop {
    application {
        mainClass = "com.blazify.buds.desktop.MainKt"
        // A small app: a small heap the collector gives back.
        // The last one lets the app name its window class, so the Linux dash shows its logo instead of a gear.
        jvmArgs("-Xmx160m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Blazbuds"
            packageVersion = appVersion
            description = "Control your wireless earbuds"
            vendor = "Rajendra Pandey"
            // java.prefs keeps the settings; without it the installed app fails where the one run from Gradle works.
            modules("java.instrument", "java.prefs", "jdk.unsupported")
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
