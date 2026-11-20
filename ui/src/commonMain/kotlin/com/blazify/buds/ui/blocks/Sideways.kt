package com.blazify.buds.ui.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** True on a computer, where sideways rows get arrows: a mouse can't swipe them. */
val LocalArrows = staticCompositionLocalOf { false }

/**
 * A row that scrolls sideways: by swiping on a phone, and on a computer also by the plain mouse wheel, which only
 * scrolls up and down by itself. The wheel moves the row only while there is more of it to see that way; at either
 * end it scrolls the page as usual.
 */
@Composable
fun Modifier.sideways(state: ScrollState = rememberScrollState()): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(state) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val delta = change.scrollDelta
                    // A sideways swipe on a touchpad is left to the row's own scrolling.
                    if (delta.y == 0f || kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)) continue
                    val by = delta.y * WHEEL_STEP
                    val room = if (by > 0) state.value < state.maxValue else state.value > 0
                    if (!room) continue
                    event.changes.forEach { it.consume() }
                    scope.launch { state.scrollBy(by) }
                }
            }
        }
        .horizontalScroll(state)
}

/**
 * Left and right arrows for a sideways row, for beside its heading: each moves the row most of a width, and fades
 * when there is nothing more that way. Nothing on a phone.
 */
@Composable
fun RowArrows(state: ScrollState, modifier: Modifier = Modifier) {
    if (!LocalArrows.current || state.maxValue == 0) return
    val scope = rememberCoroutineScope()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ArrowButton(BudsIcons.Back, "Scroll left", state.canScrollBackward) { scope.launch { state.animateScrollBy(-page(state)) } }
        ArrowButton(BudsIcons.Forward, "Scroll right", state.canScrollForward) { scope.launch { state.animateScrollBy(page(state)) } }
    }
}

/**
 * A sideways row whose ends fade into [background] when there is more past them; on a computer each fade carries an
 * arrow that moves the row along. On a phone the fades alone hint that it swipes.
 */
@Composable
fun FadedRow(
    background: Color,
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val arrows = LocalArrows.current
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.sideways(state), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
        Box(Modifier.matchParentSize()) {
            Edge(state.canScrollBackward, background, start = true, arrows, Modifier.align(Alignment.CenterStart)) {
                scope.launch { state.animateScrollBy(-page(state)) }
            }
            Edge(state.canScrollForward, background, start = false, arrows, Modifier.align(Alignment.CenterEnd)) {
                scope.launch { state.animateScrollBy(page(state)) }
            }
        }
    }
}

@Composable
private fun Edge(shown: Boolean, background: Color, start: Boolean, arrow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(shown, modifier.fillMaxHeight(), enter = fadeIn(), exit = fadeOut()) {
        val fade = listOf(background, background.copy(alpha = 0.92f), background.copy(alpha = 0f))
        Box(
            Modifier.fillMaxHeight().width(if (arrow) 64.dp else 32.dp)
                .background(Brush.horizontalGradient(if (start) fade else fade.reversed())),
            contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            if (arrow) ArrowButton(if (start) BudsIcons.Back else BudsIcons.Forward, if (start) "Scroll left" else "Scroll right", true, onClick = onClick)
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shade by animateFloatAsState(if (enabled) 1f else 0.35f, label = "arrow")
    Box(
        Modifier.size(32.dp).alpha(shade).background(palette.raised, CircleShape)
            .press(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = palette.text, modifier = Modifier.size(16.dp))
    }
}

/** Most of what is in view, so the next items arrive with one still showing from before. */
private fun page(state: ScrollState): Float = (state.viewportSize * 0.75f).coerceAtLeast(120f)

/** How far one notch of the wheel moves a row, in pixels. */
private const val WHEEL_STEP = 64f
