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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.resources.Res
import com.blazify.buds.ui.resources.liberty4nc_closed
import com.blazify.buds.ui.resources.liberty4nc_open
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Our rendered pictures of a model: its case closed, and open with the earbuds standing in it. */
class ModelPictures(val closed: DrawableResource, val open: DrawableResource)

fun pictures(art: String?): ModelPictures? = when (art) {
    "liberty4nc" -> ModelPictures(Res.drawable.liberty4nc_closed, Res.drawable.liberty4nc_open)
    else -> null
}

/**
 * The real-looking case on Home. Closed while the earbuds are away; when they connect it opens with a small spring,
 * floats gently, and the glow behind it takes the colour of the listening mode.
 */
@Composable
fun ModelHero(pictures: ModelPictures, connected: Boolean, mode: ListeningMode?, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val palette = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val open by animateFloatAsState(if (connected) 1f else 0f, tween(if (reduce) Motion.NORMAL else 520, easing = FastOutSlowInEasing), label = "open")
    val pop by animateFloatAsState(if (connected) 1f else 0.94f, if (reduce) tween(Motion.NORMAL) else spring(dampingRatio = 0.5f, stiffness = 160f), label = "pop")
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
    Box(
        modifier.fillMaxWidth().height(height).semantics { contentDescription = if (connected) "Earbuds connected" else "Earbuds not connected" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.62f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = glow.alpha * 0.32f), glow.copy(alpha = glow.alpha * 0.08f), Color.Transparent), center, r), r, center)
            // A soft shadow on the ground, smaller as the case lifts.
            val lift = if (reduce || !connected) 0f else (bob + 1f) / 2f
            val w = size.minDimension * (0.62f - 0.05f * lift)
            drawOval(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = if (palette.dark) 0.45f else 0.18f), Color.Transparent),
                    Offset(center.x, size.height * 0.86f),
                    w / 2,
                ),
                Offset(center.x - w / 2, size.height * 0.86f - w * 0.09f),
                Size(w, w * 0.18f),
            )
        }
        val side = height * 0.98f
        val y = if (reduce || !connected) 0f else bob * 4f
        Box(Modifier.size(side).graphicsLayer { scaleX = pop; scaleY = pop; translationY = y * density }) {
            Image(painterResource(pictures.closed), null, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - open })
            Image(painterResource(pictures.open), null, Modifier.fillMaxSize().graphicsLayer { alpha = open })
        }
    }
}
