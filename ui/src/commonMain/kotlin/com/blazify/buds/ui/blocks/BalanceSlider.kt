package com.blazify.buds.ui.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.eq.Balance
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a balance reads: "Centre", or which ear the sound leans to and by how much. */
fun balanceLabel(db: Double): String = when {
    abs(db) < 0.25 -> "Centre"
    db < 0 -> "Left, ${number(-db)} dB"
    else -> "Right, ${number(db)} dB"
}

private fun number(x: Double) = if (x == kotlin.math.floor(x)) x.toInt().toString() else x.toString()

/**
 * Left and right balance: L on one end, R on the other, a mark in the middle the knob settles on when near it. It
 * moves in half decibels under the finger and is sent when let go.
 */
@Composable
fun BalanceSlider(balance: Double, enabled: Boolean, modifier: Modifier = Modifier, onDone: (Double) -> Unit) {
    val palette = LocalPalette.current
    var moving by remember { mutableStateOf<Double?>(null) }
    val shown = moving ?: balance
    val done by rememberUpdatedState(onDone)
    fun at(x: Float, width: Float, knob: Float): Double {
        val t = ((x - knob) / (width - 2 * knob)).coerceIn(0f, 1f)
        val raw = -Balance.MAX + t * 2 * Balance.MAX
        // A detent at the centre: near it is the centre.
        return if (abs(raw) < 0.75) 0.0 else (raw * 2).roundToInt() / 2.0
    }
    fun finish() {
        moving?.let { if (it != balance) done(it) }
        moving = null
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("L", style = MaterialTheme.typography.titleMedium, color = if (shown < 0) palette.accent else palette.muted, modifier = Modifier.width(24.dp))
            Canvas(
                Modifier.weight(1f).height(40.dp)
                    .semantics {
                        contentDescription = "Balance"
                        stateDescription = balanceLabel(shown)
                        if (enabled) setProgress { target -> done(((target * 2).roundToInt() / 2.0).coerceIn(-Balance.MAX, Balance.MAX)); true }
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { moving = at(it.x, size.width.toFloat(), 11.dp.toPx()); finish() }
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(onDragEnd = { finish() }, onDragCancel = { finish() }) { input, _ ->
                            moving = at(input.position.x, size.width.toFloat(), 11.dp.toPx())
                        }
                    },
            ) {
                val track = 6.dp.toPx()
                val knob = 11.dp.toPx()
                val y = size.height / 2
                val left = knob
                val right = size.width - knob
                fun x(db: Double) = left + ((db + Balance.MAX) / (2 * Balance.MAX)).toFloat() * (right - left)
                drawRoundRect(palette.raised, Offset(left, y - track / 2), Size(right - left, track), CornerRadius(track / 2))
                // Small ticks every 2 dB, the centre one tall.
                for (step in -3..3) {
                    val tall = step == 0
                    val h = if (tall) 12.dp.toPx() else 5.dp.toPx()
                    drawLine(palette.muted.copy(alpha = if (tall) 0.7f else 0.35f), Offset(x(step * 2.0), y - track / 2 - h), Offset(x(step * 2.0), y - track / 2 - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                }
                val zero = x(0.0)
                val now = x(shown)
                drawRoundRect(palette.accent, Offset(minOf(zero, now), y - track / 2), Size(abs(now - zero).coerceAtLeast(1f), track), CornerRadius(track / 2))
                drawCircle(palette.card, knob, Offset(now, y))
                drawCircle(if (enabled) palette.accent else palette.muted, knob, Offset(now, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            }
            Text("R", style = MaterialTheme.typography.titleMedium, color = if (shown > 0) palette.accent else palette.muted, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
        }
        Text(balanceLabel(shown), style = MaterialTheme.typography.labelLarge, color = palette.text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
