package com.blazify.buds.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Kind
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/**
 * The card that greets earbuds as they connect: the case opening with the buds rising out of it (or the headphones
 * unfolding), the name, and each earbud's and the case's battery once they are read. A tap opens the app.
 */
@Composable
fun ConnectCard(name: String, state: BudsState?, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    // Start closed and open on the first frame, so the lid swings up as the card arrives.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = true }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .shadow(18.dp, shape)
            .background(palette.card, shape)
            .border(1.dp, palette.line.copy(alpha = if (palette.dark) 0.6f else 1f), shape)
            .press(onClick = onOpen)
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Connected", style = MaterialTheme.typography.labelMedium, color = palette.good)
            }
            Box(Modifier.size(34.dp).background(palette.raised, CircleShape).press(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(BudsIcons.Close, "Close", tint = palette.muted, modifier = Modifier.size(16.dp))
            }
        }
        val kind = Catalog.kind(name)
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            when (kind) {
                Kind.Headphones -> HeadphonesHero(connected = opened, mode = state?.sound?.mode, height = 150.dp)
                Kind.Speaker -> DeviceArt(Kind.Speaker, size = 120.dp, active = true)
                else -> CaseHero(
                    connected = opened,
                    mode = state?.sound?.mode,
                    // Until the batteries are read both rise; after, one charging in the case stays down.
                    leftOut = state == null || (state.left != null && state.left?.charging != true) || state.overall != null,
                    rightOut = state == null || (state.right != null && state.right?.charging != true) || state.overall != null,
                    height = 150.dp,
                    soft = true,
                )
            }
        }
        when {
            state == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(86.dp)) {
                LogoLoader(size = 26.dp)
                Text("Reading the battery…", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            }
            state.left == null && state.right == null && state.overall != null -> BatteryRing(if (kind == Kind.Headphones) "Headphones" else "Earbuds", state.overall, ring = 52.dp)
            else -> BatteryRow(state.left, state.case, state.right, ring = 52.dp)
        }
    }
}
