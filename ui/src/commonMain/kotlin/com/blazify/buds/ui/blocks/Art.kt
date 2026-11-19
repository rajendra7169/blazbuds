package com.blazify.buds.ui.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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

/** The add picture: headphones with a plus badge, for adding a device. */
@Composable
fun AddArt(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val palette = LocalPalette.current
    val body = if (palette.dark) Color(0xFF2A323A) else Color(0xFFE3E8EC)
    val edge = if (palette.dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.10f)
    Canvas(modifier.size(size)) {
        headphones(body, edge, palette.accent)
        val r = size.toPx() * 0.15f
        val c = Offset(this.size.width * 0.8f, this.size.height * 0.24f)
        drawCircle(palette.accent, r, c)
        val arm = r * 0.5f
        drawLine(palette.onAccent, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
        drawLine(palette.onAccent, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    }
}

