package com.blazify.buds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.demo.with
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
    private val A_X = 156f
    private val A_Y = 828f
    private val SHOW_X = 316f
    private val SHOW_Y = 943f

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

    @Test
    fun drawEditor() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val demo = DemoBuds.sample()
        val mine = listOf(3.0, 2.0, 0.5, -1.0, 0.0, 1.5, 2.5, 1.0)
        val prefs = Prefs(MapSettings()).apply { saveSound("Evening", mine) }
        // The editor after a change: Undo and A/B show, and each band is open.
        androidx.compose.ui.ImageComposeScene(390 * 2, 2300 * 2, Density(2f)) {
            BudsTheme(ThemeChoice.Light) {
                CompositionLocalProvider(LocalPrefs provides prefs) {
                    var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(custom(demo, mine)) }
                    EqualizerScreen(state, { state = state.with(it) })
                }
            }
        }.use { scene ->
            var t = 0L
            repeat(10) { scene.render(t); t += 50_000_000L }
            // Tap the 800 Hz point higher on the curve, then open Each band.
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(2 * 183f, 2 * 560f))
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(2 * 183f, 2 * 560f))
            repeat(20) { scene.render(t); t += 50_000_000L }
            File(dir, "sound-editor.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            // Then A, to hear the sound as it was, and each band opened.
            fun tap(x: Float, y: Float) {
                scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, androidx.compose.ui.geometry.Offset(2 * x, 2 * y))
                scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, androidx.compose.ui.geometry.Offset(2 * x, 2 * y))
                repeat(20) { scene.render(t); t += 50_000_000L }
            }
            tap(SHOW_X, SHOW_Y)
            tap(A_X, A_Y)
            File(dir, "sound-editor-a.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            tap(62f, 875f)
            File(dir, "sound-import.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test
    fun drawSharing() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val sound = SavedSound("Late night", listOf(2.0, 1.5, 0.5, 0.0, -1.0, -2.5, -3.0, -3.0))
        shot(dir, "sound-share-qr", height = 760) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                com.blazify.buds.ui.screens.ShareSoundDialog(sound, {}, {})
            }
        }
        val sounds = Gallery.parse(File("../community/sounds.json").readText())!!
        listOf(ThemeChoice.Dark, ThemeChoice.Light).forEach { theme ->
            shot(dir, "sound-gallery-${theme.name.lowercase()}", height = 1100, theme = theme) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.blazify.buds.ui.theme.LocalPalette.current.card)) {
                    com.blazify.buds.ui.screens.GalleryContent(com.blazify.buds.ui.screens.GalleryState.Ready(sounds), DemoBuds.FREQUENCIES, {}, {})
                }
            }
        }
    }
}
