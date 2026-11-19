package com.blazify.buds.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/** A deeper page: a back button and title over a scrolling column, centred and kept readable on wide windows. */
@Composable
fun PlaceFrame(title: String, back: () -> Unit, showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(palette.background).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBack) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).press(onClick = back), contentAlignment = Alignment.Center) {
                        Icon(BudsIcons.Back, "Back", tint = palette.text)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = palette.text)
                }
            }
            content()
        }
    }
}

@Composable
fun PlaceScreen(place: Place, state: BudsState, change: (Change) -> Unit, back: () -> Unit) {
    when (place) {
        Place.Equalizer -> EqualizerScreen(state, change, back)
        Place.Controls -> ControlsScreen(state, change, back)
        Place.Sounds -> SoundsScreen(state, change, back)
        Place.About -> AboutScreen(state, back)
        Place.Settings -> SettingsScreen(back)
        Place.Devices -> DevicesScreen(state, change, back)
        Place.Earbuds -> Unit
    }
}
