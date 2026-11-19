package com.blazify.buds.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.ui.blocks.LinkRow
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.icons.BudsIcons

/** Everything about the pair itself: their connections, tones and prompts, what they are, and the app's own pages. */
@Composable
fun DeviceTab(state: BudsState, change: (Change) -> Unit, open: (Place) -> Unit) =
    PlaceFrame("Device", back = null, actions = listOf(BudsIcons.Support to ("Help" to { open(Place.Support) }), BudsIcons.Settings to ("Settings" to { open(Place.Settings) }))) {
        HostsSection(state, change)
        PromptsSection(state, change)
        AboutSection(state)
        Panel(padding = 8.dp) {
            LinkRow(BudsIcons.Earbuds, "My devices", "Switch earbuds or add a pair") { open(Place.Earbuds) }
            LinkRow(BudsIcons.Support, "Help", "Answers, and details to send") { open(Place.Support) }
            LinkRow(BudsIcons.Settings, "Settings", "Look and motion") { open(Place.Settings) }
        }
    }
