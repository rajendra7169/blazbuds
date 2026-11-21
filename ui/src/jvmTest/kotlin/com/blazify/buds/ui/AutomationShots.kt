package com.blazify.buds.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.screens.AutomationsScreen
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.MapSettings
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** The Automations page drawn off screen, empty and with rules, into SHOTS_DIR. Runs only when that is set. */
class AutomationShots {
    @Test
    fun drawAutomations() {
        val dir = System.getenv("SHOTS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        listOf(ThemeChoice.Dark to "empty", ThemeChoice.Light to "rules").forEach { (theme, kind) ->
            val book = RuleBook(MapSettings())
            if (kind == "rules") {
                book.save(Rule("a", Trigger.Connect, Action.PutScene("focus")))
                book.save(Rule("b", Trigger.At(22 * 60, ALL_DAYS), Action.PutScene("relax")))
                book.save(Rule("c", Trigger.At(8 * 60 + 30, WEEKDAYS), Action.PutScene("commute"), on = false))
                book.save(Rule("d", Trigger.BatteryBelow(20), Action.PutMode(ListeningMode.Normal)))
            }
            ImageComposeScene(390 * 2, 844 * 2, Density(2f)) {
                BudsTheme(theme) {
                    CompositionLocalProvider(LocalPrefs provides Prefs(MapSettings())) {
                        AutomationsScreen(book, now = { 1L }, back = {})
                    }
                }
            }.use { scene ->
                var t = 0L
                repeat(30) { scene.render(t); t += 50_000_000L }
                File(dir, "automations-$kind.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
