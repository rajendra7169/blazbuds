package com.blazify.buds.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.ui.LocalPrefs
import com.blazify.buds.ui.Prefs
import com.blazify.buds.ui.screens.LocalSettingsExtras
import com.blazify.buds.ui.screens.SettingsScreen
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.PreferencesSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.prefs.Preferences
import kotlin.test.Test

/**
 * Pictures of Settings with the Home Assistant panel, drawn off screen with no earbuds:
 * BLAZBUDS_SHOTS=folder ./gradlew :desktop:test --tests '*SettingsShot*' --rerun. Skipped without it.
 */
class SettingsShot {
    @Test
    fun draw() {
        val dir = System.getenv("BLAZBUDS_SHOTS")?.let { File(it).apply { mkdirs() } } ?: return
        val node = Preferences.userRoot().node("blazbuds-test/shot-" + System.nanoTime())
        try {
            val settings = HaSettings(node).apply { host("homeassistant.local"); username("blazbuds"); password("secret") }
            val bridge = HomeAssistant(settings, MutableStateFlow(null), { null }, { _, _ -> })
            for (theme in listOf(ThemeChoice.Dark, ThemeChoice.Light)) {
                val prefs = Prefs(PreferencesSettings(node.node("prefs-" + theme.name)))
                ImageComposeScene(412 * 2, 1400 * 2, Density(2f)) {
                    BudsTheme(theme) {
                        CompositionLocalProvider(LocalPrefs provides prefs, LocalSettingsExtras provides { HomeAssistantPanel(settings, bridge) }) {
                            SettingsScreen(back = {})
                        }
                    }
                }.use { scene ->
                    repeat(10) { scene.render(it * 50_000_000L) }
                    File(dir, "settings-ha-${theme.name.lowercase()}.png").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }
        } finally {
            node.removeNode()
        }
    }
}
