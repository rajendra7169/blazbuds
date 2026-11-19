package com.blazify.buds.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.MapSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Draws Home off screen in both looks, at phone and computer sizes, into the folder named by SHOTS_DIR.
 * Runs only when that is set: it makes pictures for the readme and for checking the design by eye.
 */
class Shots {
    @Test
    fun drawHome() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val sizes = listOf("phone" to (412 to 915), "desktop" to (1180 to 820))
        for ((sizeName, size) in sizes) {
            for (theme in listOf(ThemeChoice.Light, ThemeChoice.Dark)) {
                val prefs = Prefs(MapSettings()).apply { theme(theme) }
                val scale = 2f
                ImageComposeScene((size.first * scale).toInt(), (size.second * scale).toInt(), Density(scale)) {
                    BudsApp(DemoBuds(), prefs)
                }.use { scene ->
                    // Several frames, so entrance animations have settled.
                    var time = 0L
                    repeat(40) { scene.render(time); time += 50_000_000L }
                    val image = scene.render(time)
                    val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                    File(dir, "home-$sizeName-${theme.name.lowercase()}.png").writeBytes(bytes)
                }
            }
        }
    }
}
