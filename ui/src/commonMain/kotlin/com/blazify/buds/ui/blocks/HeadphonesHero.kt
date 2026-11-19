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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

/**
 * Over-ear headphones, drawn with light and shade, for any pair. Folded flat while they are away; when they connect
 * the headband stretches out and the ear cups swing round to face you. The light on the right cup takes the mode's
 * colour.
 */
@Composable
fun HeadphonesHero(connected: Boolean, mode: ListeningMode?, modifier: Modifier = Modifier, height: Dp = 240.dp, still: Boolean = false) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current || still
    val open by animateFloatAsState(if (connected) 1f else 0f, if (reduce) tween(Motion.NORMAL) else spring(dampingRatio = 0.6f, stiffness = 50f), label = "unfold")
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
    val bob by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val finish = if (palette.dark) Graphite else Pearl
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = if (connected) "Headphones connected" else "Headphones not connected" }) {
        drawHeadphones(finish, open, if (reduce) 0f else bob, tint, palette.dark)
    }
}

internal fun DrawScope.drawHeadphones(f: Finish, open: Float, bob: Float, tint: Color, dark: Boolean) {
    val u = minOf(size.width, size.height * 1.15f) / 84f
    val cx = size.width / 2
    val lift = bob * 0.8f * u * open
    val crown = size.height * 0.13f + lift
    val reach = 5f * u * open
    val spread = 25f * u + 2.5f * u * open
    val cupY = size.height * 0.58f + reach * 0.5f + lift
    val padColor = if (dark) Color(0xFF0C0D10) else Color(0xFF7E8793)

    val glowR = 44f * u
    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = tint.alpha * 0.28f), tint.copy(alpha = tint.alpha * 0.06f), Color.Transparent), Offset(cx, cupY - 8f * u), glowR), glowR, Offset(cx, cupY - 8f * u))
    val shadowW = spread * 2.5f
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = if (dark) 0.5f else 0.18f), Color.Transparent), Offset(cx, cupY + 17f * u), shadowW / 2),
        Offset(cx - shadowW / 2, cupY + 14f * u),
        Size(shadowW, 6f * u),
    )

    // The headband: a thick padded arc, shell outside, soft cushion underneath.
    val endY = cupY - 16f * u - reach
    fun arc(inset: Float) = Path().apply {
        moveTo(cx - spread + inset, endY)
        cubicTo(cx - spread + inset - 1f * u, crown - 4f * u + inset, cx + spread - inset + 1f * u, crown - 4f * u + inset, cx + spread - inset, endY)
    }
    drawPath(arc(0f), Brush.verticalGradient(listOf(f.top, f.mid, f.low), crown - 8f * u, endY), style = Stroke(8.5f * u, cap = StrokeCap.Round))
    drawPath(arc(-2.6f * u), Color.White.copy(alpha = 0.45f * f.shine), style = Stroke(1.1f * u, cap = StrokeCap.Round))
    drawPath(arc(5f * u), padColor, style = Stroke(4.2f * u, cap = StrokeCap.Round))

    for (side in listOf(-1f, 1f)) {
        val x = cx + side * spread
        // The polished slider, out further when worn.
        drawLine(
            Brush.horizontalGradient(listOf(Color(0xFF9AA2AD), Color(0xFFF5F7F9), Color(0xFF86909C)), x - 1f * u, x + 1f * u),
            Offset(x, endY - 1f * u), Offset(x, cupY - 10f * u), strokeWidth = 2f * u, cap = StrokeCap.Round,
        )
        // The cup turns to face forward as they unfold; folded, it is seen edge on.
        val face = 0.3f + 0.7f * open
        val cupW = 19f * u * face
        val cupH = 25f * u
        // The cushion, showing on the inner side.
        val padW = 4.2f * u
        val pad = RoundRect(
            // Tucked a little under the cup so the two meet.
            Rect(
                x - side * (cupW / 2) - (if (side > 0) padW * face else 3f * u),
                cupY - cupH / 2 + 1.5f * u,
                x - side * (cupW / 2) + (if (side < 0) padW * face else 3f * u),
                cupY + cupH / 2 - 1.5f * u,
            ),
            CornerRadius(3f * u),
        )
        drawPath(Path().apply { addRoundRect(pad) }, Brush.horizontalGradient(listOf(padColor, padColor.copy(alpha = 0.85f)), pad.left, pad.right))
        val cup = RoundRect(Rect(x - cupW / 2, cupY - cupH / 2, x + cupW / 2, cupY + cupH / 2), CornerRadius(minOf(cupW / 2, 8f * u), 8f * u))
        val cupPath = Path().apply { addRoundRect(cup) }
        drawPath(cupPath, Brush.verticalGradient(listOf(f.top, f.mid, f.low), cup.top, cup.bottom))
        clipPath(cupPath) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.3f)), cup.left, cup.right))
            drawOval(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f * f.shine), Color.Transparent), cup.left + cupW * 0.1f, cup.left + cupW * 0.46f),
                Offset(cup.left + cupW * 0.1f, cup.top + 1f * u), Size(cupW * 0.36f, cupH * 0.8f),
            )
        }
        // A fine inset plate on the cup's face.
        val plate = RoundRect(Rect(cup.left + 2f * u * face, cup.top + 2.2f * u, cup.right - 2f * u * face, cup.bottom - 2.2f * u), CornerRadius(minOf(cupW / 2 - 2f * u, 6f * u).coerceAtLeast(0.5f * u), 6f * u))
        drawPath(Path().apply { addRoundRect(plate) }, Color.Black.copy(alpha = if (dark) 0.25f else 0.06f), style = Stroke(0.45f * u))
        if (side > 0) {
            val dot = Offset(x + cupW * 0.22f, cup.bottom - 5f * u)
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.5f * tint.alpha), Color.Transparent), dot, 3f * u), 3f * u, dot)
            drawCircle(if (tint.alpha > 0.01f) tint else Color.Black.copy(alpha = 0.3f), 0.7f * u, dot)
        }
    }
}
