package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Blazbuds mark: one earbud, a round head with a stem and a sound wave, on a 1000-unit square. The outline is a single line all the
 * way round, so the loader can run along it; the app icon fills it and cuts the opening out of the head.
 */
object LogoShape {
    const val OUTLINE = "M175 390 L175 870 A80 80 0 0 0 335 870 L335 630 A260 260 0 1 0 175 390 Z"
    const val OPENING = "M435 298 A92 92 0 1 0 435 482 A92 92 0 1 0 435 298 Z"
    /** A sound wave round the head, drawn on the icon only, as wide as [WAVE_WIDTH]. */
    const val WAVE = "M722.6 165.3 A365 365 0 0 1 722.6 614.7"
    const val WAVE_WIDTH = 64f

    fun path(svg: String, box: Size, inset: Float = 0f): Path {
        val side = minOf(box.width, box.height) - 2 * inset
        val path = nodes(svg).toPath()
        path.transform(
            Matrix().apply {
                translate((box.width - side) / 2, (box.height - side) / 2)
                scale(side / 1000f, side / 1000f)
            },
        )
        return path
    }

    // The mark is drawn on every frame of the animated pictures, so each outline is read from its text only once.
    private val outlineNodes by lazy { svgNodes(OUTLINE) }
    private val openingNodes by lazy { svgNodes(OPENING) }
    private val waveNodes by lazy { svgNodes(WAVE) }

    private fun nodes(svg: String): List<PathNode> = when (svg) {
        OUTLINE -> outlineNodes
        OPENING -> openingNodes
        WAVE -> waveNodes
        else -> svgNodes(svg)
    }
}

/** An SVG path's steps, read from its text. */
internal fun svgNodes(svg: String): List<PathNode> = PathParser().parsePathString(svg).toNodes()

/** True when the person asked their device for less motion; animations then hold still or only fade. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The mark drawn whole: the earbud filled, with its opening cut out. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 32.dp, color: Color = LocalPalette.current.accent) {
    Canvas(modifier.size(size)) {
        val shape = LogoShape.path(LogoShape.OUTLINE, this.size)
        val opening = LogoShape.path(LogoShape.OPENING, this.size)
        val whole = Path().apply {
            fillType = PathFillType.EvenOdd
            addPath(shape)
            addPath(opening)
        }
        drawPath(whole, color)
        drawPath(LogoShape.path(LogoShape.WAVE, this.size), color, style = Stroke(this.size.width * LogoShape.WAVE_WIDTH / 1000f, cap = StrokeCap.Round))
    }
}

/**
 * The app's loading mark: the earbud's outline as a faint line with a bright stretch running round it that grows and
 * shrinks as it goes, so it reads as the shape being drawn and let go. With less motion asked for, the outline only
 * breathes. Drawn from the time alone, so nothing else is laid out again while it moves.
 */
@Composable
fun LogoLoader(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = LocalPalette.current.accent,
    track: Color = color.copy(alpha = color.alpha * TRACK),
) {
    val still = LocalReduceMotion.current
    val going = rememberInfiniteTransition(label = "loader")
    val time by going.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(if (still) PULSE_MS else CYCLE_MS, easing = LinearEasing)),
        label = "time",
    )
    Spacer(
        modifier.size(size).semantics { contentDescription = "Loading" }.drawWithCache {
            val mark = Outline(this.size, lineFor(this.size.width))
            onDrawBehind {
                if (still) {
                    val glow = 0.5f - 0.5f * cos(2 * PI.toFloat() * time)
                    drawPath(mark.path, color.copy(alpha = color.alpha * (PULSE_LOW + (1 - PULSE_LOW) * glow)), style = mark.stroke)
                    return@onDrawBehind
                }
                drawPath(mark.path, track, style = mark.stroke)
                // Two laps to a cycle; the stretch is longest halfway, so the end of a cycle meets its start smoothly.
                val laps = 2 * time + SWAY * sin(4 * PI.toFloat() * time)
                val reach = SHORTEST + (LONGEST - SHORTEST) * (0.5f - 0.5f * cos(2 * PI.toFloat() * time))
                for (i in PIECES downTo 1) {
                    drawPath(mark.stretch(laps - reach * i / PIECES, laps), color.copy(alpha = color.alpha * LAYER), style = mark.stroke)
                }
                drawPath(mark.stretch(laps - reach / PIECES, laps), color, style = mark.headStroke)
            }
        },
    )
}

/** The outline in a box, with a way to take out any stretch of it as parts of the way round. */
private class Outline(box: Size, line: Float) {
    val path = LogoShape.path(LogoShape.OUTLINE, box, line / 2)
    val stroke = Stroke(line, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    val headStroke = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val measure = PathMeasure().apply { setPath(path, false) }
    private val length = measure.length
    private val piece = Path()

    fun stretch(from: Float, to: Float): Path {
        piece.reset()
        val start = from - floor(from)
        val end = start + (to - from)
        if (end <= 1f) {
            measure.getSegment(start * length, end * length, piece, true)
        } else {
            measure.getSegment(start * length, length, piece, true)
            measure.getSegment(0f, (end - 1f) * length, piece, true)
        }
        return piece
    }
}

private fun Density.lineFor(width: Float): Float = (width * LINE).coerceIn(MIN_LINE.toPx(), MAX_LINE.toPx())

private const val CYCLE_MS = 3200
private const val PULSE_MS = 1600
private const val PULSE_LOW = 0.3f
private const val TRACK = 0.22f
private const val SHORTEST = 0.18f
private const val LONGEST = 0.5f
private const val PIECES = 8
private const val LAYER = 0.3f
private const val SWAY = 0.03f
private const val LINE = 0.065f
private val MIN_LINE = 1.5.dp
private val MAX_LINE = 3.5.dp

