package com.blazify.buds.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.FitResult
import com.blazify.buds.ui.blocks.EarArt
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.LogoLoader
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A wide, filled button for the one thing a page is for. */
@Composable
fun BigButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (quiet) palette.raised else palette.accent).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .press(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = if (quiet) palette.text else palette.onAccent, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (quiet) palette.text else palette.onAccent, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
    }
}

/**
 * Find my earbuds: each one plays a loud sound on its own, to be followed to wherever it fell. It stops by itself after
 * a minute, and as soon as the page closes.
 */
@Composable
fun FindScreen(state: BudsState, ring: (Boolean, Boolean) -> Unit, back: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    val ringing = left || right
    LaunchedEffect(left, right) {
        ring(left, right)
        if (ringing) {
            delay(60_000)
            left = false
            right = false
        }
    }
    DisposableEffect(Unit) { onDispose { ring(false, false) } }
    PlaceFrame("Find my earbuds", back) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(BudsIcons.Ear, null, tint = palette.warn, modifier = Modifier.size(24.dp))
                Text("Take them out of your ears first. The sound is loud.", style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingButton("Left", left, state.isReady) { left = !left }
            RingButton("Right", right, state.isReady) { right = !right }
        }
        if (ringing) {
            BigButton("Stop", BudsIcons.Close, quiet = true) {
                left = false
                right = false
            }
        } else {
            BigButton("Play on both", BudsIcons.Find) {
                left = true
                right = true
            }
        }
        Hint("The sound plays through the earbuds like music, so they must be connected and out of the case. Bring your phone closer if you hear nothing.")
    }
}

@Composable
private fun RingButton(label: String, on: Boolean, reachable: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val wave = rememberInfiniteTransition(label = "ring")
    val beat by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "beat")
    val fill by animateColorAsState(if (on) palette.accent else palette.raised, label = "fill")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(128.dp).drawBehind {
                if (on) {
                    drawCircle(palette.accent.copy(alpha = 0.3f * (1f - beat)), size.minDimension / 2 * (0.75f + 0.25f * beat))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(fill).press(enabled = reachable, role = Role.Switch, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BudsIcons.Earbuds, null, tint = if (on) palette.onAccent else palette.text, modifier = Modifier.size(40.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = palette.text)
        Text(
            when {
                !reachable -> "Not connected"
                on -> "Playing"
                else -> "Tap to play"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (on) palette.accent else palette.muted,
        )
    }
}

/**
 * The ear tip fit test: both earbuds play a short tone and listen to how much of it stays in the ear, which tells how
 * well each tip seals. A poor seal costs bass and noise cancelling.
 */
@Composable
fun FitTestScreen(buds: Buds, back: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FitResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    PlaceFrame("Ear tip fit test", back) {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Ear.Left to result?.left, Ear.Right to result?.right).forEach { (ear, seal) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EarArt(ear, height = 140.dp)
                        Text(if (ear == Ear.Left) "Left" else "Right", style = MaterialTheme.typography.titleMedium, color = palette.text)
                        SealChip(running, seal, result != null)
                    }
                }
            }
        }
        val verdict = result
        Panel {
            when {
                running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoLoader(size = 24.dp)
                    Text("Listening… keep still for a few seconds.", style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.padding(start = 12.dp))
                }
                failed -> Text("The earbuds didn't answer. Make sure both are in your ears, then try again.", style = MaterialTheme.typography.bodyLarge, color = palette.text)
                verdict != null -> Text(
                    if (verdict.left == true && verdict.right == true) "Both seal well: you get the full bass and noise cancelling." else "One side seals poorly. Settle that earbud by turning it a little, or try a bigger ear tip on that side.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.text,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium, color = palette.text)
                    Text("Put both earbuds in your ears and tap Start. A short tone plays and each earbud listens to how well its tip seals.", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                }
            }
        }
        BigButton(if (verdict == null) "Start" else "Test again", BudsIcons.Ear) {
            if (running) return@BigButton
            running = true
            failed = false
            result = null
            scope.launch {
                val found = buds.fitTest()
                running = false
                if (found == null) failed = true else result = found
            }
        }
    }
}

@Composable
private fun SealChip(running: Boolean, seal: Boolean?, done: Boolean) {
    val palette = LocalPalette.current
    val (text, color) = when {
        running -> "Listening" to palette.muted
        !done -> "Not tested" to palette.muted
        seal == true -> "Good seal" to palette.good
        seal == false -> "Poor seal" to palette.warn
        else -> "Not heard" to palette.muted
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
