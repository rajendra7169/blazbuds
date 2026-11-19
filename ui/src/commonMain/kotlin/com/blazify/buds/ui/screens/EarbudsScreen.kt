package com.blazify.buds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.ui.Pair2
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.LogoLoader
import com.blazify.buds.ui.blocks.LogoMark
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/**
 * Every pair of earbuds this phone or computer knows, with what the app can do for each and the battery where it
 * can be read. Pairing help is right here, not behind a link.
 */
@Composable
fun EarbudsScreen(
    earbuds: List<Pair2>,
    chosen: String?,
    loading: Boolean,
    reload: suspend () -> Unit,
    pick: (Pair2) -> Unit,
    openSettings: (() -> Unit)?,
    back: (() -> Unit)?,
) {
    LaunchedEffect(Unit) { reload() }
    val palette = LocalPalette.current
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (back == null) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LogoMark(size = 56.dp)
                Spacer(Modifier.height(12.dp))
                Text("Your earbuds", style = MaterialTheme.typography.headlineMedium, color = palette.text)
                Hint("Pick a pair to control. Earbuds paired with this device show up here by themselves.")
            }
        }
        if (loading && earbuds.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LogoLoader() }
        }
        if (earbuds.isNotEmpty()) {
            Panel(padding = 8.dp) {
                earbuds.forEach { row -> EarbudsRow(row, row.device.address == chosen) { pick(row) } }
            }
        } else if (!loading) {
            Hint("No earbuds are paired with this device yet.")
        }
        var help by remember { mutableStateOf(earbuds.isEmpty()) }
        Panel {
            SectionTitle("Don't see yours?", action = if (help) null else "Show") { help = true }
            AnimatedVisibility(help) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Catalog.pairingHelp.forEach { (name, steps) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = palette.text)
                            steps.forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                                    Text(step, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                                }
                            }
                        }
                    }
                    if (openSettings != null) Choice("Open Bluetooth settings", true, icon = BudsIcons.Bluetooth, onClick = openSettings)
                }
            }
        }
    }
    if (back != null) {
        PlaceFrame("Your earbuds", back, content = content)
    } else {
        PlaceFrame("", back = {}, showBack = false, content = content)
    }
}

@Composable
private fun EarbudsRow(row: Pair2, chosen: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clip(RoundedCornerShape(14.dp))
            .background(if (chosen) palette.accent.copy(alpha = 0.10f) else palette.card)
            .press(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.raised), contentAlignment = Alignment.Center) {
            LogoMark(size = 24.dp, color = if (row.device.connected) palette.accent else palette.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(row.device.name, style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (row.device.connected) palette.good else palette.line))
                Text(
                    (if (row.device.connected) "Connected" else "Not connected") + (row.battery?.let { " · ${it.percent}%" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
        Text(
            if (row.support == Support.Full) "Full control" else "Basics",
            style = MaterialTheme.typography.labelMedium,
            color = if (row.support == Support.Full) palette.accent else palette.muted,
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (row.support == Support.Full) palette.accent.copy(alpha = 0.12f) else palette.raised)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        if (chosen) Icon(BudsIcons.Check, "Chosen", tint = palette.accent, modifier = Modifier.size(20.dp))
    }
}
