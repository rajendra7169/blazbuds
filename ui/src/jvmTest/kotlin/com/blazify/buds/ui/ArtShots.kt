package com.blazify.buds.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.blazify.buds.ui.blocks.Graphite
import com.blazify.buds.ui.blocks.Pearl
import com.blazify.buds.ui.blocks.drawHeadphones
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** The drawn devices at each step of opening, in both looks, for checking them by eye. Only with SHOTS_DIR set. */
class ArtShots {
    @Test
    fun drawPresets() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val presets = com.blazify.buds.core.soundcore.A3947.PRESETS
        for (theme in listOf(com.blazify.buds.ui.theme.ThemeChoice.Dark, com.blazify.buds.ui.theme.ThemeChoice.Light)) {
            ImageComposeScene(412 * 2, 1180 * 2, Density(2f)) {
                com.blazify.buds.ui.theme.BudsTheme(theme) {
                    Column(
                        Modifier.background(com.blazify.buds.ui.theme.LocalPalette.current.background).padding(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    ) {
                        presets.chunked(2).forEachIndexed { row, pair ->
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                                pair.forEachIndexed { i, (name, tenths) ->
                                    com.blazify.buds.ui.blocks.PresetTile(row * 2 + i, name, tenths.take(8).map { it / 10.0 }, chosen = name == "Acoustic", modifier = Modifier.weight(1f), showCurve = false) {}
                                }
                            }
                        }
                    }
                }
            }.use { scene ->
                var t = 0L
                repeat(10) { scene.render(t); t += 100_000_000L }
                File(dir, "presets-${theme.name.lowercase()}.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }

    @Test
    fun drawEarPrint() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        ImageComposeScene(1400 * 2, 640 * 2, Density(2f)) {
            Column {
                for (theme in listOf(com.blazify.buds.ui.theme.ThemeChoice.Dark, com.blazify.buds.ui.theme.ThemeChoice.Light)) {
                    com.blazify.buds.ui.theme.BudsTheme(theme) {
                        Row(Modifier.background(com.blazify.buds.ui.theme.LocalPalette.current.background)) {
                            com.blazify.buds.ui.blocks.FingerprintEar(0f, size = 280.dp)
                            com.blazify.buds.ui.blocks.FingerprintEar(0.4f, size = 280.dp, listening = true)
                            com.blazify.buds.ui.blocks.FingerprintEar(0.75f, size = 280.dp, mirrored = true)
                            com.blazify.buds.ui.blocks.FingerprintEar(1f, size = 280.dp, done = true)
                            com.blazify.buds.ui.blocks.FingerprintEar(1f, size = 280.dp, done = true, mirrored = true)
                        }
                    }
                }
            }
        }.use { scene ->
            var t = 0L
            repeat(40) { scene.render(t); t += 100_000_000L }
            File(dir, "earprint.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test
    fun compareCase() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        ImageComposeScene(1200 * 2, 640 * 2, Density(2f)) {
            Column {
                for (theme in listOf(com.blazify.buds.ui.theme.ThemeChoice.Light, com.blazify.buds.ui.theme.ThemeChoice.Dark)) {
                    com.blazify.buds.ui.theme.BudsTheme(theme) {
                        Row(Modifier.background(com.blazify.buds.ui.theme.LocalPalette.current.background)) {
                            for (soft in listOf(false, true)) for (connected in listOf(false, true)) {
                                com.blazify.buds.ui.blocks.CaseHero(
                                    connected, com.blazify.buds.core.model.ListeningMode.NoiseCancelling, leftOut = true, rightOut = true,
                                    modifier = Modifier.size(300.dp, 320.dp), height = 320.dp, still = true, soft = soft,
                                )
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            var t = 0L
            repeat(30) { scene.render(t); t += 100_000_000L }
            File(dir, "compare.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test
    fun drawDevices() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        val teal = Color(0xFF3DD5C3)
        ImageComposeScene(1500 * 2, 640 * 2, Density(2f)) {
            Column {
                for ((finish, bg, dark) in listOf(Triple(Pearl, Color(0xFFF5F7F8), false), Triple(Graphite, Color(0xFF0C0F12), true))) {
                    Row(Modifier.background(bg)) {
                        for (open in listOf(0f, 1f)) {
                            Canvas(Modifier.size(300.dp, 320.dp)) { drawHeadphones(finish, open, 0f, if (open > 0.9f) teal else Color.Transparent, dark) }
                        }
                    }
                }
            }
        }.use { scene ->
            File(dir, "art.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
