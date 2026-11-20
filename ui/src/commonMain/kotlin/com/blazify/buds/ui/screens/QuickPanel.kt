package com.blazify.buds.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.Scene
import com.blazify.buds.ui.blocks.BatteryRing
import com.blazify.buds.ui.blocks.BatteryRow
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.LogoLoader
import com.blazify.buds.ui.blocks.LogoMark
import com.blazify.buds.ui.blocks.ModeSwitch
import com.blazify.buds.ui.theme.LocalPalette

/**
 * The small panel from the computer's tray: battery, the mode and the scenes, and nothing else. Opened by a click on
 * the tray icon, gone when it loses focus.
 */
@Composable
fun QuickPanel(state: BudsState, change: (Change) -> Unit, openApp: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(palette.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(size = 22.dp)
            Text(state.name, style = MaterialTheme.typography.titleMedium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Choice("Open", false, onClick = openApp)
        }
        when {
            state.link == Link.Connecting -> Box(Modifier.padding(24.dp).align(Alignment.CenterHorizontally)) { LogoLoader() }
            !state.isReady -> Text("Not connected", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            else -> {
                if (state.left == null && state.right == null && state.overall != null) {
                    BatteryRing("Earbuds", state.overall, ring = 44.dp)
                } else {
                    BatteryRow(state.left, com.blazify.buds.ui.caseBattery(state), state.right, ring = 44.dp)
                }
                state.sound?.let { sound ->
                    ModeSwitch(sound.mode) { change(Change.Mode(it)) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Scene.DEFAULTS.forEach { scene -> Choice(scene.name, scene.matches(state)) { scene.changes().forEach(change) } }
                    }
                }
            }
        }
    }
}
