package com.blazify.buds.ui.blocks

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

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

/** How far one notch of the wheel moves a row, in pixels. */
private const val WHEEL_STEP = 64f
