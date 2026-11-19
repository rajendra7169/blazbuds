package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The EarPrint mark: an ear drawn as a fingerprint. Its ridges follow the ear round from the top of the helix down to
 * the lobe, open at the front where the ear canal is, and break here and there like a real print's. [progress] lights
 * them from the bowl of the ear outward, each ridge filling along its length, and fills the ring of eight segments
 * round it. While [listening], a scan line passes over the ear and sound ripples into the canal. [mirrored] turns the
 * left ear into the right one.
 */
@Composable
fun FingerprintEar(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    mirrored: Boolean = false,
    listening: Boolean = false,
    done: Boolean = false,
    ring: Boolean = true,
    segments: Int = 8,
    group: Int = 1,
) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = 50f), label = "progress")
    val facing by animateFloatAsState(if (mirrored) -1f else 1f, tween(500, easing = FastOutSlowInEasing), label = "facing")
    val glow by animateFloatAsState(if (listening || done) 1f else 0f, tween(500), label = "glow")
    val time = rememberInfiniteTransition(label = "earprint")
    val ripple by time.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "ripple")
    val scan by time.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scan")
    val shimmer by time.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val breathe by time.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier.size(size).semantics { contentDescription = "EarPrint, ${(progress * 100).toInt()} percent" }) {
        val c = center
        val outer = this.size.minDimension / 2
        val ringR = if (ring) outer * 0.74f else outer
        val bright = palette.accent
        val deep = palette.cancelling

        // A soft light behind it, warmer while a beep plays or when it is done.
        drawCircle(Brush.radialGradient(listOf(bright.copy(alpha = 0.06f + 0.14f * glow), deep.copy(alpha = 0.04f), Color.Transparent), c, outer), outer, c)

        if (ring) {
            // Thin rings outside, drifting outward while listening.
            for (k in 0 until 3) {
                val drift = if (listening && !reduce) ((ripple + k / 3f) % 1f) else k / 3f
                val r = outer * (0.85f + 0.14f * drift)
                drawCircle(bright.copy(alpha = (0.18f + 0.12f * glow) * (1f - drift * 0.8f)), r, c, style = Stroke(1.dp.toPx()))
            }
            // Eight thick segments; the one under way fills partly.
            val sweep = 360f / segments
            val stroke = ringR * minOf(0.11f, sweep * (PI.toFloat() / 180f) * 0.34f)
            val capDeg = (stroke / ringR) * (180f / PI.toFloat())
            val track = if (palette.background.luminance() < 0.5f) deep.copy(alpha = 0.22f) else deep.copy(alpha = 0.14f)
            for (i in 0 until segments) {
                // A wider gap where one group of blocks ends and the next begins.
                val gap = capDeg + if (group > 1 && i % group == 0) 7f else if (group > 1) 2.5f else 5f
                val start = -90f + i * sweep + gap / 2
                val length = sweep - gap
                val topLeft = Offset(c.x - ringR, c.y - ringR)
                val box = Size(ringR * 2, ringR * 2)
                drawArc(track, start, length, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                val fill = if (done) 1f else (shown * segments - i).coerceIn(0f, 1f)
                if (fill > 0f) {
                    drawArc(lerp(bright, deep, i / maxOf(1f, segments - 1f)), start, length * fill, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }

        // The ear itself.
        val earH = ringR * 1.36f
        val earTop = c.y - earH / 2
        val earLeft = c.x - earH * EarRidges.width / 2
        fun at(p: Offset) = Offset(earLeft + p.x * earH, earTop + p.y * earH)
        val brush = Brush.linearGradient(listOf(bright, deep), Offset(c.x, earTop), Offset(c.x, earTop + earH))
        val width = (earH * 0.026f).coerceAtLeast(1.5f)
        scale(facing, 1f, pivot = c) {
            val count = EarRidges.ridges.size
            val dim = palette.muted.copy(alpha = 0.26f + 0.08f * breathe)
            // Inner ridges first: the print grows out from the bowl of the ear.
            EarRidges.ridges.forEachIndexed { index, ridge ->
                val order = count - 1 - index
                val fill = if (done) 1f else (shown * count - order).coerceIn(0f, 1f)
                val wave = if (done && !reduce) 0.72f + 0.28f * sin(2 * PI.toFloat() * (shimmer - order / count.toFloat())) else 1f
                ridge.pieces.forEach { piece ->
                    drawPiece(piece, 0f, 1f, ::at, dim, width)
                    if (fill > 0f) {
                        // A wide faint stroke under the lit one makes it glow.
                        drawPieceBrush(piece, 0f, fill, ::at, brush, width * 3.2f, alpha = 0.16f * wave)
                        drawPieceBrush(piece, 0f, fill, ::at, brush, width, alpha = wave)
                    }
                }
            }
            // A scan line moving over the ear while a beep plays, brightening what it passes.
            if (listening && !reduce) {
                val y = earTop + earH * (0.05f + 0.9f * scan)
                val band = earH * 0.07f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, bright.copy(alpha = 0.18f), Color.Transparent), y - band, y + band),
                    Offset(c.x - earH * 0.55f, y - band), Size(earH * 1.1f, band * 2),
                )
                clipRect(c.x - earH, y - band * 0.6f, c.x + earH, y + band * 0.6f) {
                    EarRidges.ridges.forEach { ridge -> ridge.pieces.forEach { drawPiece(it, 0f, 1f, ::at, Color.White.copy(alpha = 0.85f), width) } }
                }
            }
            // The ear canal, where the sound goes in.
            val canal = at(EarRidges.canal)
            if (listening && !reduce) {
                for (k in 0 until 3) {
                    val t = (ripple + k / 3f) % 1f
                    drawCircle(bright.copy(alpha = 0.5f * (1f - t)), earH * (0.10f - 0.07f * t), canal, style = Stroke(1.5.dp.toPx()))
                }
            }
            val lit = done || shown > 0.001f || listening
            drawCircle(if (lit) bright.copy(alpha = 0.25f) else dim, earH * 0.045f, canal)
            drawCircle(if (lit) bright else dim, earH * 0.026f, canal)
        }
    }
}

/** One unbroken stretch of a ridge, as points in the ear's box (its height is 1), with where it falls along the ridge. */
private class Piece(val points: List<Offset>, val from: Float, val to: Float)

private class Ridge(val pieces: List<Piece>)

/** The ear's ridges, worked out once. Outermost first. */
private object EarRidges {
    // How far the ear reaches from its middle, every 30° round (0° to the right, 90° down).
    private val reach = floatArrayOf(0.33f, 0.33f, 0.35f, 0.45f, 0.39f, 0.27f, 0.20f, 0.25f, 0.36f, 0.41f, 0.41f, 0.37f)
    private const val COUNT = 9
    val ridges: List<Ridge>
    val canal: Offset
    val width: Float

    init {
        val raw = (0 until COUNT).map { i ->
            val f = i / (COUNT - 1f)
            val scale = 1f - i * (0.82f / (COUNT - 1))
            val from = 188f + f * 40f
            val to = 522f - f * 70f
            val shift = Offset(0.035f * f, -0.03f * f)
            List(160) { k ->
                val t = k / 159f
                val angle = from + (to - from) * t
                var r = radius(angle) * scale
                r *= 0.62f + 0.38f * smooth(0f, 0.16f, t) // the helix curls in at the top front
                r *= 1f - 0.12f * smooth(0.88f, 1f, t) // and the lobe turns in toward the face
                val a = angle * PI.toFloat() / 180f
                Offset(shift.x + cos(a) * r, shift.y + sin(a) * r)
            }
        }
        val all = raw.flatten()
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val h = maxY - minY
        width = (maxX - minX) / h
        fun norm(p: Offset) = Offset((p.x - minX) / h, (p.y - minY) / h)
        canal = norm(Offset(-0.06f, 0.04f))
        ridges = raw.mapIndexed { i, points -> Ridge(breakUp(points.map(::norm), i)) }
    }

    /** Splits a ridge where a print's ridges end or fork: a couple of short breaks, never in the same place twice. */
    private fun breakUp(points: List<Offset>, seed: Int): List<Piece> {
        var x = seed * 2654435761L + 12345L
        fun next(): Float {
            x = (x * 6364136223846793005L + 1442695040888963407L)
            return ((x ushr 33) and 0xFFFFFF).toFloat() / 0xFFFFFF
        }
        val breaks = (0 until 1 + (seed % 3)).map { 0.12f + 0.76f * next() }.sorted()
        val gapLength = 0.025f
        val pieces = mutableListOf<Piece>()
        var start = 0f
        for (b in breaks) {
            if (b - start > gapLength * 2) pieces += piece(points, start, b - gapLength / 2)
            start = b + gapLength / 2
        }
        pieces += piece(points, start, 1f)
        return pieces
    }

    private fun piece(points: List<Offset>, from: Float, to: Float): Piece {
        val a = (from * (points.size - 1)).toInt()
        val b = (to * (points.size - 1)).toInt()
        return Piece(points.subList(a, b + 1), from, to)
    }

    private fun radius(angle: Float): Float {
        val u = ((angle % 360f) + 360f) % 360f / 30f
        val i = u.toInt()
        val t = u - i
        val p0 = reach[(i + 11) % 12]
        val p1 = reach[i % 12]
        val p2 = reach[(i + 1) % 12]
        val p3 = reach[(i + 2) % 12]
        return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t)
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

/** The part of [piece] between [from] and [to] of the whole ridge, as a path. */
private fun pathOf(piece: Piece, from: Float, to: Float, at: (Offset) -> Offset): Path? {
    val lo = maxOf(from, piece.from)
    val hi = minOf(to, piece.to)
    if (hi <= lo) return null
    val n = piece.points.size - 1
    val a = (((lo - piece.from) / (piece.to - piece.from)) * n).toInt().coerceIn(0, n)
    val b = (((hi - piece.from) / (piece.to - piece.from)) * n).toInt().coerceIn(0, n)
    if (b <= a) return null
    return Path().apply {
        val first = at(piece.points[a])
        moveTo(first.x, first.y)
        for (k in a + 1..b) at(piece.points[k]).let { lineTo(it.x, it.y) }
    }
}

private fun DrawScope.drawPiece(piece: Piece, from: Float, to: Float, at: (Offset) -> Offset, color: Color, width: Float) {
    pathOf(piece, from, to, at)?.let { drawPath(it, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

private fun DrawScope.drawPieceBrush(piece: Piece, from: Float, to: Float, at: (Offset) -> Offset, brush: Brush, width: Float, alpha: Float) {
    pathOf(piece, from, to, at)?.let { drawPath(it, brush, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)
