package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The equalizer as one picture you play with: a smooth curve through a point for each band, drawn over faint lines at
 * +6, 0 and −6 dB. Drag a point up or down, or tap where you want it; a bubble shows its value while you hold it, and
 * the earbuds get the change when you let go.
 */
@Composable
fun EqEditor(
    gains: List<Double>,
    frequencies: List<Int>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    min: Double = -6.0,
    max: Double = 6.0,
    onChange: (List<Double>) -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    var held by remember { mutableIntStateOf(-1) }
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val latest by rememberUpdatedState(gains)
    val shown = gains.mapIndexed { i, g -> animateFloatAsState(g.toFloat(), spring(dampingRatio = 0.75f, stiffness = 500f), label = "b$i").value }
    val left = 34f
    val bottomPad = 26f
    val topPad = 30f

    fun band(x: Float, width: Float): Int {
        val inner = width - left - 12f
        return (((x - left) / inner) * (gains.size - 1)).roundToInt().coerceIn(0, gains.size - 1)
    }

    fun value(y: Float, h: Float, d: Float): Double {
        val top = topPad * d
        val bottom = h - bottomPad * d
        val raw = max - ((y - top) / (bottom - top)) * (max - min)
        return ((raw * 2).roundToInt() / 2.0).coerceIn(min, max)
    }

    Canvas(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = "Equalizer: " + gains.joinToString { "${(it * 10).roundToInt() / 10.0} dB" } }
            .pointerInput(editable, gains.size) {
                if (!editable) return@pointerInput
                detectTapGestures { at ->
                    val i = band(at.x / density, size.width / density)
                    change(latest.toMutableList().also { it[i] = value(at.y, size.height.toFloat(), density) })
                    done()
                }
            }
            .pointerInput(editable, gains.size) {
                if (!editable) return@pointerInput
                detectDragGestures(
                    onDragStart = { at -> held = band(at.x / density, size.width / density) },
                    onDragEnd = { held = -1; done() },
                    onDragCancel = { held = -1; done() },
                ) { input, _ ->
                    val i = held
                    if (i >= 0) change(latest.toMutableList().also { it[i] = value(input.position.y, size.height.toFloat(), density) })
                }
            },
    ) {
        val l = left.dp.toPx()
        val r = size.width - 12.dp.toPx()
        val top = topPad.dp.toPx()
        val bottom = size.height - bottomPad.dp.toPx()
        fun y(db: Float) = top + (1f - ((db - min.toFloat()) / (max - min).toFloat())) * (bottom - top)
        val step = (r - l) / (shown.size - 1).coerceAtLeast(1)
        val small = TextStyle(fontSize = 10.sp, color = palette.muted)
        // The scale: +6, 0 and −6 dB, the middle one stronger.
        listOf(max.toFloat(), 0f, min.toFloat()).forEach { db ->
            drawLine(
                if (db == 0f) palette.line else palette.line.copy(alpha = 0.6f),
                Offset(l, y(db)), Offset(r, y(db)),
                strokeWidth = if (db == 0f) 1.4.dp.toPx() else 1.dp.toPx(),
                pathEffect = if (db == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            label(measurer, (if (db > 0) "+" else if (db < 0) "−" else "") + "${abs(db).toInt()}", Offset(2.dp.toPx(), y(db) - 7.dp.toPx()), small)
        }
        // A faint line for each band, and its frequency under it.
        frequencies.forEachIndexed { i, hz ->
            val x = l + i * step
            drawLine(palette.line.copy(alpha = 0.35f), Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
            val text = if (hz >= 1000) "${hz / 1000}${if (hz % 1000 >= 100) "." + (hz % 1000) / 100 else ""}k" else "$hz"
            val laid = measurer.measure(text, small)
            drawText(laid, topLeft = Offset(x - laid.size.width / 2f, bottom + 6.dp.toPx()))
        }
        if (shown.size < 2) return@Canvas
        val points = shown.mapIndexed { i, g -> Offset(l + i * step, y(g)) }
        val curve = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points.getOrElse(i - 1) { points[i] }
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points.getOrElse(i + 2) { p2 }
                cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
            }
        }
        // The area between the curve and no change, coloured where the sound is lifted.
        val area = Path().apply {
            addPath(curve)
            lineTo(points.last().x, y(0f))
            lineTo(points.first().x, y(0f))
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.30f), palette.accent.copy(alpha = 0.04f), palette.cancelling.copy(alpha = 0.18f)), top, bottom))
        val ink = if (editable) Brush.horizontalGradient(listOf(palette.accent, palette.cancelling), l, r) else Brush.horizontalGradient(listOf(palette.muted, palette.muted))
        drawPath(curve, ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        // The handles.
        points.forEachIndexed { i, p ->
            val big = i == held
            drawCircle(palette.accent.copy(alpha = if (big) 0.25f else 0f), if (big) 18.dp.toPx() else 0f, p)
            drawCircle(palette.card, (if (big) 9 else 7).dp.toPx(), p)
            drawCircle(if (editable) palette.accent else palette.muted, (if (big) 9 else 7).dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
        }
        // The held point's value, in a bubble above it.
        if (held in points.indices) {
            val p = points[held]
            val db = shown[held]
            val text = (if (db > 0) "+" else if (db < 0) "−" else "") + "${(abs(db) * 10).roundToInt() / 10.0} dB"
            val laid = measurer.measure(text, TextStyle(fontSize = 12.sp, color = palette.onAccent))
            val w = laid.size.width + 16.dp.toPx()
            val h = laid.size.height + 8.dp.toPx()
            val at = Offset((p.x - w / 2).coerceIn(0f, size.width - w), (p.y - h - 16.dp.toPx()).coerceAtLeast(0f))
            drawRoundRect(palette.accent, at, Size(w, h), CornerRadius(h / 2))
            drawText(laid, topLeft = Offset(at.x + 8.dp.toPx(), at.y + 4.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(measurer.measure(text, style), topLeft = at)
}
