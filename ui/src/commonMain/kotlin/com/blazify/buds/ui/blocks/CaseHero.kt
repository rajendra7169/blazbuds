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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The earbuds' charging case, open when they are connected: the lid swings up and back with a little overshoot, and
 * each earbud rises out and floats above it. One that is back in the case, charging, stays down inside. The light on
 * the front and the glow behind take the colour of the listening mode.
 */
@Composable
fun CaseHero(
    connected: Boolean,
    mode: ListeningMode?,
    leftOut: Boolean,
    rightOut: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    still: Boolean = false,
    soft: Boolean = false,
) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current || still
    val open by animateFloatAsState(
        if (connected) 1f else 0f,
        if (reduce) tween(Motion.NORMAL) else spring(dampingRatio = 0.55f, stiffness = 90f),
        label = "lid",
    )
    val leftRise by animateFloatAsState(if (connected && leftOut) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 70f), label = "left")
    val rightRise by animateFloatAsState(if (connected && rightOut) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 60f), label = "right")
    val tint by animateColorAsState(
        when {
            !connected -> palette.line
            mode == ListeningMode.NoiseCancelling -> palette.cancelling
            mode == ListeningMode.Transparency -> palette.transparency
            else -> palette.accent
        },
        tween(Motion.SLOW * 2),
        label = "tint",
    )
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val shell = if (palette.dark) Color(0xFF242B32) else Color(0xFFE6EBEF)
    val shellLight = if (palette.dark) Color(0xFF323B44) else Color(0xFFF7F9FA)
    val inner = if (palette.dark) Color(0xFF0E1216) else Color(0xFFC3CCD3)
    val edge = if (palette.dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val bud = if (palette.dark) Color(0xFF2E363E) else Color(0xFFFFFFFF)
    Canvas(
        modifier.fillMaxWidth().height(height).semantics {
            contentDescription = if (connected) "Earbuds connected" else "Earbuds not connected"
        },
    ) {
        val w = minOf(size.width, size.height * 1.35f)
        val cx = size.width / 2
        val caseW = w * 0.5f
        val baseH = w * 0.23f
        val lidH = w * 0.15f
        val seam = size.height * 0.62f
        // The glow behind, in the mode's colour.
        val r = w * 0.55f
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.30f * open), tint.copy(alpha = 0.08f * open), Color.Transparent), Offset(cx, seam - w * 0.08f), r),
            r,
            Offset(cx, seam - w * 0.08f),
        )
        // The lid swings up and back about the hinge at the top of the case.
        val angle = open * 108f * (PI.toFloat() / 180f)
        val lift = caseW * 0.16f * sin(angle)
        val shown = lidH * (0.8f + 0.2f * abs(cos(angle)))
        val lidBottom = seam - lift
        val lid = RoundRect(
            Rect(cx - caseW / 2, lidBottom - shown, cx + caseW / 2, lidBottom),
            topLeft = CornerRadius(shown * 0.9f), topRight = CornerRadius(shown * 0.9f),
            bottomLeft = CornerRadius(w * 0.02f), bottomRight = CornerRadius(w * 0.02f),
        )
        val pastUpright = angle > PI.toFloat() / 2
        val lidPath = Path().apply { addRoundRect(lid) }
        if (soft) {
            // Rounded light: bright along the top, falling off towards the seam, darker at the sides.
            drawPath(lidPath, Brush.verticalGradient(if (pastUpright) listOf(shell, shellLight) else listOf(shellLight, shell), lid.top, lid.bottom))
            clipPath(lidPath) {
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.2f)), lid.left, lid.right))
                if (!pastUpright) drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (palette.dark) 0.16f else 0.55f), Color.Transparent), lid.top, lid.top + shown * 0.6f), Offset(cx - caseW * 0.38f, lid.top + shown * 0.08f), Size(caseW * 0.5f, shown * 0.5f))
            }
        } else {
            drawPath(lidPath, if (pastUpright) shellLight else shell)
        }
        drawPath(lidPath, edge, style = Stroke(w * 0.006f))
        if (pastUpright) {
            // The inside of the lid, with its magnets' seat.
            if (soft) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(inner.copy(alpha = 0.45f), inner.copy(alpha = 0.12f)), lidBottom - shown * 0.75f, lidBottom - shown * 0.2f),
                    Offset(cx - caseW * 0.38f, lidBottom - shown * 0.75f), Size(caseW * 0.76f, shown * 0.55f), CornerRadius(shown * 0.25f),
                )
            } else {
                drawRoundRect(inner.copy(alpha = 0.25f), Offset(cx - caseW * 0.36f, lidBottom - shown * 0.7f), Size(caseW * 0.72f, shown * 0.4f), CornerRadius(shown * 0.2f))
            }
        }
        // The opening of the case, seen as it opens.
        if (open > 0.02f) {
            drawRoundRect(
                if (soft) Brush.verticalGradient(listOf(inner.copy(alpha = 1f), inner.copy(alpha = 0.55f)), seam - baseH * 0.12f * open, seam + baseH * 0.08f * open) else androidx.compose.ui.graphics.SolidColor(inner),
                Offset(cx - caseW * 0.44f, seam - baseH * 0.12f * open),
                Size(caseW * 0.88f, baseH * 0.2f * open + 1f),
                CornerRadius(baseH * 0.1f),
            )
        }
        // The earbuds, rising out of their slots.
        val float = (bob - 0.5f) * w * 0.012f
        listOf(Triple(-1f, leftRise, 0f), Triple(1f, rightRise, 0.5f)).forEach { (dir, rise, phase) ->
            val up = rise * w * 0.30f + if (rise > 0.5f) float * if (phase > 0f) -1f else 1f else 0f
            val side = w * 0.3f
            val x = cx + dir * caseW * 0.2f
            val y = seam - up
            if (open > 0.05f) budAt(Offset(x, y), side, dir < 0, bud, edge, tint, (open * 1.4f).coerceAtMost(1f), soft, shellLight, shell)
        }
        // The front of the case covers the earbuds' stems while they are inside.
        val base = RoundRect(
            Rect(cx - caseW / 2, seam, cx + caseW / 2, seam + baseH),
            topLeft = CornerRadius(w * 0.02f), topRight = CornerRadius(w * 0.02f),
            bottomLeft = CornerRadius(baseH * 0.95f), bottomRight = CornerRadius(baseH * 0.95f),
        )
        val basePath = Path().apply { addRoundRect(base) }
        drawPath(basePath, Brush.verticalGradient(listOf(shellLight, shell), seam, seam + baseH))
        if (soft) {
            clipPath(basePath) {
                // Round across: shade at both sides; a long soft highlight on the left; a lighter lip at the top.
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.24f)), base.left, base.right))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)), seam + baseH * 0.4f, seam + baseH))
                drawOval(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (palette.dark) 0.2f else 0.45f), Color.Transparent), cx - caseW * 0.42f, cx - caseW * 0.24f),
                    Offset(cx - caseW * 0.42f, seam - baseH * 0.1f), Size(caseW * 0.18f, baseH * 0.85f),
                )
                drawLine(Color.White.copy(alpha = if (palette.dark) 0.25f else 0.6f), Offset(base.left + w * 0.03f, seam + w * 0.004f), Offset(base.right - w * 0.03f, seam + w * 0.004f), strokeWidth = w * 0.004f)
            }
        }
        drawPath(basePath, edge, style = Stroke(w * 0.006f))
        // The status light.
        drawCircle(tint, w * 0.011f, Offset(cx, seam + baseH * 0.62f))
        drawCircle(tint.copy(alpha = 0.25f), w * 0.024f, Offset(cx, seam + baseH * 0.62f))
        // A soft shadow under the case.
        if (soft) {
            val sw = caseW * 1.15f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = if (palette.dark) 0.55f else 0.16f), Color.Transparent), Offset(cx, seam + baseH + w * 0.03f), sw / 2),
                Offset(cx - sw / 2, seam + baseH + w * 0.005f), Size(sw, w * 0.05f),
            )
        } else {
            drawOval(Color.Black.copy(alpha = if (palette.dark) 0.35f else 0.08f), Offset(cx - caseW * 0.42f, seam + baseH + w * 0.015f), Size(caseW * 0.84f, w * 0.03f))
        }
    }
}

private fun DrawScope.budAt(at: Offset, side: Float, mirrored: Boolean, fill: Color, edge: Color, tint: Color, alpha: Float, soft: Boolean = false, light: Color = fill, dark: Color = fill) {
    val box = Size(side, side)
    // Placed so the head sits at [at] and the stem hangs below it.
    translate(at.x - side * 0.435f, at.y - side * 0.39f) {
        scale(if (mirrored) -1f else 1f, 1f, pivot = Offset(side * 0.435f, side * 0.39f)) {
            rotate(-14f, pivot = Offset(side * 0.435f, side * 0.39f)) {
                val shape = LogoShape.path(LogoShape.OUTLINE, box)
                if (soft) {
                    // Lit from the upper left, like a soft clay model: bright head, the stem rounding into shade.
                    drawPath(shape, Brush.radialGradient(listOf(light, fill, dark), Offset(side * 0.36f, side * 0.28f), side * 0.75f), alpha = alpha)
                    clipPath(shape) {
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), side * 0.25f, side * 0.38f), Offset(side * 0.25f, side * 0.6f), Size(side * 0.15f, side * 0.4f), alpha = alpha)
                    }
                    drawPath(shape, edge.copy(alpha = edge.alpha * alpha), style = Stroke(side * 0.014f))
                    // The speaker opening, set in, with the mode's light inside it.
                    drawPath(LogoShape.path(LogoShape.OPENING, box), Brush.radialGradient(listOf(tint, tint.copy(alpha = tint.alpha * 0.6f)), Offset(side * 0.42f, side * 0.37f), side * 0.11f), alpha = 0.95f * alpha)
                    drawCircle(Color.Black.copy(alpha = 0.18f * alpha), side * 0.092f, Offset(side * 0.435f, side * 0.39f), style = Stroke(side * 0.012f))
                    // The gloss on the head.
                    drawOval(Color.White.copy(alpha = 0.7f * alpha), Offset(side * 0.29f, side * 0.2f), Size(side * 0.12f, side * 0.06f))
                } else {
                    drawPath(shape, fill.copy(alpha = fill.alpha * alpha))
                    drawPath(shape, edge.copy(alpha = edge.alpha * alpha), style = Stroke(side * 0.02f))
                    drawPath(LogoShape.path(LogoShape.OPENING, box), tint.copy(alpha = 0.9f * alpha))
                }
            }
        }
    }
}
