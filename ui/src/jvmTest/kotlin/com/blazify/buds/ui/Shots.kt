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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
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
    fun drawMockups() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        shot(dir, "mockups", 1000, 520) {
            BudsTheme(ThemeChoice.Light) {
                androidx.compose.foundation.layout.Row(
                    androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFFF5F7F8)),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
                ) {
                    listOf(SystemLook.Android, SystemLook.Ios, SystemLook.Linux).forEach { look ->
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(310.dp)) {
                            com.blazify.buds.ui.blocks.BluetoothMockup(look, "Liberty 4 NC", pairing = false)
                        }
                    }
                }
            }
        }
    }

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
            shot(dir, "find-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), Place.Find) }
            shot(dir, "fit-phone-$look", 412, 915) { BudsApp(DemoBuds(), prefs(theme), Place.Fit) }
            for ((name, style) in listOf("manual" to com.blazify.buds.core.model.CancelStyle.Manual, "travel" to com.blazify.buds.core.model.CancelStyle.Travel)) {
                shot(dir, "nc-$name-$look", 412, 915) {
                    val buds = remember { DemoBuds().also { kotlinx.coroutines.runBlocking { it.apply(com.blazify.buds.core.model.Change.Cancelling(style, level = 3)) } } }
                    BudsApp(buds, prefs(theme))
                }
            }
            shot(dir, "connect-phone-$look", 412, 915) { BudsApp(DemoBuds(connected = false), prefs(theme), Place.Connect) }
            shot(dir, "panel-$look", 360, 330) {
                val buds = remember { DemoBuds() }
                BudsTheme(theme) { QuickPanel(buds.state.value, {}, {}) }
            }
        }
    }
}
