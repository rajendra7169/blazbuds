package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

data class NavItem(val label: String, val icon: ImageVector)

/** The bar along the bottom of a phone: the chosen tab sits on a soft pill that fades in. */
@Composable
fun NavBar(items: List<NavItem>, selected: Int, onPick: (Int) -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(palette.card).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = palette.line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item -> NavButton(item, i == selected, Modifier.weight(1f)) { onPick(i) } }
        }
    }
}

/** The same tabs down the side of a tablet or computer window. */
@Composable
fun NavRail(items: List<NavItem>, selected: Int, onPick: (Int) -> Unit, top: @Composable () -> Unit = {}) {
    val palette = LocalPalette.current
    Column(
        Modifier.width(96.dp).fillMaxHeight().background(palette.card).windowInsetsPadding(WindowInsets.systemBars).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        top()
        items.forEachIndexed { i, item -> NavButton(item, i == selected, Modifier.width(80.dp).height(64.dp)) { onPick(i) } }
    }
}

@Composable
private fun NavButton(item: NavItem, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val pill by animateColorAsState(if (chosen) palette.accent.copy(alpha = if (palette.dark) 0.20f else 0.14f) else palette.card.copy(alpha = 0f), tween(Motion.NORMAL), label = "pill")
    val ink by animateColorAsState(if (chosen) palette.accent else palette.muted, tween(Motion.NORMAL), label = "ink")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).press(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(pill), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Text(item.label, style = MaterialTheme.typography.labelMedium, color = if (chosen) palette.text else palette.muted, modifier = Modifier.padding(top = 4.dp))
    }
}
