package com.blazify.buds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.eq.Tuned
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.ui.screens.EqualizerScreen
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.MapSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** The Sound tab drawn off screen with the demo earbuds, into SHOTS_DIR. Runs only when that is set. */
class SoundShots {
    private fun shot(dir: File, name: String, height: Int = 1400, theme: ThemeChoice = ThemeChoice.Dark, prefs: Prefs = Prefs(MapSettings()), content: @Composable () -> Unit) {
        ImageComposeScene(390 * 2, height * 2, Density(2f)) {
            BudsTheme(theme) {
                CompositionLocalProvider(LocalPrefs provides prefs) { content() }
            }
        }.use { scene ->
            var t = 0L
            repeat(40) { scene.render(t); t += 50_000_000L }
            File(dir, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private fun custom(state: BudsState, gains: List<Double>, right: List<Double> = gains) =
        state.copy(equalizer = state.equalizer!!.copy(presetId = Equalizer.CUSTOM, left = gains, right = right))

    @Test
    fun drawTuned() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val demo = DemoBuds.sample()
        val tuned = Tuned.find(demo)!!
        shot(dir, "sound-tuned-offer") { EqualizerScreen(demo, {}) }
        shot(dir, "sound-tuned-on", theme = ThemeChoice.Light) { EqualizerScreen(custom(demo, tuned.gains), {}) }
    }

    @Test
    fun drawBalance() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val demo = DemoBuds.sample()
        val mine = listOf(3.0, 2.0, 0.5, -1.0, 0.0, 1.5, 2.5, 1.0)
        val prefs = Prefs(MapSettings()).apply { saveSound("Evening", mine); saveSound("Gym", listOf(5.0, 4.0, 1.0, 0.0, 0.0, 1.0, 2.0, 3.0)) }
        val (left, right) = com.blazify.buds.core.eq.Balance.split(mine, -2.5)
        shot(dir, "sound-balance", height = 1700, prefs = prefs) { EqualizerScreen(custom(demo, left, right), {}) }
    }
}
