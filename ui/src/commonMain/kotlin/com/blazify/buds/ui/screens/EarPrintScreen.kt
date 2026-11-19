package com.blazify.buds.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.earprint.AgeRange
import com.blazify.buds.core.earprint.EarPrint
import com.blazify.buds.core.earprint.HearingTest
import com.blazify.buds.core.earprint.TasteTest
import com.blazify.buds.core.earprint.TestSounds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.ui.LocalSoundOut
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.FingerprintEar
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

private enum class Stage { Intro, Hearing, Taste, Result }

/**
 * EarPrint: a sound made for one person's ears, in four calm steps. First a word on what it is, then the hearing check
 * (the ear drawn as a fingerprint fills as it goes), then six rounds of "this or that" over a short beat, and last the
 * sound itself, to compare with the original and keep on the earbuds.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun EarPrintScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit, preview: Int = 0) {
    val palette = LocalPalette.current
    val sound = LocalSoundOut.current
    var stage by remember { mutableStateOf(Stage.Intro) }
    var age by remember { mutableStateOf(AgeRange.Under30) }
    val hearing = remember { HearingTest() }
    var turn by remember { mutableIntStateOf(0) }
    var left by remember { mutableStateOf(List(8) { 0.0 }) }
    var right by remember { mutableStateOf(List(8) { 0.0 }) }
    var taste by remember { mutableStateOf<TasteTest?>(null) }
    var saved by remember { mutableStateOf(false) }
    val wasOn = remember { state.earPrint?.on == true }
    // The beat for the taste rounds takes a moment to make, so it is made in the background from the start.
    var beat by remember { mutableStateOf<ShortArray?>(null) }
    LaunchedEffect(Unit) { beat = withContext(Dispatchers.Default) { TestSounds.demo() } }
    LaunchedEffect(stage == Stage.Taste || stage == Stage.Result, beat) {
        val samples = beat
        if ((stage == Stage.Taste || stage == Stage.Result) && samples != null) sound.play(samples, loop = true)
    }
    // For the readme pictures: jump straight to a later step with made-up answers.
    remember {
        if (preview >= 1) {
            stage = Stage.Hearing
            var n = 0
            while (!hearing.done && (preview > 1 || n < 10)) { hearing.answer(n % 3 != 2); n++ }
        }
        if (preview >= 2) {
            left = EarPrint.curve(hearing.thresholds(Ear.Left), age)
            right = EarPrint.curve(hearing.thresholds(Ear.Right), age)
            taste = TasteTest(List(8) { 0.0 }).also { t -> repeat(if (preview == 2) 3 else 6) { t.choose(it % 2 == 0) } }
            stage = if (preview == 2) Stage.Taste else Stage.Result
        }
    }

    // Leaving part way puts everything back as it was.
    DisposableEffect(Unit) {
        onDispose {
            sound.stop()
            sound.steadyVolume(false)
            if (stage == Stage.Hearing) change(Change.HearingTestMode(false))
            if (!saved) change(Change.EarPrintOn(wasOn))
        }
    }

    fun finishHearing() {
        change(Change.HearingTestMode(false))
        sound.steadyVolume(false)
        left = EarPrint.curve(hearing.thresholds(Ear.Left), age)
        right = EarPrint.curve(hearing.thresholds(Ear.Right), age)
        taste = TasteTest(List(8) { 0.0 })
        stage = Stage.Taste
    }

    PlaceFrame("EarPrint", back) {
        AnimatedContent(stage, transitionSpec = { fadeIn(tween(Motion.SLOW)) togetherWith fadeOut(tween(Motion.QUICK)) }, label = "stage") { shown ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (shown) {
                    Stage.Intro -> {
                        FingerprintEar(1f, size = 240.dp, done = true)
                        Text("A sound made for your ears", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
                        Hint("Everyone hears a little differently. EarPrint checks how your ears hear, then shapes the sound to fit, and keeps it on your earbuds.", Modifier.padding(horizontal = 8.dp))
                        Panel {
                            StepLine(1, "Hearing check", "Beeps at six pitches in each ear. About three minutes.")
                            StepLine(2, "Your taste", "Six quick choices between two sounds.")
                        }
                        Panel {
                            Text("Your age", style = MaterialTheme.typography.titleMedium, color = palette.text)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AgeRange.entries.forEach { range -> Choice(range.label, range == age) { age = range } }
                            }
                            Spacer(Modifier.height(8.dp))
                            Hint("Hearing changes with age; this helps start from the right place.")
                        }
                        Hint("Find a quiet place and put both earbuds in. EarPrint shapes sound; it isn't a medical hearing test.", Modifier.padding(horizontal = 8.dp))
                        BigButton("Start", BudsIcons.Ear) {
                            change(Change.HearingTestMode(true))
                            sound.steadyVolume(true)
                            stage = Stage.Hearing
                        }
                    }
                    Stage.Hearing -> {
                        val step = hearing.current()
                        var listening by remember { mutableStateOf(false) }
                        LaunchedEffect(turn) {
                            val now = hearing.current() ?: return@LaunchedEffect
                            delay(500)
                            listening = true
                            sound.play(TestSounds.beeps(now.hz, now.level, now.ear == Ear.Left, now.ear == Ear.Right), loop = false)
                            delay(1500)
                            listening = false
                        }
                        if (step != null) {
                            Text(if (step.ear == Ear.Left) "Left ear" else "Right ear", style = MaterialTheme.typography.titleLarge, color = palette.text)
                            Text("Pitch ${(step.number - 1) % EarPrint.TEST_PITCHES.size + 1} of ${EarPrint.TEST_PITCHES.size}", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                            // Every answer lights a block; a pitch that is done fills its group of four.
                            val blocks = 4
                            val lit = hearing.pitchesDone * blocks + minOf(hearing.answersOnPitch, blocks - 1)
                            androidx.compose.runtime.key(step.ear) {
                                FingerprintEar(
                                    lit / (hearing.pitchCount * blocks).toFloat(),
                                    size = 270.dp,
                                    mirrored = step.ear == Ear.Right,
                                    listening = listening,
                                    segments = hearing.pitchCount * blocks,
                                    group = blocks,
                                )
                            }
                            Text(
                                if (listening) "Listen…" else "Did you hear the beeps?",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BigButton("Can't hear", null, Modifier.weight(1f), quiet = true) {
                                    hearing.answer(false)
                                    if (hearing.done) finishHearing() else turn++
                                }
                                BigButton("Can hear", null, Modifier.weight(1f)) {
                                    hearing.answer(true)
                                    if (hearing.done) finishHearing() else turn++
                                }
                            }
                            Text(
                                "Play again",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.accent,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).press { turn++ }.padding(10.dp),
                            )
                        }
                    }
                    Stage.Taste -> {
                        val test = taste
                        val round = test?.current()
                        var pick by remember(round?.number) { mutableStateOf<Boolean?>(null) }
                        LaunchedEffect(pick, round?.number) {
                            val r = round ?: return@LaunchedEffect
                            val curve = if (pick == true) r.b else r.a
                            change(Change.EarPrintTry(EarPrint.add(left, curve), EarPrint.add(right, curve)))
                        }
                        if (test != null && round != null) {
                            Text("Which do you prefer?", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
                            RoundBar(round.number, round.total)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SoundCard("Sound A", pick != true, Modifier.weight(1f)) { pick = false }
                                SoundCard("Sound B", pick == true, Modifier.weight(1f)) { pick = true }
                            }
                            Hint("A short beat is playing. Switch between A and B a few times, then choose.", Modifier.padding(horizontal = 8.dp))
                            BigButton(if (pick == true) "Choose B" else "Choose A", null) {
                                test.choose(pick == true)
                                if (test.done) stage = Stage.Result
                                pick = null
                            }
                            Text(
                                "No difference",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.accent,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).press {
                                    test.choose(null)
                                    if (test.done) stage = Stage.Result
                                }.padding(10.dp),
                            )
                        }
                    }
                    Stage.Result -> {
                        val tweak = taste?.sound ?: List(8) { 0.0 }
                        val finalLeft = EarPrint.add(left, tweak)
                        val finalRight = EarPrint.add(right, tweak)
                        var comparing by remember { mutableStateOf(true) }
                        LaunchedEffect(comparing) {
                            if (comparing) change(Change.EarPrintTry(finalLeft, finalRight)) else change(Change.EarPrintOn(false))
                        }
                        FingerprintEar(1f, size = 170.dp, done = true)
                        Text("Your EarPrint is ready", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
                        Panel {
                            TwoEarCurve(finalLeft, finalRight)
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                                Legend(palette.accent, "Left ear")
                                Legend(palette.cancelling, "Right ear")
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Original", !comparing, Modifier.weight(1f), centered = true) { comparing = false }
                            Choice("EarPrint", comparing, Modifier.weight(1f), centered = true) { comparing = true }
                        }
                        Hint("Switch between them while the beat plays, or play your own music.", Modifier.padding(horizontal = 8.dp))
                        BigButton("Keep my EarPrint", BudsIcons.Check) {
                            change(Change.EarPrintSave(finalLeft, finalRight, kotlin.time.Clock.System.now().epochSeconds))
                            saved = true
                            sound.stop()
                            back()
                        }
                        Text(
                            "Test again",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).press {
                                sound.stop()
                                stage = Stage.Intro
                            }.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepLine(number: Int, title: String, detail: String) {
    val palette = LocalPalette.current
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = palette.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        }
    }
}

/** How far through the six rounds: a short bar for each, filled as they are done. */
@Composable
private fun RoundBar(number: Int, total: Int) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val fill by animateColorAsState(if (i < number) palette.accent else palette.raised, tween(Motion.NORMAL), label = "round")
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(fill))
        }
        Text("$number/$total", style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = Modifier.padding(start = 6.dp))
    }
}

/** One of the two sounds: a tall card with a live wave, lit when it is the one playing. */
@Composable
private fun SoundCard(name: String, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val time = rememberInfiniteTransition(label = "wave")
    val phase by time.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(
        modifier.height(230.dp).clip(RoundedCornerShape(24.dp))
            .background(if (playing) Brush.verticalGradient(listOf(palette.cancelling, palette.accent)) else Brush.verticalGradient(listOf(palette.raised, palette.raised)))
            .border(if (playing) 0.dp else 1.dp, palette.line, RoundedCornerShape(24.dp))
            .press(role = Role.RadioButton, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxWidth().height(230.dp)) {
            val bars = 28
            val w = size.width / bars
            for (i in 0 until bars) {
                val x = i * w + w / 2
                val wave = if (playing) 0.5f + 0.5f * kotlin.math.sin((i / bars.toFloat() * 6f + phase * 6.283f) * 1.3f) else 0.25f
                val h = size.height * (0.12f + 0.22f * wave * (0.6f + 0.4f * kotlin.math.sin(i * 1.7f)))
                drawLine(
                    if (playing) Color.White.copy(alpha = 0.85f) else palette.muted.copy(alpha = 0.4f),
                    Offset(x, size.height - 16.dp.toPx()), Offset(x, size.height - 16.dp.toPx() - h),
                    strokeWidth = w * 0.45f, cap = StrokeCap.Round,
                )
            }
        }
        Text(name, style = MaterialTheme.typography.titleLarge, color = if (playing) Color.White else palette.text, modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp))
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(26.dp).clip(CircleShape)
                .background(if (playing) Color.White else Color.Transparent).border(2.dp, if (playing) Color.White else palette.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) Icon(BudsIcons.Check, null, tint = palette.cancelling, modifier = Modifier.size(16.dp))
        }
    }
}

/** Both ears' curves together, over bass, middle and treble. */
@Composable
private fun TwoEarCurve(left: List<Double>, right: List<Double>) {
    val palette = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        fun y(db: Double) = (size.height * 0.5f - (db / 6.0).toFloat() * size.height * 0.42f)
        drawLine(palette.line, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        listOf(left to palette.accent, right to palette.cancelling).forEach { (curve, color) ->
            val step = size.width / (curve.size - 1)
            val points = curve.mapIndexed { i, g -> Offset(i * step, y(g)) }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points.getOrElse(i - 1) { points[i] }
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = points.getOrElse(i + 2) { p2 }
                    cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                }
            }
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("Bass", "Middle", "Treble").forEachIndexed { i, label ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, modifier = Modifier.weight(1f), textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End })
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(width = 16.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

