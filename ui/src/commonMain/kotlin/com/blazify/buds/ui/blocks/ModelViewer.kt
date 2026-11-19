package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.floor
import kotlin.math.roundToInt

/** A model's rendered frames: the case opening and the earbuds rising out, then a turn all the way round, open. */
class ModelFrames(val open: List<DrawableResource>, val turn: List<DrawableResource>) {
    val closed get() = open.first()
    val shown get() = open.last()
}

/** The model's own frames, or Blazbuds' universal earbuds for any other pair of earbuds. */
fun frames(art: String?, kind: com.blazify.buds.core.catalog.Kind = com.blazify.buds.core.catalog.Kind.StemBuds): ModelFrames? = when {
    art == "liberty4nc" -> LIBERTY_4_NC_FRAMES
    kind == com.blazify.buds.core.catalog.Kind.StemBuds || kind == com.blazify.buds.core.catalog.Kind.RoundBuds -> UNIVERSAL_FRAMES
    else -> null
}

/**
 * The earbuds' case as if it were live 3D. It opens and the earbuds rise when they connect, and closes when they go;
 * a tap opens or closes it by hand, and a sideways drag turns it round, springing back to face you on release.
 * Neighbouring frames blend, so it moves smoothly though it is pictures rendered ahead of time.
 */
@Composable
fun ModelViewer(frames: ModelFrames, connected: Boolean, mode: ListeningMode?, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var byHand by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(connected) { byHand = null }
    val wantOpen = byHand ?: connected
    val progress = remember { Animatable(if (connected) 1f else 0f) }
    LaunchedEffect(wantOpen) {
        progress.animateTo(if (wantOpen) 1f else 0f, tween(if (reduce) Motion.NORMAL else 1300, easing = FastOutSlowInEasing))
    }
    val angle = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val glow by animateColorAsState(
        when {
            !connected -> Color.Transparent
            mode == ListeningMode.NoiseCancelling -> palette.cancelling
            mode == ListeningMode.Transparency -> palette.transparency
            else -> palette.accent
        },
        tween(Motion.SLOW * 2),
        label = "glow",
    )
    // Every opening frame is loaded up front so none of them flickers in; the turning ones once a turn begins.
    val openPainters = frames.open.map { painterResource(it) }
    val turnPainters = if (turning || angle.value != 0f) frames.turn.map { painterResource(it) } else emptyList()
    Box(
        modifier.fillMaxWidth().height(height)
            .semantics { contentDescription = if (connected) "Earbuds connected. Tap to open or close the case, drag to turn it." else "Earbuds not connected" }
            .pointerInput(frames) { detectTapGestures { byHand = !(byHand ?: connected) } }
            .pointerInput(frames) {
                detectHorizontalDragGestures(
                    onDragStart = { turning = true },
                    onDragEnd = {
                        turning = false
                        scope.launch {
                            // Back to facing forward the short way round.
                            val now = angle.value
                            val home = (now / 360f).roundToInt() * 360f
                            angle.animateTo(home, spring(dampingRatio = 0.7f, stiffness = 60f))
                            angle.snapTo(0f)
                        }
                    },
                    onDragCancel = { turning = false },
                ) { _, dx ->
                    // Turning only means something with the case open.
                    if (progress.value > 0.98f) scope.launch { angle.snapTo(angle.value - dx * 0.5f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.62f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = glow.alpha * 0.32f), glow.copy(alpha = glow.alpha * 0.08f), Color.Transparent), center, r), r, center)
            val w = size.minDimension * 0.6f
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = if (palette.dark) 0.45f else 0.16f), Color.Transparent), Offset(center.x, size.height * 0.88f), w / 2),
                Offset(center.x - w / 2, size.height * 0.88f - w * 0.09f),
                Size(w, w * 0.18f),
            )
        }
        val y = if (reduce || !connected) 0f else bob * 3f
        Box(Modifier.size(height).graphicsLayer { translationY = y * density }) {
            if (turnPainters.isNotEmpty() && angle.value != 0f) {
                Blend(turnPainters, ((angle.value / 30f) % 12f + 12f) % 12f, wrap = true)
            } else {
                Blend(openPainters, progress.value * (openPainters.size - 1), wrap = false)
            }
        }
    }
}

/** The frame at [at], blended into the next by how far between them it is. */
@Composable
private fun Blend(painters: List<Painter>, at: Float, wrap: Boolean) {
    val first = floor(at).toInt().coerceIn(0, painters.size - 1)
    val next = if (wrap) (first + 1) % painters.size else (first + 1).coerceAtMost(painters.size - 1)
    val mix = (at - floor(at)).coerceIn(0f, 1f)
    Image(painters[first], null, Modifier.fillMaxSize())
    if (next != first && mix > 0.01f) Image(painters[next], null, Modifier.fillMaxSize().graphicsLayer { alpha = mix })
}
