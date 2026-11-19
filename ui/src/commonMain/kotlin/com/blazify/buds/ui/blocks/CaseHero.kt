package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

/** The colours a drawn device is made of: a pearl white one in the light look, graphite in the dark. */
class Finish(
    val top: Color,
    val mid: Color,
    val low: Color,
    val inside: Color,
    val insideLow: Color,
    val tip: Color,
    val shine: Float,
)

val Pearl = Finish(Color(0xFFFFFFFF), Color(0xFFEDEFF2), Color(0xFFC7CDD5), Color(0xFFB9C0C9), Color(0xFF8F98A4), Color(0xFFD4D8DE), 0.9f)
val Graphite = Finish(Color(0xFF5A616B), Color(0xFF353A42), Color(0xFF1B1E23), Color(0xFF15181C), Color(0xFF07080A), Color(0xFF6B717B), 0.45f)

/**
 * Earbuds in their charging case, drawn with light and shade rather than shipped as pictures, for any pair. Closed
 * while they are away; when they connect the lid swings up and back and the earbuds rise out and float above it. A
 * bud that is charging stays in its slot. The light on the front and the glow behind take the listening mode's colour.
 */
@Composable
fun CaseHero(
    connected: Boolean,
    mode: ListeningMode?,
    leftOut: Boolean,
    rightOut: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    still: Boolean = false,
) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current || still
    val open by animateFloatAsState(if (connected) 1f else 0f, if (reduce) tween(Motion.NORMAL) else tween(900, easing = FastOutSlowInEasing), label = "lid")
    val leftRise by animateFloatAsState(if (connected && leftOut) 1f else 0f, if (reduce) tween(Motion.NORMAL) else spring(dampingRatio = 0.62f, stiffness = 45f), label = "left")
    val rightRise by animateFloatAsState(if (connected && rightOut) 1f else 0f, if (reduce) tween(Motion.NORMAL) else spring(dampingRatio = 0.62f, stiffness = 38f), label = "right")
    val tint by animateColorAsState(
        when {
            !connected -> Color.Transparent
            mode == ListeningMode.NoiseCancelling -> palette.cancelling
            mode == ListeningMode.Transparency -> palette.transparency
            else -> palette.accent
        },
        tween(Motion.SLOW * 2),
        label = "tint",
    )
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val finish = if (palette.dark) Graphite else Pearl
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = if (connected) "Earbuds connected" else "Earbuds not connected" }) {
        drawCase(finish, open, leftRise, rightRise, if (reduce) 0f else bob, tint, palette.dark)
    }
}

/** The whole scene, in units of a hundredth of the drawing's width, so it scales to any size. */
internal fun DrawScope.drawCase(f: Finish, open: Float, leftRise: Float, rightRise: Float, bob: Float, tint: Color, dark: Boolean) {
    val u = minOf(size.width, size.height * 1.25f) / 78f
    val cx = size.width / 2
    val seam = size.height * 0.56f
    val halfW = 22f * u
    val baseH = 23f * u
    val lidH = 14f * u

    // Glow behind, in the mode's colour.
    val glowR = 46f * u
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = tint.alpha * 0.30f), tint.copy(alpha = tint.alpha * 0.07f), Color.Transparent), Offset(cx, seam - 6f * u), glowR), glowR, Offset(cx, seam - 6f * u))
    // Soft shadow on the ground.
    val shadowW = halfW * 2.3f
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = if (dark) 0.55f else 0.20f), Color.Transparent), Offset(cx, seam + baseH + 1.5f * u), shadowW / 2),
        Offset(cx - shadowW / 2, seam + baseH - 1.5f * u),
        Size(shadowW, 6f * u),
    )

    val swing = (open * 1.15f).coerceAtMost(1f)
    // The lid, once it has swung past upright: its inside stands behind the earbuds.
    if (swing > 0.5f) {
        val t = (swing - 0.5f) / 0.5f
        val h = lidH * 1.05f * t + 2f * u
        val top = seam - 1.2f * u - h - 3f * u * t
        val rect = Rect(cx - halfW, top, cx + halfW, seam - 1.2f * u)
        val shell = RoundRect(rect, CornerRadius(12f * u * t + 2f * u), CornerRadius(12f * u * t + 2f * u), CornerRadius(2f * u), CornerRadius(2f * u))
        drawPath(Path().apply { addRoundRect(shell) }, Brush.verticalGradient(listOf(f.mid, f.low), rect.top, rect.bottom))
        val inner = RoundRect(Rect(rect.left + 2f * u, rect.top + 2f * u, rect.right - 2f * u, rect.bottom), CornerRadius(10f * u * t + 1f * u), CornerRadius(10f * u * t + 1f * u), CornerRadius(1f * u), CornerRadius(1f * u))
        drawPath(Path().apply { addRoundRect(inner) }, Brush.verticalGradient(listOf(f.insideLow, f.inside), inner.top, inner.bottom))
        // A sheen across the lid's hollow.
        drawOval(Color.White.copy(alpha = 0.10f * f.shine * t), Offset(cx - halfW * 0.6f, rect.top + 3f * u), Size(halfW * 0.9f, h * 0.35f))
    }
    // The opening of the case, seen from just above, as the lid lifts.
    if (open > 0.02f) {
        val depth = 4.2f * u * swing
        val mouth = RoundRect(Rect(cx - halfW + 1.8f * u, seam - depth, cx + halfW - 1.8f * u, seam + 1.5f * u), CornerRadius(3f * u))
        drawPath(Path().apply { addRoundRect(mouth) }, Brush.verticalGradient(listOf(f.insideLow, f.inside), mouth.top, mouth.bottom))
    }
    // The earbuds, rising out of their slots; the front of the case hides what is still inside.
    val drift = bob * 0.8f * u
    if (open > 0.05f) {
        earbud(f, Offset(cx - 9.5f * u, seam + 6f * u - leftRise * 19f * u + drift * leftRise), u, mirrored = true, tilt = -10f)
        earbud(f, Offset(cx + 9.5f * u, seam + 6.8f * u - rightRise * 17f * u - drift * rightRise), u, mirrored = false, tilt = 10f)
    }
    // The base: a soft rounded tub, lit from the upper left.
    val base = RoundRect(Rect(cx - halfW, seam, cx + halfW, seam + baseH), CornerRadius(3f * u), CornerRadius(3f * u), CornerRadius(14f * u), CornerRadius(14f * u))
    val basePath = Path().apply { addRoundRect(base) }
    drawPath(basePath, Brush.verticalGradient(listOf(f.top, f.mid, f.low), base.top, base.bottom))
    clipPath(basePath) {
        // Round on the sides: darker towards each edge.
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.30f)), base.left, base.right))
        // A long soft highlight down the left, as from a window.
        drawOval(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f * f.shine), Color.Transparent), cx - halfW * 0.8f, cx - halfW * 0.46f),
            Offset(cx - halfW * 0.8f, seam - 2f * u),
            Size(halfW * 0.34f, baseH * 0.8f),
        )
        // The lip along the top edge catches the light.
        drawLine(Color.White.copy(alpha = 0.6f * f.shine), Offset(base.left + 3f * u, seam + 0.6f * u), Offset(base.right - 3f * u, seam + 0.6f * u), strokeWidth = 0.7f * u)
        // Bounce light along the bottom.
        drawOval(Color.White.copy(alpha = 0.05f * f.shine), Offset(cx - halfW * 0.6f, seam + baseH - 4f * u), Size(halfW * 1.2f, 3f * u))
    }
    // The status light, a little pill on the front.
    val led = Rect(cx - 2.6f * u, seam + 6f * u, cx + 2.6f * u, seam + 7.4f * u)
    val lit = if (tint.alpha > 0.01f) tint else (if (dark) Color(0xFF2A2F36) else Color(0xFFDCE0E5))
    drawCircle(Brush.radialGradient(listOf(lit.copy(alpha = 0.45f * tint.alpha), Color.Transparent), led.center, 6f * u), 6f * u, led.center)
    drawRoundRect(lit, led.topLeft, led.size, CornerRadius(0.7f * u))
    drawRoundRect(Color.White.copy(alpha = 0.55f * tint.alpha), Offset(led.left + 0.8f * u, led.top + 0.3f * u), Size(led.width - 1.6f * u, 0.35f * u), CornerRadius(0.2f * u))

    // The lid, closed or starting to lift: the top of the shell, with the seam shadow under it.
    if (swing <= 0.5f) {
        val t = swing / 0.5f
        val shown = lidH * (1f - 0.75f * t)
        val bottom = seam - 0.4f * u - 4f * u * t
        val rect = Rect(cx - halfW, bottom - shown, cx + halfW, bottom)
        val lid = RoundRect(rect, CornerRadius(12f * u * (1f - 0.5f * t)), CornerRadius(12f * u * (1f - 0.5f * t)), CornerRadius(2.5f * u), CornerRadius(2.5f * u))
        val lidPath = Path().apply { addRoundRect(lid) }
        drawPath(lidPath, Brush.verticalGradient(listOf(f.top, f.mid), rect.top, rect.bottom))
        clipPath(lidPath) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.24f)), rect.left, rect.right))
            drawOval(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f * f.shine), Color.Transparent), rect.top, rect.top + shown * 0.7f),
                Offset(cx - halfW * 0.85f, rect.top + shown * 0.08f),
                Size(halfW * 1.2f, shown * 0.6f),
            )
        }
        // The seam: a fine dark line where lid meets base.
        drawLine(Color.Black.copy(alpha = if (dark) 0.6f else 0.18f), Offset(cx - halfW + 1f * u, seam - 0.1f * u), Offset(cx + halfW - 1f * u, seam - 0.1f * u), strokeWidth = 0.5f * u)
        // The mark, pressed lightly into the lid.
        if (t < 0.4f) {
            val side = 7f * u
            val at = Offset(cx - side / 2, rect.top + shown * 0.5f - side / 2)
            val mark = LogoShape.path(LogoShape.OUTLINE, Size(side, side)).apply { translate(at) }
            drawPath(mark, Color.Black.copy(alpha = (if (dark) 0.35f else 0.08f) * (1f - t / 0.4f)))
        }
    }
}

/**
 * One earbud as they really look: a bean-shaped body, the silicone tip on its inner side, and the stem coming down
 * from its outer edge, all lit from the upper left. [head] is the middle of the body.
 */
private fun DrawScope.earbud(f: Finish, head: Offset, u: Float, mirrored: Boolean, tilt: Float) {
    rotate(tilt, pivot = head) {
        val dir = if (mirrored) 1f else -1f
        // The stem, hanging from the outer side of the body.
        val stemW = 4.6f * u
        val stemX = head.x - dir * 2.2f * u
        val stem = RoundRect(Rect(stemX - stemW / 2, head.y, stemX + stemW / 2, head.y + 15.5f * u), CornerRadius(stemW / 2))
        val stemPath = Path().apply { addRoundRect(stem) }
        drawPath(stemPath, Brush.horizontalGradient(listOf(f.low, f.top, f.mid, f.low), stem.left, stem.right))
        clipPath(stemPath) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), stem.top, stem.bottom))
        }
        drawLine(Color.White.copy(alpha = 0.75f * f.shine), Offset(stem.left + stemW * 0.3f, stem.top + 5f * u), Offset(stem.left + stemW * 0.3f, stem.bottom - 2.5f * u), strokeWidth = 0.55f * u)
        drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(stemX - 0.9f * u, stem.bottom - 2.2f * u), Size(1.8f * u, 0.8f * u), CornerRadius(0.4f * u))
        // The ear tip, soft and matte, turned in towards the other earbud.
        val tipC = Offset(head.x + dir * 4.6f * u, head.y + 0.6f * u)
        val tipDark = Color(f.tip.red * 0.72f, f.tip.green * 0.72f, f.tip.blue * 0.74f)
        drawOval(Brush.radialGradient(listOf(f.tip, tipDark), tipC - Offset(dir * 1f * u, 1f * u), 4.2f * u), Offset(tipC.x - 3.2f * u, tipC.y - 3.6f * u), Size(6.4f * u, 7.2f * u))
        drawOval(Color.Black.copy(alpha = 0.32f), Offset(tipC.x + dir * 1.3f * u - 0.9f * u, tipC.y - 1.4f * u), Size(1.8f * u, 2.8f * u))
        // The body: a rounded bean, wider than tall.
        val body = Rect(head.x - 6.2f * u, head.y - 5.2f * u, head.x + 6.2f * u, head.y + 4.8f * u)
        val bodyPath = Path().apply { addOval(body) }
        drawPath(bodyPath, Brush.radialGradient(listOf(f.top, f.mid, f.low), Offset(head.x - 2.4f * u, head.y - 2.8f * u), 9f * u))
        drawPath(bodyPath, Color.Black.copy(alpha = 0.10f), style = Stroke(0.35f * u))
        // A sensor window and the gloss.
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(head.x - dir * 2.6f * u - 0.9f * u, head.y - 0.6f * u), Size(1.8f * u, 1.1f * u))
        drawOval(Color.White.copy(alpha = 0.8f * f.shine), Offset(head.x - 4f * u, head.y - 4.2f * u), Size(4f * u, 1.8f * u))
    }
}
