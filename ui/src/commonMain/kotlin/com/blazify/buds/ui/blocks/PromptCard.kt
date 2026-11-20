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
    val time = if (still) 0.15f else tick
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
fun PromptCardLayout(title: String, body: String, buttons: PromptButtons, time: Float, art: DrawScope.(time: Float) -> Unit, modifier: Modifier = Modifier) {
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
                drawSkyNotes(time, if (palette.dark) Color.White else TealDeep)
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
        Canvas(Modifier.size(132.dp)) { art(time) }
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

/** Faint notes in the card's sky, each floating a little up and down on its own beat. */
private fun DrawScope.drawSkyNotes(time: Float, ink: Color) {
    val spots = listOf(Triple(0.12f, 0.62f, 0.0f), Triple(0.88f, 0.42f, 0.3f), Triple(0.22f, 0.3f, 0.55f), Triple(0.78f, 0.78f, 0.8f))
    spots.forEach { (x, y, phase) ->
        val wave = sin((time + phase) * 2 * PI * 2).toFloat()
        drawNote(Offset(size.width * x, size.height * y - wave * 3.dp.toPx()), 11.dp.toPx(), ink.copy(alpha = 0.13f + 0.05f * wave))
    }
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
            // Two notes tumbling out with the smoke, one to each side.
            for (i in 0..1) {
                val p = (time * 2f + i / 2f) % 1f
                val side = if (i == 0) -1f else 1f
                drawNote(Offset((50f + side * (6f + 6f * p)) * u, (86f + 14f * p) * u), 8.5f * u, Teal.copy(alpha = (1f - p) * 0.95f))
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
            val side = 12f * u
            translate(port.x - side / 2f, port.y - side / 2f) {
                drawPath(LogoShape.path(LogoShape.OUTLINE, Size(side, side)), Teal)
            }
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

/** The rocket as a plain object, for callers outside a DrawScope extension (the screenshot test). */
object RocketArt {
    fun DrawScope.draw(time: Float) = drawRocket(time)
}

