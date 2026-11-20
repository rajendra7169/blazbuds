package com.blazify.buds.ui

import androidx.compose.foundation.layout.padding
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
            shot(dir, "sound-custom-$look", 412, 915) {
                val night = listOf(3.0, 2.0, 0.5, -1.0, -0.5, 1.0, 2.5, 2.0)
                val buds = remember { DemoBuds().also { kotlinx.coroutines.runBlocking { it.apply(com.blazify.buds.core.model.Change.Bands(night)) } } }
                val mine = remember {
                    prefs(theme).apply {
                        saveSound("Late night", night)
                        saveSound("Gym", listOf(5.0, 4.0, 1.0, 0.0, 0.0, 1.0, 2.0, 3.0))
                        saveSound("Podcast", listOf(-2.0, 0.0, 2.0, 3.0, 2.0, 0.0, -1.0, -2.0))
                    }
                }
                BudsApp(buds, mine, startTab = Tab.Sound)
            }
            shot(dir, "battery-$look", 412, 915) {
                val buds = remember { DemoBuds() }
                BudsTheme(theme) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalPrefs provides Prefs(com.russhwolf.settings.MapSettings().apply {
                        val now = kotlin.time.Clock.System.now().epochSeconds
                        val name = buds.state.value.name
                        putString("battery_history_$name", (0 until 96).joinToString("\n") { i ->
                            val t = now - (95 - i) * 900L
                            val l = if (i < 40) 100 - i * 2 else if (i < 52) 20 + (i - 40) * 6 else 92 - (i - 52)
                            val r = (l - 5).coerceAtLeast(5)
                            val c = if (i in 40..51) 80 - (i - 40) * 2 else -1
                            "$t,$l,$r,$c"
                        })
                    })) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(com.blazify.buds.ui.theme.LocalPalette.current.background).padding(16.dp)) {
                            com.blazify.buds.ui.screens.BatterySection(buds.state.value)
                        }
                    }
                }
            }
            listOf("intro", "hearing", "taste", "result", "age", "quiet").forEachIndexed { step, name ->
                shot(dir, "earprint-$name-$look", 412, 915) {
                    val buds = remember { DemoBuds() }
                    BudsTheme(theme) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(com.blazify.buds.ui.theme.LocalPalette.current.background)) {
                            com.blazify.buds.ui.screens.EarPrintScreen(buds.state.value, {}, {}, preview = step)
                        }
                    }
                }
            }
            for ((name, style) in listOf("manual" to com.blazify.buds.core.model.CancelStyle.Manual, "travel" to com.blazify.buds.core.model.CancelStyle.Travel)) {
                shot(dir, "nc-$name-$look", 412, 915) {
                    val buds = remember { DemoBuds().also { kotlinx.coroutines.runBlocking { it.apply(com.blazify.buds.core.model.Change.Cancelling(style, level = 3)) } } }
                    BudsApp(buds, prefs(theme))
                }
            }
            shot(dir, "connect-phone-$look", 412, 915) { BudsApp(DemoBuds(connected = false), prefs(theme), Place.Connect) }
            shot(dir, "panel-$look", 360, 330) {
                val buds = remember { DemoBuds() }
                BudsTheme(theme) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalPrefs provides prefs(theme)) { QuickPanel(buds.state.value, {}, {}) }
                }
            }
        }
    }

    /** The computer's narrow window with its row arrows, and the tray panel at its own height: SHOTS_DESKTOP. */
    @Test
    fun drawDesktopRows() {
        val dir = System.getenv("SHOTS_DESKTOP")?.let(::File)?.apply { mkdirs() } ?: return
        for (theme in listOf(ThemeChoice.Dark, ThemeChoice.Light)) {
            val look = theme.name.lowercase()
            // Tall enough to show the scenes and the sound card's presets below the fold of a real window.
            shot(dir, "home-narrow-$look", 440, 1700) {
                androidx.compose.runtime.CompositionLocalProvider(com.blazify.buds.ui.blocks.LocalArrows provides true) {
                    BudsApp(DemoBuds(), prefs(theme))
                }
            }
            shot(dir, "settings-$look", 440, 2000) { BudsApp(DemoBuds(), prefs(theme), Place.Settings) }
            shot(dir, "changelog-$look", 440, 1100) { BudsApp(DemoBuds(), prefs(theme), Place.Changelog) }
            shot(dir, "help-$look", 440, 2100) { BudsApp(DemoBuds(), prefs(theme), Place.Support) }
            shot(dir, "panel-$look", 360, 460) {
                val buds = remember { DemoBuds() }
                BudsTheme(theme) {
                    androidx.compose.runtime.CompositionLocalProvider(LocalPrefs provides prefs(theme)) { QuickPanel(buds.state.value, {}, {}) }
                }
            }
        }
    }
}
