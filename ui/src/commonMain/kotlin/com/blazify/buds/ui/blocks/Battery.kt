package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.Battery
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

/** Left, case and right, each a ring that fills to its level and settles with a small bounce when it changes. */
@Composable
fun BatteryRow(left: Battery?, case: Battery?, right: Battery?, modifier: Modifier = Modifier, ring: Dp = 56.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BatteryRing("Left", left, ring, BudsIcons.Earbuds, mirrored = true)
        // The case unheard from reads 0%, as the brand apps show it; a missing earbud keeps its dash.
        BatteryRing("Case", case, ring, BudsIcons.Case, missing = "0%")
        BatteryRing("Right", right, ring, BudsIcons.Earbuds)
    }
}

@Composable
fun BatteryRing(label: String, battery: Battery?, ring: Dp = 56.dp, picture: androidx.compose.ui.graphics.vector.ImageVector? = null, mirrored: Boolean = false, missing: String = "–") {
    val palette = LocalPalette.current
    val level = battery?.percent ?: 0
    val fill by animateFloatAsState(level / 100f, spring(dampingRatio = 0.6f, stiffness = 140f), label = "fill")
    val color by animateColorAsState(
        when {
            battery == null -> palette.line
            battery.charging -> palette.accent
            level <= 20 -> palette.low
            level <= 40 -> palette.warn
            else -> palette.good
        },
        tween(Motion.SLOW),
        label = "color",
    )
    val old = battery?.seenAt != null
    val spoken = if (battery == null) "$label: not connected" else "$label battery ${battery.percent} percent" + (if (battery.charging) ", charging" else "") + (battery.seenAt?.let { ", last seen " + com.blazify.buds.ui.ago(it) } ?: "")
    // A level last heard a while ago is drawn faded, with when it was heard underneath.
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }.graphicsLayer { alpha = if (old) 0.55f else 1f }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ring)) {
                val width = 5.dp.toPx()
                val inset = width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - width, size.height - width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(palette.raised, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
                drawArc(color, -90f, 360f * fill, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
            if (picture != null) {
                // A small picture of what this ring is for, as on the best earbud apps.
                Icon(
                    picture, null,
                    tint = if (battery == null) palette.muted else palette.text,
                    modifier = Modifier.size(ring * 0.42f).graphicsLayer { scaleX = if (mirrored) -1f else 1f },
                )
                if (battery?.charging == true) {
                    Box(Modifier.align(Alignment.BottomEnd).size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                        Icon(BudsIcons.Bolt, null, tint = palette.onAccent, modifier = Modifier.size(12.dp))
                    }
                }
            } else if (battery?.charging == true) {
                Icon(BudsIcons.Bolt, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    if (battery == null) "–" else "${battery.percent}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = palette.text,
                )
            }
        }
        if (picture != null) {
            Text(
                if (battery == null) missing else "${battery.percent}%",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = palette.text,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(battery?.seenAt?.let { "$label · " + com.blazify.buds.ui.ago(it) } ?: label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        } else {
            Text(
                if (battery?.charging == true) "$label · ${battery.percent}%" else label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
        }
    }
}

