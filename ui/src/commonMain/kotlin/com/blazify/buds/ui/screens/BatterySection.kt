package com.blazify.buds.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.ui.BatteryLog
import com.blazify.buds.ui.BatterySample
import com.blazify.buds.ui.LocalPrefs
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The batteries: an alert when an earbud runs low, at a level the person picks between 10 and 40 percent, and how
 * the levels have gone over the last day or week.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun BatterySection(state: BudsState) {
    val palette = LocalPalette.current
    val log = LocalPrefs.current.battery
    var on by remember { mutableStateOf(log.alertOn) }
    var at by remember { mutableIntStateOf(log.alertAt) }
    var week by remember { mutableStateOf(false) }
    val samples = remember(state.left, state.right, state.case, state.overall) { log.history(state.name) }
    Panel {
        SectionTitle("Battery")
        SwitchRow(
            BudsIcons.Bolt, "Low battery alert", on,
            { on = it; log.alertOn = it },
            detail = if (on) "A notice when an earbud reaches $at%" else "Off",
            color = Hue.Orange,
        )
        if (on) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${BatteryLog.MIN}%", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                Slider(
                    value = at.toFloat(),
                    onValueChange = { at = (it / 5).toInt() * 5 },
                    onValueChangeFinished = { log.alertAt = at },
                    valueRange = BatteryLog.MIN.toFloat()..BatteryLog.MAX.toFloat(),
                    steps = (BatteryLog.MAX - BatteryLog.MIN) / 5 - 1,
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent, inactiveTrackColor = palette.raised, activeTickColor = palette.onAccent, inactiveTickColor = palette.muted),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text("${BatteryLog.MAX}%", style = MaterialTheme.typography.labelMedium, color = palette.muted)
            }
            if (state.left != null && state.left?.percent?.rem(20) == 0 && state.model.startsWith("A39")) {
                Hint("These earbuds tell their battery in steps of 20%, so the alert comes at the step at or below this.")
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleMedium, color = palette.text, modifier = Modifier.weight(1f))
            Choice("Day", !week) { week = false }
            Spacer(Modifier.size(6.dp))
            Choice("Week", week) { week = true }
        }
        Spacer(Modifier.height(8.dp))
        val now = Clock.System.now().epochSeconds
        val span = if (week) 7 * 24 * 3600L else 24 * 3600L
        val shown = samples.filter { now - it.at <= span }
        if (shown.size < 2) {
            Hint("The graph fills in as you use your earbuds: a look every quarter hour or so, kept for a week.")
        } else {
            HistoryChart(shown, now - span, now, week)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                Key(palette.accent, "Left")
                Key(palette.cancelling, "Right")
                if (shown.any { it.case >= 0 }) Key(palette.muted, "Case", dashed = true)
            }
        }
    }
}

/** Each level as a line over time, 0 to 100 percent, with faint lines at 20, 50 and 80 and the times along the foot. */
@Composable
private fun HistoryChart(samples: List<BatterySample>, from: Long, to: Long, week: Boolean) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = palette.muted)
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val left = 30.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val w = size.width - left
        fun x(t: Long) = left + w * ((t - from).toFloat() / (to - from).toFloat())
        fun y(p: Int) = bottom * (1 - p / 100f)
        for (p in listOf(20, 50, 80)) {
            drawLine(palette.line, Offset(left, y(p)), Offset(size.width, y(p)), strokeWidth = 1.dp.toPx())
            val t = measurer.measure("$p", label)
            drawText(t, topLeft = Offset(0f, y(p) - t.size.height / 2))
        }
        fun line(pick: (BatterySample) -> Int, color: Color, dashed: Boolean = false) {
            var path: Path? = null
            var last: BatterySample? = null
            for (s in samples) {
                val v = pick(s)
                // A gap of more than an hour (or three over a week) means nothing was heard: break the line there.
                val gap = last != null && s.at - last.at > (if (week) 3 * 3600 else 3600)
                if (v < 0 || gap) {
                    path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)) }
                    path = null
                }
                if (v >= 0) {
                    val p = Offset(x(s.at), y(v))
                    path = (path ?: Path().apply { moveTo(p.x, p.y) }).apply { lineTo(p.x, p.y) }
                }
                last = s
            }
            path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)) }
        }
        line({ it.case }, palette.muted, dashed = true)
        line({ it.right }, palette.cancelling)
        line({ it.left }, palette.accent)
        // Along the foot: "now" on the right, and how far back on the left.
        val start = measurer.measure(if (week) "7 days ago" else "24 h ago", label)
        drawText(start, topLeft = Offset(left, size.height - start.size.height))
        val end = measurer.measure("now", label)
        drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))
    }
}

@Composable
private fun Key(color: Color, name: String, dashed: Boolean = false) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 16.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).let { if (dashed) it else it }.then(Modifier)) {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}
