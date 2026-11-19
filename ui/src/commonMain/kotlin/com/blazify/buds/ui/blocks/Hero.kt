package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

/**
 * The earbuds themselves, drawn from the logo's shape, a mirrored pair. Behind them a soft glow takes the colour of the
 * listening mode and fades from one to the next, so the mode is seen at a glance. They float a little and lean towards
 * the pointer on a computer; with less motion asked for they hold still.
 */
@Composable
fun BudsHero(
    mode: ListeningMode?,
    leftIn: Boolean = true,
    rightIn: Boolean = true,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val glow by animateColorAsState(
        when (mode) {
            ListeningMode.NoiseCancelling -> palette.cancelling
            ListeningMode.Transparency -> palette.transparency
            else -> palette.muted.copy(alpha = 0f)
        },
        tween(Motion.SLOW * 2),
        label = "glow",
    )
    // Cancelling draws the glow in close and tight; transparency lets it spread open.
    val spread by animateFloatAsState(
        when (mode) {
            ListeningMode.NoiseCancelling -> 0.78f
            ListeningMode.Transparency -> 1.05f
            else -> 0.9f
        },
        spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "spread",
    )
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(
        -1f,
        1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val breathe by float.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "breathe")
    var lean by remember { mutableStateOf(Offset.Zero) }
    val leanX by animateFloatAsState(if (reduce) 0f else lean.x, spring(stiffness = 90f), label = "leanX")
    val leanY by animateFloatAsState(if (reduce) 0f else lean.y, spring(stiffness = 90f), label = "leanY")
    val bud = palette.card
    val edge = if (palette.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
    val shade = if (palette.dark) Color.White.copy(alpha = 0.10f) else Color(0xFFDDE3E7)
    Box(
        modifier.fillMaxWidth().height(height)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val at = event.changes.firstOrNull()?.position ?: continue
                        lean = when (event.type) {
                            PointerEventType.Exit -> Offset.Zero
                            else -> Offset(
                                (at.x / size.width - 0.5f).coerceIn(-0.5f, 0.5f),
                                (at.y / size.height - 0.5f).coerceIn(-0.5f, 0.5f),
                            )
                        }
                    }
                }
            }
            .graphicsLayer {
                rotationY = leanX * 14f
                rotationX = -leanY * 10f
                cameraDistance = 12f * density
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val lift = if (reduce) 0f else bob * 4.dp.toPx()
            val r = size.minDimension * 0.62f * (spread + 0.04f * breathe)
            drawCircle(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = glow.alpha * 0.42f), glow.copy(alpha = glow.alpha * 0.12f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            val side = size.height * 0.8f
            val gap = size.height * 0.36f
            translate(top = lift) {
                drawBud(Offset(center.x - gap, center.y), side, mirrored = true, fill = bud, edge = edge, shade = shade, faded = !leftIn)
                drawBud(Offset(center.x + gap, center.y), side, mirrored = false, fill = bud, edge = edge, shade = shade, faded = !rightIn)
            }
        }
    }
}

private fun DrawScope.drawBud(at: Offset, side: Float, mirrored: Boolean, fill: Color, edge: Color, shade: Color, faded: Boolean) {
    val box = Size(side, side)
    val outline = LogoShape.path(LogoShape.OUTLINE, box)
    val opening = LogoShape.path(LogoShape.OPENING, box)
    val alpha = if (faded) 0.35f else 1f
    translate(at.x - side / 2, at.y - side / 2) {
        scale(if (mirrored) -1f else 1f, 1f, pivot = Offset(side / 2, side / 2)) {
            rotate(-24f, pivot = Offset(side * 0.43f, side * 0.6f)) {
                drawPath(outline, fill.copy(alpha = fill.alpha * alpha))
                drawPath(outline, edge.copy(alpha = edge.alpha * alpha), style = Stroke(side * 0.012f))
                // The speaker opening, set in a little darker.
                drawPath(opening, shade.copy(alpha = shade.alpha * alpha))
                val shine = Path().apply {
                    fillType = PathFillType.NonZero
                    addPath(LogoShape.path("M265 340 A200 200 0 0 1 495 190", box))
                }
                drawPath(shine, Color.White.copy(alpha = 0.35f * alpha), style = Stroke(side * 0.025f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
    }
}
