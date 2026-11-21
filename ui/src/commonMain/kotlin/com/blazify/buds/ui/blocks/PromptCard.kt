package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.sin

// Blazbuds' teal, as the app's accent: light to deep, and the dark ink that sits on it.
private val Teal = Color(0xFF3DD5C3)
private val TealDeep = Color(0xFF0F9488)
private val TealDark = Color(0xFF075E56)
private val ButtonInk = Color(0xFF04201C)

/** The two buttons of a [PromptCard]: a quiet one on the left and the main one on the right. */
data class PromptButtons(val primary: String, val onPrimary: () -> Unit, val secondary: String, val onSecondary: () -> Unit)

/**
 * The card the app asks things with, such as "a new version is ready": one compact card, a picture drawn in 3D breaking
 * out of its top edge, a title, a line or two and two buttons. It rises in gently, and holds still with less motion.
 */
@Composable
fun PromptCard(title: String, body: String, buttons: PromptButtons, onDismissRequest: () -> Unit, art: DrawScope.(time: Float) -> Unit) {
    val still = LocalReduceMotion.current
    val enter = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
    // One slow clock for everything that moves in the picture: a full turn every six seconds.
    val clock = rememberInfiniteTransition(label = "promptArt")
    val tick by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "promptTime")
    // Read only where the pictures are drawn, so the card itself isn't composed again on every frame.
    val time = { if (still) 0.15f else tick }
    Dialog(onDismissRequest = onDismissRequest) {
        PromptCardLayout(
            title, body, buttons, time, art,
            Modifier.graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                val grow = 0.94f + 0.06f * enter.value
                scaleX = grow
                scaleY = grow
                translationY = (1f - enter.value) * 28.dp.toPx()
            },
        )
    }
}

/** The card itself, outside any window, so the screenshots can draw it on its own. */
@Composable
fun PromptCardLayout(title: String, body: String, buttons: PromptButtons, time: Float, art: DrawScope.(time: Float) -> Unit, modifier: Modifier = Modifier) =
    PromptCardLayout(title, body, buttons, { time }, art, modifier)

/** The card, with the pictures' clock read only as they are drawn. */
@Composable
private fun PromptCardLayout(title: String, body: String, buttons: PromptButtons, time: () -> Float, art: DrawScope.(time: Float) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val glow = Teal.copy(alpha = if (palette.dark) 0.18f else 0.22f)
    val overflow = 46.dp
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier.widthIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.padding(top = overflow).shadow(16.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(palette.card),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The sky the picture flies in: a teal glow with notes drifting in it.
            Canvas(Modifier.fillMaxWidth().height(84.dp)) {
                drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent)))
                drawSkyNotes(time(), if (palette.dark) Color.White else TealDeep)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = palette.text)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = palette.muted)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PromptPill(buttons.secondary, primary = false, onClick = buttons.onSecondary, modifier = Modifier.weight(1f))
                    PromptPill(buttons.primary, primary = true, onClick = buttons.onPrimary, modifier = Modifier.weight(1f))
                }
            }
        }
        Canvas(Modifier.size(132.dp)) { art(time()) }
    }
}

@Composable
private fun PromptPill(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) Brush.horizontalGradient(listOf(Teal, TealDeep)) else Brush.horizontalGradient(listOf(palette.text.copy(alpha = 0.08f), palette.text.copy(alpha = 0.08f))))
            .press(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (primary) ButtonInk else palette.text,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/* The picture draws in a 100 × 100 space scaled to the canvas. */

private inline fun DrawScope.inUnits(block: DrawScope.(u: Float) -> Unit) = block(size.minDimension / 100f)

/** Faint earbuds, headphones, a case and a note in the card's sky, each floating a little on its own beat. */
private fun DrawScope.drawSkyNotes(time: Float, ink: Color) {
    val spots = listOf(Triple(0.12f, 0.62f, 0.0f), Triple(0.88f, 0.42f, 0.3f), Triple(0.22f, 0.3f, 0.55f), Triple(0.78f, 0.78f, 0.8f))
    spots.forEachIndexed { i, (x, y, phase) ->
        val wave = sin((time + phase) * 2 * PI * 2).toFloat()
        drawTrinket(SKY[i], Offset(size.width * x, size.height * y - wave * 3.dp.toPx()), 12.dp.toPx(), ink.copy(alpha = 0.16f + 0.05f * wave))
    }
}

private val SKY = listOf(Trinket.Headphones, Trinket.Earbud, Trinket.Case, Trinket.Note)

/** The little things that float round the pictures: what Blazbuds is about, and a bit of music. */
enum class Trinket { Earbud, Headphones, Case, Note }

/** One [Trinket] centred on [at], [h] tall. */
fun DrawScope.drawTrinket(kind: Trinket, at: Offset, h: Float, color: Color) {
    if (color.alpha <= 0f) return
    when (kind) {
        Trinket.Earbud -> translate(at.x - h / 2f, at.y - h / 2f) { drawLogo(h, color, wave = false) }
        Trinket.Note -> drawNote(at, h, color)
        Trinket.Headphones -> {
            val w = h * 1.05f
            drawArc(color, 200f, 140f, false, topLeft = Offset(at.x - w * 0.42f, at.y - h * 0.46f), size = Size(w * 0.84f, h * 0.8f), style = Stroke(h * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            listOf(-1f, 1f).forEach { side ->
                drawRoundRect(color, topLeft = Offset(at.x + side * w * 0.36f - h * 0.12f, at.y - h * 0.02f), size = Size(h * 0.24f, h * 0.42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.12f))
            }
        }
        Trinket.Case -> {
            val w = h * 1.1f
            val top = Offset(at.x - w / 2f, at.y - h * 0.32f)
            drawRoundRect(color, topLeft = top, size = Size(w, h * 0.68f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.3f))
            // The lid's seam and the little light on the front, cut out of the case.
            drawLine(Color.Black.copy(alpha = color.alpha * 0.5f), Offset(top.x + w * 0.08f, at.y - h * 0.08f), Offset(top.x + w * 0.92f, at.y - h * 0.08f), strokeWidth = h * 0.05f)
            drawCircle(Color.White.copy(alpha = color.alpha * 0.8f), h * 0.05f, Offset(at.x, at.y + h * 0.16f))
        }
    }
}

/** The Blazbuds mark, [side] across, from the top left: the earbud with its opening cut out, and its sound wave. */
private fun DrawScope.drawLogo(side: Float, color: Color, wave: Boolean = true) {
    val box = Size(side, side)
    val whole = Path().apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        addPath(LogoShape.path(LogoShape.OUTLINE, box))
        addPath(LogoShape.path(LogoShape.OPENING, box))
    }
    drawPath(whole, color)
    if (wave) drawPath(LogoShape.path(LogoShape.WAVE, box), color, style = Stroke(side * LogoShape.WAVE_WIDTH / 1000f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/**
 * The update picture: a glossy rocket climbing to the right, the Blazbuds logo in its porthole, and smoke and notes
 * tumbling out of its exhaust.
 */
fun DrawScope.drawRocket(time: Float) = inUnits { u ->
    val bob = sin(time * 2 * PI * 2).toFloat() * 1.1f * u
    rotate(38f, Offset(50f * u, 52f * u)) {
        translate(0f, bob - 5f * u) {
            // Smoke: soft round puffs under the nozzle, widening as it falls behind; lit from the top left.
            val puffs = listOf(Triple(50f, 84f, 6.5f), Triple(44.5f, 89.5f, 6f), Triple(55.5f, 89.5f, 6f), Triple(50f, 95f, 6.5f))
            puffs.forEachIndexed { i, (x, y, r) ->
                val breathe = 1f + 0.08f * sin((time * 2 * PI * 2) + i).toFloat()
                val c = Offset(x * u, y * u)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE4ECEB), Color(0xFFB9C6C4)), center = c + Offset(-r * 0.35f * u, -r * 0.4f * u), radius = r * u * 1.4f),
                    radius = r * u * breathe,
                    center = c,
                )
            }
            // Small puffs leaving the nozzle, spreading to both sides as they grow and fade.
            for (i in 0 until 6) {
                val p = (time * 3 + i / 6f) % 1f
                val side = if (i % 2 == 0) -1f else 1f
                val c = Offset((50f + side * 7f * p) * u, (80f + 24f * p) * u)
                val r = (2.4f + 4.2f * p) * u
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFD7E2E0)), center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.3f), radius = r, center = c, alpha = (1f - p) * 0.9f)
            }
            // Little earbuds, cases, headphones and notes tumbling out with the smoke, one to each side, a new one each time.
            for (i in 0..1) {
                val run = time * 2f + i / 2f
                val p = run % 1f
                val side = if (i == 0) -1f else 1f
                val kind = Trinket.entries[(run.toInt() + i * 2) % Trinket.entries.size]
                drawTrinket(kind, Offset((50f + side * (6f + 6f * p)) * u, (86f + 14f * p) * u), 8.5f * u, Teal.copy(alpha = (1f - p) * 0.95f))
            }
            // Fins behind the body.
            drawPath(
                Path().apply {
                    moveTo(37f * u, 50f * u)
                    cubicTo(26f * u, 56f * u, 24f * u, 70f * u, 27f * u, 80f * u)
                    cubicTo(31f * u, 75f * u, 35f * u, 72f * u, 39f * u, 71f * u)
                    close()
                },
                Brush.linearGradient(listOf(Color(0xFF7FE9DC), TealDeep, TealDark), Offset(30f * u, 55f * u), Offset(40f * u, 82f * u)),
            )
            drawPath(
                Path().apply {
                    moveTo(63f * u, 50f * u)
                    cubicTo(74f * u, 56f * u, 76f * u, 70f * u, 73f * u, 80f * u)
                    cubicTo(69f * u, 75f * u, 65f * u, 72f * u, 61f * u, 71f * u)
                    close()
                },
                Brush.linearGradient(listOf(Color(0xFF7FE9DC), TealDeep, Color(0xFF053F3A)), Offset(62f * u, 55f * u), Offset(74f * u, 82f * u)),
            )
            // Nozzle.
            drawPath(
                Path().apply { moveTo(43f * u, 72f * u); lineTo(57f * u, 72f * u); lineTo(55f * u, 79f * u); lineTo(45f * u, 79f * u); close() },
                Brush.horizontalGradient(listOf(Color(0xFF59626A), Color(0xFFA9B2B8), Color(0xFF3E454B)), 43f * u, 57f * u),
            )
            // Body: a rounded shell, shaded like a cylinder lit from the left.
            val body = Path().apply {
                moveTo(50f * u, 8f * u)
                cubicTo(61f * u, 16f * u, 66f * u, 32f * u, 64f * u, 52f * u)
                cubicTo(63f * u, 62f * u, 61f * u, 70f * u, 58f * u, 74f * u)
                lineTo(42f * u, 74f * u)
                cubicTo(39f * u, 70f * u, 37f * u, 62f * u, 36f * u, 52f * u)
                cubicTo(34f * u, 32f * u, 39f * u, 16f * u, 50f * u, 8f * u)
                close()
            }
            drawPath(body, Brush.horizontalGradient(0f to Color(0xFFA3AFB3), 0.3f to Color(0xFFFFFFFF), 0.62f to Color(0xFFE9EFEF), 1f to Color(0xFF84918F), startX = 36f * u, endX = 64f * u))
            clipPath(body) {
                // Teal nose cone, with the same cylinder shading.
                drawRect(
                    Brush.horizontalGradient(0f to TealDark, 0.32f to Color(0xFFA6F4EA), 0.6f to Teal, 1f to Color(0xFF075049), startX = 36f * u, endX = 64f * u),
                    topLeft = Offset(30f * u, 0f),
                    size = Size(40f * u, 25f * u),
                )
                // A band at the waist.
                drawRect(
                    Brush.horizontalGradient(0f to TealDark, 0.35f to Color(0xFF6FE6D7), 1f to Color(0xFF053F3A), startX = 36f * u, endX = 64f * u),
                    topLeft = Offset(30f * u, 63f * u),
                    size = Size(40f * u, 4f * u),
                )
                // Shine down the lit side.
                drawRect(
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), 40f * u, 46f * u),
                    topLeft = Offset(40f * u, 14f * u),
                    size = Size(6f * u, 54f * u),
                )
            }
            // Porthole: a teal rim around dark glass, with the Blazbuds logo inside.
            val port = Offset(50f * u, 41f * u)
            drawCircle(Brush.linearGradient(listOf(Color(0xFFB8F7EF), TealDeep, Color(0xFF053F3A)), port - Offset(10.5f * u, 10.5f * u), port + Offset(10.5f * u, 10.5f * u)), radius = 11f * u, center = port)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF123B37), Color(0xFF021412)), center = port - Offset(2f * u, 2f * u), radius = 10.5f * u), radius = 8.6f * u, center = port)
            val side = 14f * u
            translate(port.x - side / 2f, port.y - side / 2f) { drawLogo(side, Teal) }
            drawArc(
                Color.White.copy(alpha = 0.55f), startAngle = 195f, sweepAngle = 55f, useCenter = false,
                topLeft = port - Offset(7.4f * u, 7.4f * u), size = Size(14.8f * u, 14.8f * u), style = Stroke(width = 1.1f * u),
            )
        }
    }
}

/** A music note: an oval head tilted back, a stem, and a flag. */
private fun DrawScope.drawNote(at: Offset, h: Float, color: Color) {
    if (color.alpha <= 0f) return
    val headW = h * 0.46f
    val headH = h * 0.32f
    val head = Offset(at.x - h * 0.12f, at.y + h * 0.32f)
    rotate(-22f, head) { drawOval(color, topLeft = head - Offset(headW / 2f, headH / 2f), size = Size(headW, headH)) }
    val stemX = head.x + headW * 0.42f
    val top = at.y - h * 0.5f
    drawRect(color, topLeft = Offset(stemX - h * 0.06f, top), size = Size(h * 0.1f, head.y - top))
    drawPath(
        Path().apply {
            moveTo(stemX, top)
            cubicTo(stemX + h * 0.12f, top + h * 0.12f, stemX + h * 0.36f, top + h * 0.2f, stemX + h * 0.26f, top + h * 0.48f)
            cubicTo(stemX + h * 0.28f, top + h * 0.3f, stemX + h * 0.14f, top + h * 0.24f, stemX, top + h * 0.22f)
            close()
        },
        color,
    )
}

/**
 * The star picture: a faceted teal star, each face lit by how it turns to a light at the top left, the Blazbuds earbud
 * engraved in the middle, a glint at a tip, GitHub marks low on each side and earbuds and notes floating round it.
 */
fun DrawScope.drawStar(time: Float) = inUnits { u ->
    val beat = sin(time * 2 * PI * 2).toFloat()
    val grow = 1f + 0.035f * beat
    val centre = Offset(50f * u, 52f * u)
    listOf(Triple(10f, 66f, 0.25f), Triple(90f, 70f, 0.75f)).forEach { (x, y, phase) ->
        val wave = sin((time + phase) * 2 * PI * 2).toFloat()
        drawGitHubMark(Offset(x * u, (y - 3f * wave) * u), 10f * u, Teal.copy(alpha = 0.75f + 0.15f * wave))
    }
    listOf(Triple(14f, 22f, 0f) to Trinket.Earbud, Triple(86f, 30f, 0.5f) to Trinket.Note).forEach { (spot, kind) ->
        val (x, y, phase) = spot
        val wave = sin((time + phase) * 2 * PI * 2).toFloat()
        drawTrinket(kind, Offset(x * u, (y - 3f * wave) * u), 10f * u, Teal.copy(alpha = 0.8f + 0.15f * wave))
    }
    scale(grow, centre) {
        val outer = 36f * u
        val inner = outer * 0.48f
        val points = (0 until 10).map { i ->
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            centre + Offset((r * kotlin.math.cos(a)).toFloat(), (r * sin(a)).toFloat())
        }
        val light = -PI * 0.75
        for (i in 0 until 10) {
            val p1 = points[i]
            val p2 = points[(i + 1) % 10]
            val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f) - centre
            // Each face is lit by how squarely it faces the light, nudged by its side of the ridge: the bevel reads as 3D.
            val facing = kotlin.math.cos(kotlin.math.atan2(mid.y.toDouble(), mid.x.toDouble()) - light).toFloat()
            val ridge = if (i % 2 == 0) 0.18f else -0.18f
            val lit = ((facing + 1f) / 2f + ridge).coerceIn(0f, 1f)
            drawPath(Path().apply { moveTo(centre.x, centre.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }, androidx.compose.ui.graphics.lerp(TealDark, Color(0xFFC8FBF4), lit))
        }
        drawPath(
            Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() },
            Color(0xFF064E47).copy(alpha = 0.55f),
            style = Stroke(width = 1.2f * u),
        )
        // The earbud engraved in a deeper teal in the middle of the facets.
        val mark = 24f * u
        translate(centre.x - mark / 2f + 0.5f * u, centre.y - mark / 2f + 1f * u) { drawLogo(mark, Color(0xFF053F3A).copy(alpha = 0.7f), wave = false) }
        val glint = 0.5f + 0.5f * sin(time * 2 * PI * 3).toFloat()
        drawSparkle(points[8] + Offset(2f * u, 2f * u), (4f + 3f * glint) * u, Color.White.copy(alpha = 0.5f + 0.5f * glint))
    }
}

/** GitHub's mark centred on [at], [side] across. */
private fun DrawScope.drawGitHubMark(at: Offset, side: Float, color: Color) {
    translate(at.x - side / 2f, at.y - side / 2f) {
        scale(side / 24f, side / 24f, pivot = Offset.Zero) { drawPath(GitHubMark, color) }
    }
}

private val GitHubMark: Path by lazy {
    androidx.compose.ui.graphics.vector.PathParser().parsePathString(
        "M12,2A10,10 0,0 0,2 12c0,4.42 2.87,8.17 6.84,9.5c0.5,0.08 0.66,-0.23 0.66,-0.5c0,-0.23 0,-0.86 0,-1.69c-2.77,0.6 -3.36,-1.34 -3.36,-1.34c-0.46,-1.16 -1.11,-1.47 -1.11,-1.47c-0.91,-0.62 0.07,-0.6 0.07,-0.6c1,0.07 1.53,1.03 1.53,1.03c0.87,1.52 2.34,1.07 2.91,0.83c0.09,-0.65 0.35,-1.09 0.63,-1.34c-2.22,-0.25 -4.55,-1.11 -4.55,-4.92c0,-1.11 0.38,-2 1.03,-2.71c-0.1,-0.25 -0.45,-1.29 0.1,-2.64c0,0 0.84,-0.27 2.75,1.02c0.79,-0.22 1.65,-0.33 2.5,-0.33c0.85,0 1.71,0.11 2.5,0.33c1.91,-1.29 2.75,-1.02 2.75,-1.02c0.55,1.35 0.2,2.39 0.1,2.64c0.65,0.71 1.03,1.6 1.03,2.71c0,3.82 -2.34,4.66 -4.57,4.91c0.36,0.31 0.69,0.92 0.69,1.85c0,1.34 0,2.42 0,2.74c0,0.27 0.16,0.59 0.67,0.5C19.14,20.16 22,16.42 22,12A10,10 0,0 0,12 2Z",
    ).toPath()
}

/** A four-pointed sparkle. */
private fun DrawScope.drawSparkle(at: Offset, r: Float, color: Color) {
    val w = r * 0.22f
    drawPath(
        Path().apply {
            moveTo(at.x, at.y - r)
            quadraticTo(at.x + w, at.y - w, at.x + r, at.y)
            quadraticTo(at.x + w, at.y + w, at.x, at.y + r)
            quadraticTo(at.x - w, at.y + w, at.x - r, at.y)
            quadraticTo(at.x - w, at.y - w, at.x, at.y - r)
            close()
        },
        color,
    )
}

/**
 * The connect-card picture: an open charging case, lit from the top left, with its two earbuds floating up out of it
 * on their own beats, and a few small things drifting round.
 */
fun DrawScope.drawCaseArt(time: Float) = inUnits { u ->
    val beat = sin(time * 2 * PI * 2).toFloat()
    listOf(Triple(12f, 30f, 0f) to Trinket.Note, Triple(88f, 26f, 0.4f) to Trinket.Headphones, Triple(90f, 72f, 0.7f) to Trinket.Earbud).forEach { (spot, kind) ->
        val (x, y, phase) = spot
        val wave = sin((time + phase) * 2 * PI * 2).toFloat()
        drawTrinket(kind, Offset(x * u, (y - 3f * wave) * u), 9f * u, Teal.copy(alpha = 0.75f + 0.15f * wave))
    }
    // The lid, tipped back and open.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF3A444C), Color(0xFF1E252B)), 44f * u, 60f * u),
        topLeft = Offset(26f * u, 47f * u), size = Size(48f * u, 12f * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * u),
    )
    // Two earbuds rising out of the case, each with a teal tip.
    listOf(-1f to 0f, 1f to 0.5f).forEach { (side, phase) ->
        val rise = sin((time + phase) * 2 * PI * 2).toFloat() * 2.2f
        val bud = 28f * u
        val x = 50f + side * 12f
        val y = 37f + rise
        // Graphite like the case, with a light rim so they read on light and dark cards; mirrored for the left one,
        // so the pair faces each other as in the case.
        translate(x * u - bud / 2f, y * u - bud / 2f) {
            scale(if (side < 0) -1f else 1f, 1f, pivot = Offset(bud / 2f, bud / 2f)) {
                drawLogo(bud, Color(0xFF3A444C), wave = false)
                drawPath(LogoShape.path(LogoShape.OUTLINE, Size(bud, bud)), Color(0xFF8A979F), style = Stroke(0.9f * u))
            }
        }
        drawCircle(Teal, 2.6f * u, Offset((x + side * 1.6f) * u, (y - 4.4f) * u))
    }
    // The case body: a rounded shell shaded like a cylinder, a seam, and a light glowing on the front.
    drawRoundRect(
        Brush.horizontalGradient(0f to Color(0xFF1B2126), 0.3f to Color(0xFF4A555D), 0.65f to Color(0xFF2D353B), 1f to Color(0xFF151A1E), startX = 22f * u, endX = 78f * u),
        topLeft = Offset(22f * u, 56f * u), size = Size(56f * u, 30f * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f * u),
    )
    drawLine(Color.Black.copy(alpha = 0.45f), Offset(25f * u, 62f * u), Offset(75f * u, 62f * u), strokeWidth = 0.8f * u)
    val glow = 0.6f + 0.4f * (0.5f + 0.5f * beat)
    drawCircle(Teal.copy(alpha = 0.35f * glow), 4.5f * u, Offset(50f * u, 76f * u))
    drawCircle(Teal.copy(alpha = glow), 1.6f * u, Offset(50f * u, 76f * u))
}

/** The pictures as plain objects, for callers outside a DrawScope extension (the screenshot test). */
object RocketArt {
    fun DrawScope.draw(time: Float) = drawRocket(time)
    fun DrawScope.star(time: Float) = drawStar(time)
    fun DrawScope.case(time: Float) = drawCaseArt(time)
}

