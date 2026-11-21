package com.blazify.buds.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.ui.blocks.LinkRow
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.icons.BudsIcons

/** Everything about the pair itself: their connections, tones and prompts, what they are, and the app's own pages. */
@Composable
fun DeviceTab(state: BudsState, change: (Change) -> Unit, inactive: Boolean = false, open: (Place) -> Unit) =
    PlaceFrame("Device", back = null, actions = listOf(BudsIcons.Support to ("Help" to { open(Place.Support) }), BudsIcons.Settings to ("Settings" to { open(Place.Settings) }))) {
        // The earbuds' own settings fade while they are out of reach; the links below stay usable.
        com.blazify.buds.ui.blocks.Inactive(inactive) {
            HostsSection(state, change)
            SafeVolumeSection(state, change)
            PromptsSection(state, change)
            AboutSection(state)
        }
        Panel(padding = 8.dp) {
            if ((state.canRing || com.blazify.buds.ui.LocalCanFind.current) && state.isReady) LinkRow(BudsIcons.Find, "Find my earbuds", "Play a sound on each one", color = Hue.Orange) { open(Place.Find) }
            if (state.canFitTest) LinkRow(BudsIcons.Ear, "Ear tip fit test", "Check how well they seal", color = Hue.Purple) { open(Place.Fit) }
        }
        Panel(padding = 8.dp) {
            LinkRow(BudsIcons.Auto, "Automations", "Change them by themselves: on connect, at a time, on low battery", color = Hue.Indigo) { open(Place.Automations) }
        }
        Panel(padding = 8.dp) {
            LinkRow(BudsIcons.Earbuds, "My devices", "Switch earbuds or add a pair", color = Hue.Blue) { open(Place.Earbuds) }
            LinkRow(BudsIcons.Support, "Help", "Answers, and details to send", color = Hue.Green) { open(Place.Support) }
            LinkRow(BudsIcons.Settings, "Settings", "Look and motion", color = Hue.Slate) { open(Place.Settings) }
        }
        BatterySection(state)
    }
