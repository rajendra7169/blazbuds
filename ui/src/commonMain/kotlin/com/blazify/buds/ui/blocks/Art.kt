package com.blazify.buds.ui.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.catalog.Kind
import com.blazify.buds.ui.theme.LocalPalette

/**
 * A device's picture, drawn rather than shipped as an image: earbuds with stems, round earbuds, headphones or a
 * speaker, in soft shades of the card colour with an accent where the sound comes out.
 */
@Composable
fun DeviceArt(kind: Kind, modifier: Modifier = Modifier, size: Dp = 64.dp, active: Boolean = true) {
    val palette = LocalPalette.current
    val body = if (palette.dark) Color(0xFF2A323A) else Color(0xFFE3E8EC)
    val edge = if (palette.dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    val accent = if (active) palette.accent else palette.muted
    Canvas(modifier.size(size)) {
        when (kind) {
            Kind.StemBuds -> stemPair(body, edge, accent)
            Kind.RoundBuds -> roundPair(body, edge, accent)
            Kind.Headphones -> headphones(body, edge, accent)
            Kind.Speaker -> speaker(body, edge, accent)
        }
    }
}

/** A pair of stem earbuds, mirrored, from the logo's own earbud. */
fun DrawScope.stemPair(body: Color, edge: Color, accent: Color) {
    val side = size.minDimension * 0.6f
    listOf(-1f to size.width * 0.27f, 1f to size.width * 0.73f).forEach { (dir, cx) ->
        translate(cx - side / 2, size.height * 0.5f - side / 2) {
            scale(if (dir < 0) -1f else 1f, 1f, pivot = Offset(side / 2, side / 2)) {
                rotate(-20f, pivot = Offset(side * 0.43f, side * 0.6f)) {
                    val shape = LogoShape.path(LogoShape.OUTLINE, Size(side, side))
                    drawPath(shape, body)
                    drawPath(shape, edge, style = Stroke(side * 0.02f))
                    drawPath(LogoShape.path(LogoShape.OPENING, Size(side, side)), accent.copy(alpha = 0.85f))
                }
            }
        }
    }
}

private fun DrawScope.roundPair(body: Color, edge: Color, accent: Color) {
    val r = size.minDimension * 0.17f
    listOf(size.width * 0.29f, size.width * 0.71f).forEachIndexed { i, cx ->
        val c = Offset(cx, size.height * 0.52f)
        drawCircle(body, r, c)
        drawCircle(edge, r, c, style = Stroke(r * 0.08f))
        val tip = Offset(cx + if (i == 0) r * 0.55f else -r * 0.55f, c.y - r * 0.35f)
        drawCircle(accent.copy(alpha = 0.85f), r * 0.32f, tip)
    }
}

private fun DrawScope.headphones(body: Color, edge: Color, accent: Color) {
    val w = size.width
    val h = size.height
    val band = Path().apply {
        moveTo(w * 0.22f, h * 0.6f)
        cubicTo(w * 0.2f, h * 0.12f, w * 0.8f, h * 0.12f, w * 0.78f, h * 0.6f)
    }
    drawPath(band, body, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
    listOf(w * 0.22f, w * 0.78f).forEach { cx ->
        val cup = Size(w * 0.2f, h * 0.34f)
        drawRoundRect(body, Offset(cx - cup.width / 2, h * 0.5f), cup, CornerRadius(cup.width / 2))
        drawRoundRect(edge, Offset(cx - cup.width / 2, h * 0.5f), cup, CornerRadius(cup.width / 2), style = Stroke(w * 0.015f))
        drawLine(accent.copy(alpha = 0.85f), Offset(cx, h * 0.58f), Offset(cx, h * 0.76f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.speaker(body: Color, edge: Color, accent: Color) {
    val w = size.width
    val h = size.height
    val box = Size(w * 0.66f, h * 0.44f)
    val at = Offset((w - box.width) / 2, (h - box.height) / 2)
    drawRoundRect(body, at, box, CornerRadius(box.height / 2))
    drawRoundRect(edge, at, box, CornerRadius(box.height / 2), style = Stroke(w * 0.015f))
    for (row in 0..2) for (col in 0..6) {
        drawCircle(accent.copy(alpha = 0.55f), w * 0.016f, Offset(at.x + box.width * (0.2f + col * 0.1f), at.y + box.height * (0.3f + row * 0.2f)))
    }
}

/** Which picture the add art shows: headphones (0) or earbuds (1), changing by itself every few seconds. */
@Composable
fun rememberDeviceCycle(): Int {
    var showing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2_800)
            showing = 1 - showing
        }
    }
    return showing
}

/**
 * The add picture, with a plus badge: headphones and earbuds taking turns, each settling in as the other fades and
 * shrinks away, so it is plain the app is for both. With less motion they simply cross-fade.
 */
@Composable
fun AddArt(modifier: Modifier = Modifier, size: Dp = 120.dp, showing: Int = rememberDeviceCycle()) {
    val palette = LocalPalette.current
    val body = if (palette.dark) Color(0xFF2A323A) else Color(0xFFE3E8EC)
    val edge = if (palette.dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    val still = LocalReduceMotion.current
    val t by androidx.compose.animation.core.animateFloatAsState(
        showing.toFloat(),
        androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "device",
    )
    androidx.compose.foundation.layout.Box(modifier.size(size)) {
        // Headphones fade and sink as the earbuds rise into place, and back again.
        listOf(1f - t to 0, t to 1).forEach { (weight, which) ->
            if (weight > 0.01f) {
                Canvas(
                    Modifier.size(size).graphicsLayer {
                        alpha = weight
                        val grow = if (still) 1f else 0.82f + 0.18f * weight
                        scaleX = grow
                        scaleY = grow
                        translationY = if (still) 0f else (1f - weight) * this.size.height * 0.08f
                    },
                ) {
                    if (which == 0) headphones(body, edge, palette.accent) else stemPair(body, edge, palette.accent)
                }
            }
        }
        Canvas(Modifier.size(size)) {
            val r = size.toPx() * 0.15f
            val c = Offset(this.size.width * 0.8f, this.size.height * 0.24f)
            drawCircle(palette.accent, r, c)
            val arm = r * 0.5f
            drawLine(palette.onAccent, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
            drawLine(palette.onAccent, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
        }
    }
}

/** "Pair your headphones" or "Pair your earbuds", the last word changing with the add picture. */
@Composable
fun DeviceWord(prefix: String, showing: Int, style: androidx.compose.ui.text.TextStyle, color: Color) {
    androidx.compose.foundation.layout.Row {
        androidx.compose.material3.Text(prefix, style = style, color = color)
        androidx.compose.animation.AnimatedContent(
            showing,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(400)) { it / 2 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) +
                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(300)) { -it / 2 })
            },
            label = "word",
        ) { which ->
            androidx.compose.material3.Text(if (which == 0) "headphones" else "earbuds", style = style, color = color)
        }
    }
}

