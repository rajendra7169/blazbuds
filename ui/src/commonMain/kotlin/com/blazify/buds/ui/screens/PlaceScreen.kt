package com.blazify.buds.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/** Where Home sends the person for what isn't in the tabs. */
enum class Place { Earbuds, Add, Settings, Support, Connect, Find, Fit }

/**
 * A page: a title, with a back button when it was opened from somewhere and icon buttons on the right, over a
 * scrolling column centred and kept readable on wide windows.
 */
@Composable
fun PlaceFrame(
    title: String,
    back: (() -> Unit)?,
    actions: List<Pair<ImageVector, Pair<String, () -> Unit>>> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(palette.background).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (back != null) {
                    IconButton(BudsIcons.Back, "Back", back)
                    Text(title, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.weight(1f))
                } else {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
                actions.forEach { (icon, action) -> IconButton(icon, action.first, action.second) }
            }
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(Modifier.size(48.dp).clip(CircleShape).press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = palette.text, modifier = Modifier.size(24.dp))
    }
}
