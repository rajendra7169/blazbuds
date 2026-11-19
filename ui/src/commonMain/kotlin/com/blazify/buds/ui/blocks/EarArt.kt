package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.Ear
import com.blazify.buds.ui.theme.LocalPalette

/**
 * An ear seen from the side, drawn as clean lines, with our earbud sitting in it and a pulsing spot on the stem where a
 * touch goes. The left ear is the right one mirrored; switching sides flips it over.
 */
@Composable
fun EarArt(ear: Ear, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val pulse = rememberInfiniteTransition(label = "touch")
    val beat by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "beat")
    val facing by animateFloatAsState(if (ear == Ear.Right) 1f else -1f, tween(420, easing = FastOutSlowInEasing), label = "flip")
    val skin = if (palette.dark) Color(0xFF1D2329) else Color(0xFFFFFFFF)
    val line = if (palette.dark) Color(0xFF6B7782) else Color(0xFF9AA5AE)
    val bud = if (palette.dark) Color(0xFF2E363E) else Color(0xFFF2F4F6)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Touch the stem of the ${if (ear == Ear.Left) "left" else "right"} earbud" }) {
        val box = size.height * 0.92f
        val left = size.width / 2 - box / 2
        val top = (size.height - box) / 2
        // Flip about the middle: a squeeze through zero reads as the drawing turning over.
        scale(facing, 1f, pivot = Offset(size.width / 2, size.height / 2)) {
            translate(left, top) {
                drawEar(box, skin, line)
                drawEarbudIn(box, bud, line, palette.accent, if (reduce) 0.5f else beat)
            }
        }
    }
}

private val EAR_OUTLINE = "M46 10 C62 2 86 10 88 34 C90 52 80 62 76 74 C72 86 70 100 58 106 C48 111 40 104 42 94 C44 86 48 82 46 76 C40 72 36 66 40 60 C44 56 42 50 38 44 C34 30 36 16 46 10 Z"
private val EAR_RIM = "M48 18 C62 12 78 18 80 34 C82 48 74 58 70 70"
private val EAR_FOLD = "M66 26 C73 38 71 52 61 60 C57 63 55 66 53 71"
private val EAR_FOLD_UPPER = "M66 26 C60 28 56 30 54 36"
private val EAR_LOBE = "M58 98 C54 100 50 98 50 94"

private fun path(svg: String, side: Float): Path = PathParser().parsePathString(svg).toPath().apply {
    transform(Matrix().apply { scale(side / 112f, side / 112f) })
}

private fun DrawScope.drawEar(side: Float, skin: Color, line: Color) {
    val outline = path(EAR_OUTLINE, side)
    drawPath(outline, Brush.radialGradient(listOf(skin, skin.copy(alpha = 0.85f)), Offset(side * 0.55f, side * 0.45f), side * 0.6f))
    val stroke = Stroke(side * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(outline, line, style = stroke)
    drawPath(path(EAR_RIM, side), line.copy(alpha = 0.7f), style = stroke)
    drawPath(path(EAR_FOLD, side), line.copy(alpha = 0.7f), style = stroke)
    drawPath(path(EAR_FOLD_UPPER, side), line.copy(alpha = 0.5f), style = stroke)
    drawPath(path(EAR_LOBE, side), line.copy(alpha = 0.45f), style = stroke)
    // The bowl the earbud sits in, softly shaded.
    val k = side / 112f
    drawOval(Brush.radialGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent), Offset(54f * k, 58f * k), 13f * k), Offset(41f * k, 45f * k), Size(26f * k, 26f * k))
}

/** Our earbud, its head in the bowl of the ear and its stem hanging forward, with the touch spot pulsing on the stem. */
private fun DrawScope.drawEarbudIn(side: Float, fill: Color, line: Color, accent: Color, beat: Float) {
    val k = side / 112f
    val budSide = 58f * k
    // The logo earbud has its head at (0.435, 0.39) of its box; put that in the ear's bowl.
    val headAt = Offset(55f * k, 55f * k)
    translate(headAt.x - budSide * 0.435f, headAt.y - budSide * 0.39f) {
        rotate(12f, pivot = Offset(budSide * 0.435f, budSide * 0.39f)) {
            val shape = LogoShape.path(LogoShape.OUTLINE, Size(budSide, budSide))
            drawPath(shape, fill)
            drawPath(shape, line, style = Stroke(side * 0.012f))
            val opening = LogoShape.path(LogoShape.OPENING, Size(budSide, budSide)).apply { fillType = PathFillType.NonZero }
            drawPath(opening, line.copy(alpha = 0.35f))
            // Where a touch goes: on the stem, just below the head.
            val spot = Offset(budSide * 0.255f, budSide * 0.68f)
            drawCircle(accent.copy(alpha = 0.35f * (1f - beat)), budSide * (0.08f + 0.12f * beat), spot)
            drawCircle(accent, budSide * 0.075f, spot)
        }
    }
}
