package com.blazify.buds.ui.blocks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.model.Travel
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlin.math.roundToInt

/** What sits under the mode switch: the choices that belong to the chosen mode, swapped in as the mode changes. */
@Composable
fun ModeDetails(
    sound: Sound,
    modifier: Modifier = Modifier,
    onCancel: (CancelStyle, Int?, Travel?) -> Unit,
    onTransparency: (TransparencyStyle) -> Unit,
    onWind: (Boolean) -> Unit,
    onEnvironment: (Boolean) -> Unit = {},
    onAmbientLevel: (Int) -> Unit = {},
) {
    AnimatedContent(
        sound.mode,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(tween(Motion.NORMAL)) + slideInVertically(tween(Motion.NORMAL)) { it / 8 }) togetherWith fadeOut(tween(Motion.QUICK))
        },
        label = "details",
    ) { mode ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (mode) {
                ListeningMode.NoiseCancelling -> {
                    val styles = sound.options.cancelStyles
                    // Equal capsules, at most three to a line so every name reads in full; only the ways these
                    // earbuds have. Four go two and two; a short last line keeps the same capsule width.
                    if (styles.size > 1) {
                        val perLine = when { styles.size <= 3 -> styles.size; styles.size == 4 -> 2; else -> 3 }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            styles.chunked(perLine).forEach { line ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    line.forEach { style ->
                                        Choice(style.name, sound.cancelStyle == style, Modifier.weight(1f), centered = true) { onCancel(style, null, null) }
                                    }
                                    repeat(perLine - line.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (styles.isEmpty()) Hint("Cuts out the noise around you.")
                    else when (sound.cancelStyle) {
                        CancelStyle.Adaptive -> {
                            Hint("Adjusts by itself to your fit and to the noise around you.")
                            if (sound.options.environment) SwitchRow(null, "Environment detection", sound.environmentDetection, onEnvironment, detail = "Also follows how loud it is around you")
                        }
                        CancelStyle.Manual -> if (sound.options.levels) {
                            val steps = sound.options.manualLevels
                            Hint("You choose how strongly it cancels: 1 is lightest, $steps is strongest.")
                            LevelSteps(sound.manualLevel, steps) { onCancel(CancelStyle.Manual, it, null) }
                        } else {
                            Hint("Cuts out the noise around you, the same all the time.")
                        }
                        CancelStyle.Travel -> {
                            Hint("Tuned for the steady rumble of each way of travelling.")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Travel.entries.forEach { travel ->
                                    TravelTile(travel, sound.travel == travel, Modifier.weight(1f)) { onCancel(CancelStyle.Travel, null, travel) }
                                }
                            }
                        }
                        CancelStyle.Transport -> Hint("Tuned for the low rumble of buses, trains and planes.")
                        CancelStyle.Indoor -> Hint("Tuned for offices, cafés and rooms full of voices.")
                        CancelStyle.Outdoor -> Hint("Tuned for traffic and the noise of the street.")
                    }
                    if (sound.options.wind) SwitchRow(BudsIcons.Wind, "Wind noise reduction", sound.windReduction, onWind)
                }
                ListeningMode.Transparency -> {
                    if (!sound.options.transparencyStyles) {
                        Hint("Lets the world in, as if you weren't wearing them.")
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Everything", sound.transparencyStyle == TransparencyStyle.Full, Modifier.weight(1f), centered = true) { onTransparency(TransparencyStyle.Full) }
                            Choice("Voices", sound.transparencyStyle == TransparencyStyle.Voice, Modifier.weight(1f), icon = BudsIcons.Voice, centered = true) {
                                onTransparency(TransparencyStyle.Voice)
                            }
                        }
                        Hint(if (sound.transparencyStyle == TransparencyStyle.Voice) "Brings voices through clearly and keeps other sounds down." else "Lets the world in, as if you weren't wearing them.")
                    }
                    // Only earbuds whose transparency has a strength say how many steps it has.
                    if (sound.options.ambientLevels > 0) AmbientLevel(sound.ambientLevel, sound.options.ambientLevels, onAmbientLevel)
                    if (sound.options.transparencyStyles && sound.options.wind) SwitchRow(BudsIcons.Wind, "Wind noise reduction", sound.windReduction, onWind)
                }
                ListeningMode.Normal -> Hint("No cancelling and no passthrough: the longest battery.")
            }
        }
    }
}

/** One way of travelling: its picture over its name, filled when chosen. */
@Composable
private fun TravelTile(travel: Travel, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val fill by animateColorAsState(if (chosen) palette.accent else palette.raised, tween(Motion.NORMAL), label = "travel")
    val ink by animateColorAsState(if (chosen) palette.onAccent else palette.text, tween(Motion.NORMAL), label = "travelInk")
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(16.dp)).background(fill).press(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(travel.icon(), null, tint = ink, modifier = Modifier.size(24.dp))
        Text(travel.label(), style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(top = 4.dp))
    }
}

fun Travel.label() = when (this) {
    Travel.Plane -> "Plane"
    Travel.Train -> "Train"
    Travel.Bus -> "Bus"
    Travel.Car -> "Car"
}

fun Travel.icon(): ImageVector = when (this) {
    Travel.Plane -> BudsIcons.Plane
    Travel.Train -> BudsIcons.Train
    Travel.Bus -> BudsIcons.Bus
    Travel.Car -> BudsIcons.Car
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.muted, modifier = modifier)
}

/**
 * Strength from 1 to [steps] (five on most earbuds): numbered steps that fill from the left, each a deeper shade than
 * the one before, with lightest and strongest marked at the ends.
 */
@Composable
fun LevelSteps(level: Int, steps: Int = 5, onPick: (Int) -> Unit) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..steps).forEach { step ->
                val on = step <= level
                val shade = palette.cancelling.copy(alpha = 0.45f + 0.55f * step / steps)
                val fill by animateColorAsState(if (on) shade else palette.raised, tween(Motion.NORMAL), label = "step")
                Box(
                    Modifier.weight(1f).height(48.dp).background(fill)
                        .semantics { contentDescription = "Strength $step of $steps" }
                        .press(role = Role.RadioButton) { onPick(step) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$step",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (step == level) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal),
                        color = if (on) Color.White else palette.muted,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Lightest", style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = Modifier.weight(1f))
            Text("Strongest", style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
    }
}

/**
 * How much of the world transparency lets in, from 1 to [steps] (twenty on Sony's): a slider that sends its value
 * once let go, with the level shown as it moves.
 */
@Composable
fun AmbientLevel(level: Int, steps: Int, onPick: (Int) -> Unit) {
    val palette = LocalPalette.current
    var moving by remember { mutableStateOf<Int?>(null) }
    val shown = (moving ?: level).coerceIn(1, steps)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ambient level", style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
            Text("$shown of $steps", style = MaterialTheme.typography.labelLarge, color = palette.transparency)
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { moving = it.roundToInt().coerceIn(1, steps) },
            onValueChangeFinished = {
                moving?.let { if (it != level) onPick(it) }
                moving = null
            },
            valueRange = 1f..steps.toFloat(),
            steps = (steps - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = palette.transparency,
                activeTrackColor = palette.transparency,
                inactiveTrackColor = palette.raised,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ambient level $shown of $steps" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = Modifier.weight(1f))
            Text("More", style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, on: Boolean, onChange: (Boolean) -> Unit, detail: String? = null, enabled: Boolean = true, color: androidx.compose.ui.graphics.Color? = null) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press(enabled = enabled, role = Role.Switch) { onChange(!on) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null && color != null) {
            Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        } else if (icon != null) {
            Icon(icon, null, tint = palette.muted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) palette.text else palette.muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        }
        Switch(
            on,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = palette.onAccent,
                uncheckedTrackColor = palette.raised,
                uncheckedBorderColor = palette.line,
                uncheckedThumbColor = palette.muted,
            ),
        )
    }
}

/** The sound as a smooth curve over the bands, with a faint line at no change. */
@Composable
fun EqCurve(gains: List<Double>, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val animated = gains.mapIndexed { i, g ->
        animateFloatAsState(g.toFloat(), spring(dampingRatio = 0.7f, stiffness = 200f), label = "band$i").value
    }
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        if (animated.size < 2) return@Canvas
        val range = (Equalizer.MAX_DB - Equalizer.MIN_DB).toFloat()
        fun y(db: Float) = size.height * (1f - (db - Equalizer.MIN_DB.toFloat()) / range)
        val step = size.width / (animated.size - 1)
        val points = animated.mapIndexed { i, g -> Offset(i * step, y(g)) }
        val curve = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points.getOrElse(i - 1) { points[i] }
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points.getOrElse(i + 2) { p2 }
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
        }
        drawLine(palette.line, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.dp.toPx())
        val area = Path().apply {
            addPath(curve)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(curve, palette.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A switch shown as a tile: an icon, a name and a word for its state, filled when on. */
@Composable
fun ToggleTile(icon: ImageVector, title: String, on: Boolean, modifier: Modifier = Modifier, detail: String? = null, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    val fill by animateColorAsState(if (on) palette.accent.copy(alpha = if (palette.dark) 0.18f else 0.12f) else palette.raised, tween(Motion.NORMAL), label = "tile")
    val ink by animateColorAsState(if (on) palette.accent else palette.muted, tween(Motion.NORMAL), label = "tileInk")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(fill)
            .semantics { contentDescription = "$title, ${if (on) "on" else "off"}" }
            .press(role = Role.Switch, onClick = onToggle).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail ?: if (on) "On" else "Off", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1, textAlign = TextAlign.Start)
    }
}
