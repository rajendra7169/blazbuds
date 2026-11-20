package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

/**
 * A tap that feels physical: the thing sinks a little as the finger lands, springs back as it lifts, and the phone
 * ticks once. Every tappable thing in the app goes through this.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.press(enabled: Boolean = true, role: Role = Role.Button, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(
        if (pressed && !reduce) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            source,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { long -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); long() } },
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

val CardShape = RoundedCornerShape(20.dp)

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            // The card drawn round rather than clipped round: nothing in it reaches the corners, and on slower phones
            // clipping every card to its rounded shape costs the graphics chip a lot while scrolling.
            .background(palette.card, CardShape)
            .border(1.dp, palette.line.copy(alpha = if (palette.dark) 0.6f else 1f), CardShape)
            .padding(padding),
        content = content,
    )
}

/** A small heading over a group, with an optional action on its right. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalPalette.current
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = palette.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(
                Modifier.press(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = palette.accent)
                Icon(BudsIcons.Forward, null, tint = palette.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A pill that is either chosen or not. */
@Composable
fun Choice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    centered: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val fill by animateColorAsState(if (selected) palette.accent else palette.raised, tween(Motion.NORMAL), label = "fill")
    val ink by animateColorAsState(if (selected) palette.onAccent else palette.text, tween(Motion.NORMAL), label = "ink")
    Row(
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .press(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row that opens something deeper: icon, title, a line of what it holds now, and an arrow. */
@Composable
fun LinkRow(icon: ImageVector, title: String, detail: String?, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .press(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A coloured circle with a white symbol, each kind of thing in its own colour.
        Box(Modifier.size(40.dp).background(color ?: palette.accent, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(BudsIcons.Forward, null, tint = palette.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun RowScope.Gap(width: Dp) = Box(Modifier.size(width, 1.dp))

/** The colours of the circles behind list icons. */
object Hue {
    val Orange = androidx.compose.ui.graphics.Color(0xFFF28A35)
    val Purple = androidx.compose.ui.graphics.Color(0xFF8B6CEF)
    val Teal = androidx.compose.ui.graphics.Color(0xFF14A99A)
    val Blue = androidx.compose.ui.graphics.Color(0xFF3D7BF0)
    val Green = androidx.compose.ui.graphics.Color(0xFF2EAD6B)
    val Slate = androidx.compose.ui.graphics.Color(0xFF6B7A90)
    val Pink = androidx.compose.ui.graphics.Color(0xFFE5578F)
    val Indigo = androidx.compose.ui.graphics.Color(0xFF5560E0)
    val Amber = androidx.compose.ui.graphics.Color(0xFFE0A13F)
    val Red = androidx.compose.ui.graphics.Color(0xFFE5534B)
}
