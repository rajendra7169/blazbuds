package com.blazify.buds.ui.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * One equalizer band: no change sits in the middle, and the fill grows from there towards a boost on the right or a
 * cut on the left, so +2 dB reads as a small lift, not as a nearly full bar. Moves in steps of 0.5 dB.
 */
@Composable
fun BandSlider(
    value: Double,
    min: Double,
    max: Double,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalPalette.current
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    fun at(x: Float, width: Float): Double {
        val raw = min + (x / width).coerceIn(0f, 1f) * (max - min)
        return (raw * 2).roundToInt() / 2.0
    }
    Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .semantics {
                contentDescription = label
                setProgress { target ->
                    change(target.toDouble().coerceIn(min, max))
                    done()
                    true
                }
            }
            .pointerInput(min, max) {
                detectTapGestures { change(at(it.x, size.width.toFloat())); done() }
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures(
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                ) { input, _ -> change(at(input.position.x, size.width.toFloat())) }
            },
    ) {
        val track = 6.dp.toPx()
        val y = size.height / 2
        val knob = 9.dp.toPx()
        val left = knob
        val right = size.width - knob
        fun x(db: Double) = left + ((db - min) / (max - min)).toFloat() * (right - left)
        drawRoundRect(palette.raised, Offset(left, y - track / 2), Size(right - left, track), CornerRadius(track / 2))
        val zero = x(0.0)
        val now = x(value.coerceIn(min, max))
        drawRoundRect(
            palette.accent,
            Offset(minOf(zero, now), y - track / 2),
            Size(kotlin.math.abs(now - zero).coerceAtLeast(1f), track),
            CornerRadius(track / 2),
        )
        drawLine(palette.muted.copy(alpha = 0.5f), Offset(zero, y - 9.dp.toPx()), Offset(zero, y + 9.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        drawCircle(palette.card, knob, Offset(now, y))
        drawCircle(palette.accent, knob, Offset(now, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
    }
}
