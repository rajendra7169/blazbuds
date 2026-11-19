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
        BatteryRing("Left", left, ring)
        BatteryRing("Case", case, ring)
        BatteryRing("Right", right, ring)
    }
}

@Composable
fun BatteryRing(label: String, battery: Battery?, ring: Dp = 56.dp) {
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
    val spoken = if (battery == null) "$label: not connected" else "$label battery ${battery.percent} percent" + if (battery.charging) ", charging" else ""
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ring)) {
                val width = 5.dp.toPx()
                val inset = width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - width, size.height - width)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(palette.raised, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
                drawArc(color, -90f, 360f * fill, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }
            if (battery?.charging == true) {
                Icon(BudsIcons.Bolt, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    if (battery == null) "–" else "${battery.percent}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = palette.text,
                )
            }
        }
        Text(
            if (battery?.charging == true) "$label · ${battery.percent}%" else label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.semantics { },
        )
    }
}

