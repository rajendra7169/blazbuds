package com.blazify.buds.ui.blocks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion

fun ListeningMode.label(): String = when (this) {
    ListeningMode.NoiseCancelling -> "Noise cancelling"
    ListeningMode.Transparency -> "Transparency"
    ListeningMode.Normal -> "Normal"
}

fun ListeningMode.icon() = when (this) {
    ListeningMode.NoiseCancelling -> BudsIcons.Cancelling
    ListeningMode.Transparency -> BudsIcons.Transparency
    ListeningMode.Normal -> BudsIcons.Normal
}

/**
 * The three listening modes as one control. The chosen one sits on a pill that slides to it and settles with a slight
 * overshoot, coloured like the mode itself.
 */
@Composable
fun ModeSwitch(mode: ListeningMode, modifier: Modifier = Modifier, modes: List<ListeningMode> = ListeningMode.entries, onPick: (ListeningMode) -> Unit) {
    val palette = LocalPalette.current
    val index = modes.indexOf(mode)
    val tint by animateColorAsState(
        when (mode) {
            ListeningMode.NoiseCancelling -> palette.cancelling
            ListeningMode.Transparency -> palette.transparency
            ListeningMode.Normal -> palette.muted
        },
        tween(Motion.SLOW),
        label = "tint",
    )
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(22.dp)).background(palette.raised).padding(4.dp),
    ) {
        val each = maxWidth / modes.size
        val x by animateDpAsState(
            each * index,
            if (reduce) tween(0) else spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "slide",
        )
        Box(Modifier.offset(x = x).width(each).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(tint))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            modes.forEach { item ->
                val chosen = item == mode
                val ink by animateColorAsState(if (chosen) palette.onAccent else palette.text, tween(Motion.NORMAL), label = "ink")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .press(role = Role.RadioButton) { onPick(item) }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(item.icon(), null, tint = if (chosen) palette.onAccent.copy(alpha = 1f) else palette.muted, modifier = Modifier.size(22.dp))
                    Text(
                        item.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
