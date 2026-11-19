package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.Motion

/** Colour pairs for the preset tiles, each a soft diagonal gradient that reads well with white text. */
private val GRADIENTS = listOf(
    Color(0xFF8E7CF0) to Color(0xFFE78AC3),
    Color(0xFFF2A65A) to Color(0xFFE0603D),
    Color(0xFF4F7CF0) to Color(0xFF7B4DE0),
    Color(0xFF2BB5A3) to Color(0xFF4E8FE0),
    Color(0xFF6E5A4A) to Color(0xFFB08D6A),
    Color(0xFFF07A5A) to Color(0xFFE04A7A),
    Color(0xFF7A3FE0) to Color(0xFFE0458A),
    Color(0xFF3FA8E0) to Color(0xFF3F6FE0),
    Color(0xFFE05A9A) to Color(0xFF8A5AE0),
    Color(0xFF3FC6D9) to Color(0xFF3F8FD9),
    Color(0xFFE0A13F) to Color(0xFFE0603F),
)

/**
 * A sound preset as a tile: its own colour, its name, and the shape of its sound as a faint line across it, so the
 * difference between presets is seen before choosing. The chosen one carries a tick and lifts slightly.
 */
@Composable
fun PresetTile(
    index: Int,
    name: String,
    gains: List<Double>,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    art: androidx.compose.ui.graphics.vector.ImageVector = com.blazify.buds.ui.icons.SoundArt.forPreset(name),
    corner: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val (from, to) = GRADIENTS[index % GRADIENTS.size]
    val lift by animateFloatAsState(if (chosen) 1f else 0f, tween(Motion.NORMAL), label = "lift")
    Box(
        modifier.height(92.dp)
            .graphicsLayer { scaleX = 1f + 0.02f * lift; scaleY = 1f + 0.02f * lift }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(from, to)))
            .border(if (chosen) 2.5.dp else 0.dp, Color.White.copy(alpha = if (chosen) 0.9f else 0f), RoundedCornerShape(18.dp))
            .semantics { contentDescription = "$name preset"; selected = chosen }
            .press(role = Role.RadioButton, onClick = onClick),
    ) {
        // The preset's own sound, drawn faintly across the tile.
        Canvas(Modifier.fillMaxSize()) {
            if (gains.size < 2) return@Canvas
            val step = size.width / (gains.size - 1)
            fun y(db: Double) = (size.height * 0.55f - (db / 6.0).toFloat() * size.height * 0.28f)
            val points = gains.mapIndexed { i, g -> Offset(i * step, y(g)) }
            val curve = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points.getOrElse(i - 1) { points[i] }
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = points.getOrElse(i + 2) { p2 }
                    cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                }
            }
            drawPath(curve, Color.White.copy(alpha = 0.35f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            // A soft shine across the top.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), 0f, size.height * 0.5f))
        }
        // What the sound is about, drawn large and dim on the right.
        Icon(
            art, null,
            tint = Color.White.copy(alpha = 0.26f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(78.dp).graphicsLayer { rotationZ = -10f },
        )
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        )
        if (corner != null) Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { corner() }
        if (chosen) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Icon(BudsIcons.Check, "Chosen", tint = from, modifier = Modifier.size(16.dp))
            }
        }
    }
}
