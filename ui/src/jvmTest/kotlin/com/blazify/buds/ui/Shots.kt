package com.blazify.buds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.ui.screens.Place
import com.blazify.buds.ui.screens.Tab
import com.blazify.buds.ui.screens.QuickPanel
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.MapSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Draws the app off screen, with the demo earbuds, into the folder named by SHOTS_DIR: the pictures in the readme.
 * Runs only when that is set.
 */
class Shots {
    private fun shot(dir: File, name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val scale = 2f
        ImageComposeScene((width * scale).toInt(), (height * scale).toInt(), Density(scale), content = content).use { scene ->
            var time = 0L
            repeat(60) { scene.render(time); time += 50_000_000L }
            File(dir, "$name.png").writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private fun prefs(theme: ThemeChoice) = Prefs(MapSettings()).apply { theme(theme) }

    @Test
    fun drawScreens() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        for (theme in listOf(ThemeChoice.Light, ThemeChoice.Dark)) {
            val look = theme.name.lowercase()
            shot(dir, "home-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme)) }
            shot(dir, "home-desktop-$look", 1180, 820) { BudsApp(DemoBuds(), prefs(theme)) }
            shot(dir, "sound-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), startTab = Tab.Sound) }
            shot(dir, "controls-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), startTab = Tab.Controls) }
            shot(dir, "device-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), startTab = Tab.Device) }
            shot(dir, "earbuds-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), Place.Earbuds) }
            shot(dir, "add-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), Place.Add) }
            shot(dir, "help-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), Place.Support) }
            shot(dir, "panel-$look", 360, 330) {
                val buds = remember { DemoBuds() }
                BudsTheme(theme) { QuickPanel(buds.state.value, {}, {}) }
            }
        }
    }
}
