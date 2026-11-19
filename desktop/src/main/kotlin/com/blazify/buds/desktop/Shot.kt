package com.blazify.buds.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.ui.BudsApp
import com.blazify.buds.ui.Hub
import com.blazify.buds.ui.Prefs
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.PreferencesSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.prefs.Preferences

/**
 * Draws the real app, talking to the real earbuds, off screen: `shot <folder> <width> <height> <light|dark> <seconds>`.
 * For checking the app with real data without opening a window.
 */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "shots" }).apply { mkdirs() }
    val width = args.getOrElse(1) { "412" }.toInt()
    val height = args.getOrElse(2) { "915" }.toInt()
    val theme = if (args.getOrElse(3) { "dark" } == "light") ThemeChoice.Light else ThemeChoice.Dark
    val seconds = args.getOrElse(4) { "6" }.toInt()
    val store = PreferencesSettings(Preferences.userRoot().node("blazbuds-shot"))
    val prefs = Prefs(store).apply { theme(theme) }
    val scale = 2f
    ImageComposeScene((width * scale).toInt(), (height * scale).toInt(), Density(scale)) {
        val scope = rememberCoroutineScope()
        BudsApp(remember { Hub(LinuxPlatform, scope, store) }, prefs)
    }.use { scene ->
        val start = System.nanoTime()
        while (System.nanoTime() - start < seconds * 1_000_000_000L) {
            scene.render(System.nanoTime() - start)
            Thread.sleep(50)
        }
        val image = scene.render(System.nanoTime() - start)
        File(dir, "real-${width}x$height-${theme.name.lowercase()}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    kotlin.system.exitProcess(0)
}
