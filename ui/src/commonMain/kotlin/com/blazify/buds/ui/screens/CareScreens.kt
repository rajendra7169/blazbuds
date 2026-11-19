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
fun BigButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, quiet: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (quiet) palette.raised else palette.accent).press(onClick = onClick).padding(horizontal = 20.dp),
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
fun FindScreen(buds: Buds, state: BudsState, back: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    val ringing = left || right
    LaunchedEffect(left, right) {
        scope.launch { buds.ring(left, right) }
        if (ringing) {
            delay(60_000)
            left = false
            right = false
        }
    }
    DisposableEffect(Unit) { onDispose { scope.launch { buds.ring(false, false) } } }
    PlaceFrame("Find my earbuds", back) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(BudsIcons.Ear, null, tint = palette.warn, modifier = Modifier.size(24.dp))
                Text("Take them out of your ears first. The sound is loud.", style = MaterialTheme.typography.bodyLarge, color = palette.text)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingButton("Left", left, state.left != null) { left = !left }
            RingButton("Right", right, state.right != null) { right = !right }
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
        Hint("An earbud in the case or out of reach can't play. Bring your phone closer and try again.")
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
fun FitTestScreen(buds: Buds, back: () -> Unit, howToWear: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FitResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    PlaceFrame("Ear tip fit test", back) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Ear.Left to result?.left, Ear.Right to result?.right).forEach { (ear, seal) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EarArt(ear, height = 150.dp)
                    Text(if (ear == Ear.Left) "Left" else "Right", style = MaterialTheme.typography.titleMedium, color = palette.text)
                    SealChip(running, seal, result != null)
                }
            }
        }
        val verdict = result
        when {
            running -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LogoLoader(size = 24.dp)
                Text("Listening… keep still for a few seconds.", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(start = 10.dp))
            }
            failed -> Hint("The earbuds didn't answer. Make sure both are in your ears, and try again.")
            verdict != null -> Panel {
                Text(
                    if (verdict.left == true && verdict.right == true) "Both seal well. You get the full bass and noise cancelling." else "Try a bigger ear tip on the side that seals poorly, or turn the earbud a little to settle it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.text,
                )
            }
            else -> Hint("Put both earbuds in your ears and start. A short tone plays.")
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
        BigButton("How to wear them", null, quiet = true, onClick = howToWear)
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

/** How to wear earbuds well, with the ear drawing: which is which, the twist that settles them, and ear tip sizes. */
@Composable
fun WearScreen(back: () -> Unit, fitTest: (() -> Unit)?) {
    val palette = LocalPalette.current
    var ear by remember { mutableStateOf(Ear.Right) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            ear = if (ear == Ear.Right) Ear.Left else Ear.Right
        }
    }
    PlaceFrame("How to wear them", back) {
        EarArt(ear, height = 200.dp)
        Panel {
            listOf(
                "Check the L and R on each earbud, and put each in its own ear.",
                "Put the earbud in with the stem pointing down, then turn it slightly towards your face until it sits snugly.",
                "The medium ear tips come fitted. If an earbud feels loose or the bass sounds thin, try a size up; if it presses, a size down.",
                "Change both sides together, then check the fit.",
            ).forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                    }
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                }
            }
        }
        if (fitTest != null) BigButton("Check the fit", BudsIcons.Ear, onClick = fitTest)
        Spacer(Modifier.height(4.dp))
    }
}
