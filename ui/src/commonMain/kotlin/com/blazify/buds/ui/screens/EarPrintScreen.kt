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
import com.blazify.buds.ui.blocks.EarHalfPrint
import androidx.compose.ui.text.drawText
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.width
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

private enum class Stage { Intro, Age, Quiet, Hearing, Taste, Result }

/**
 * EarPrint: a sound made for one person's ears, in calm steps. A word on what it is; the person's age; a check that
 * the room is quiet enough, with the noise shown live; the hearing check at full volume (the ear drawn as a
 * fingerprint fills as it goes); six rounds of "this or that" over a short beat; and last the sound itself, to
 * compare with the original and keep on the earbuds.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun EarPrintScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit, preview: Int = 0) {
    val palette = LocalPalette.current
    val sound = LocalSoundOut.current
    var stage by remember { mutableStateOf(Stage.Intro) }
    var age by remember { mutableStateOf<AgeRange?>(null) }
    val seed = remember { kotlin.random.Random.nextInt(1000) }
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
    // The taste rounds play over the beat, or over the person's own music playing in any app.
    var ownMusic by remember { mutableStateOf(false) }
    LaunchedEffect(stage == Stage.Taste || stage == Stage.Result, beat, ownMusic) {
        val samples = beat
        if (ownMusic) sound.stop()
        else if ((stage == Stage.Taste || stage == Stage.Result) && samples != null) sound.play(samples, loop = true)
    }
    // For the readme pictures: jump straight to a later step with made-up answers.
    remember {
        if (preview == 4) stage = Stage.Age
        if (preview == 5) stage = Stage.Quiet
        if (preview in 1..3) {
            stage = Stage.Hearing
            var n = 0
            while (!hearing.done && (preview > 1 || n < 10)) { hearing.answer(n % 3 != 2); n++ }
        }
        if (preview in 2..3) {
            left = EarPrint.curve(hearing.thresholds(Ear.Left), age ?: AgeRange.From18)
            right = EarPrint.curve(hearing.thresholds(Ear.Right), age ?: AgeRange.From18)
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
        left = EarPrint.curve(hearing.thresholds(Ear.Left), age ?: AgeRange.From18)
        right = EarPrint.curve(hearing.thresholds(Ear.Right), age ?: AgeRange.From18)
        taste = TasteTest(List(8) { 0.0 })
        stage = Stage.Taste
    }

    PlaceFrame("EarPrint", back) {
        AnimatedContent(stage, transitionSpec = { fadeIn(tween(Motion.SLOW)) togetherWith fadeOut(tween(Motion.QUICK)) }, label = "stage") { shown ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (shown) {
                    Stage.Intro -> {
                        EarHalfPrint(size = 280.dp, seed = seed)
                        Text("A sound made for your ears", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
                        Hint("Everyone hears a little differently. EarPrint checks how your ears hear, then shapes the sound to fit, and keeps it on your earbuds.", Modifier.padding(horizontal = 8.dp))
                        Panel {
                            StepLine(1, "A quiet room", "Noise hides the quiet beeps, so the room is checked first.")
                            StepLine(2, "Hearing check", "Beeps at six pitches in each ear, at full volume. About three minutes.")
                            StepLine(3, "Your taste", "Six quick choices between two sounds.")
                        }
                        Hint("Put both earbuds in. EarPrint shapes sound; it isn't a medical hearing test.", Modifier.padding(horizontal = 8.dp))
                        BigButton("Start", BudsIcons.Ear) { stage = Stage.Age }
                    }
                    Stage.Age -> {
                        Text("How old are you?", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
                        Hint("Hearing of high sounds fades a little with the years; this starts EarPrint from the right place.", Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AgeRange.entries.forEach { range ->
                                Choice(range.label, range == age, Modifier.fillMaxWidth(), centered = true) { age = range }
                            }
                        }
                        BigButton("Next", null, enabled = age != null) { stage = Stage.Quiet }
                    }
                    Stage.Quiet -> QuietCheck(onReady = {
                        change(Change.HearingTestMode(true))
                        sound.steadyVolume(true)
                        stage = Stage.Hearing
                    })
                    Stage.Hearing -> {
                        val step = hearing.current()
                        var listening by remember { mutableStateOf(false) }
                        LaunchedEffect(turn) {
                            val now = hearing.current() ?: return@LaunchedEffect
                            delay(500)
                            listening = true
                            sound.play(TestSounds.beeps(now.hz, now.level, now.ear != Ear.Right, now.ear != Ear.Left), loop = false)
                            delay(1500)
                            listening = false
                        }
                        if (step != null) {
                            Text(when (step.ear) { Ear.Left -> "Left ear"; Ear.Right -> "Right ear"; null -> "Both ears" }, style = MaterialTheme.typography.titleLarge, color = palette.text)
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
                                SoundCard("Sound A", round.a, pick != true, Modifier.weight(1f)) { pick = false }
                                SoundCard("Sound B", round.b, pick == true, Modifier.weight(1f)) { pick = true }
                            }
                            MusicChoice(ownMusic) { ownMusic = it }
                            Hint(
                                if (ownMusic) "Play a song you know in any app. Tap A and B a few times while it plays, then choose."
                                else "A short beat is playing. Tap A and B a few times, then choose. Real music makes the difference easier to hear.",
                                Modifier.padding(horizontal = 8.dp),
                            )
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
                                if (EarPrint.same(finalLeft, finalRight)) {
                                    Legend(palette.cancelling, "Your EarPrint, both ears")
                                } else {
                                    Legend(palette.accent, "Left ear")
                                    Legend(palette.cancelling, "Right ear")
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Original", !comparing, Modifier.weight(1f), centered = true) { comparing = false }
                            Choice("EarPrint", comparing, Modifier.weight(1f), centered = true) { comparing = true }
                        }
                        MusicChoice(ownMusic) { ownMusic = it }
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
private fun SoundCard(name: String, curve: List<Double>, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
                // The bars follow this sound's own shape, bass on the left and treble on the right, with a gentle sway.
                val at = i / (bars - 1f) * (curve.size - 1)
                val lo = at.toInt().coerceAtMost(curve.size - 2)
                val db = curve[lo] + (curve[lo + 1] - curve[lo]) * (at - lo)
                val sway = if (playing) 0.02f * kotlin.math.sin(i * 0.9f + phase * 6.283f) else 0f
                val h = size.height * (0.24f + 0.032f * db.toFloat() + sway).coerceIn(0.04f, 0.6f)
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


/**
 * Is the room quiet enough? The microphone's level, live, on a bar like a sound meter with the line it needs to stay
 * under. Continue opens once it has been quiet for two seconds; where the room can't be heard, the person decides.
 */
@Composable
private fun QuietCheck(onReady: () -> Unit) {
    val palette = LocalPalette.current
    val ear = com.blazify.buds.ui.LocalRoomEar.current
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var level by remember { mutableStateOf<Double?>(null) }
    var quietSince by remember { mutableStateOf<Long?>(null) }
    var ready by remember { mutableStateOf(false) }
    var deaf by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ear.ask { allowed = it } }
    androidx.compose.runtime.DisposableEffect(allowed) {
        val stop = if (allowed == true) ear.listen { db ->
            level = db
        } else null
        if (allowed == true && stop == null) deaf = true
        if (allowed == false) deaf = true
        onDispose { stop?.invoke() }
    }
    // Quiet for two seconds in a row opens the way on.
    LaunchedEffect(level) {
        val db = level ?: return@LaunchedEffect
        if (db < com.blazify.buds.core.earprint.RoomNoise.QUIET) {
            if (quietSince == null) quietSince = clock()
        } else {
            quietSince = null
            ready = false
        }
        quietSince?.let { if (clock() - it >= 2000) ready = true }
    }
    Text("Is it quiet here?", style = MaterialTheme.typography.headlineMedium, color = palette.text, textAlign = TextAlign.Center)
    Hint("Noise hides the quiet beeps and makes the result wrong. Only the loudness is heard; nothing is recorded.", Modifier.padding(horizontal = 8.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NoiseBar(level, Modifier.height(240.dp).width(64.dp))
        Spacer(Modifier.width(28.dp))
        Column {
            Text(
                level?.let { "${it.toInt()}" } ?: "–",
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = palette.text,
            )
            Text("dB around you", style = MaterialTheme.typography.labelLarge, color = palette.muted)
        }
    }
    val quiet = level?.let { it < com.blazify.buds.core.earprint.RoomNoise.QUIET }
    Text(
        when {
            deaf -> "The room can't be heard here. Make sure it's quiet, then continue."
            level == null -> "Listening to the room…"
            ready -> "Quiet. Your results will be precise."
            quiet == true -> "Quiet. Stay still a moment…"
            else -> "Too noisy. Find a quieter place, away from people, fans and traffic."
        },
        style = MaterialTheme.typography.titleMedium,
        color = when {
            ready || deaf -> palette.text
            quiet == false -> palette.low
            else -> palette.muted
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    BigButton(if (ready || deaf) "Continue" else "Waiting for quiet", null, enabled = ready || deaf, onClick = onReady)
}

/** Milliseconds on a clock that only goes forward. */
private val start = kotlin.time.TimeSource.Monotonic.markNow()
private fun clock(): Long = start.elapsedNow().inWholeMilliseconds

/** A tall bar like a sound meter: green low, amber, red high, with the line to stay under. */
@Composable
private fun NoiseBar(level: Double?, modifier: Modifier) {
    val palette = LocalPalette.current
    val shown by androidx.compose.animation.core.animateFloatAsState(((level ?: 0.0) / 80.0).toFloat().coerceIn(0f, 1f), tween(120), label = "noise")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = palette.muted)
    Canvas(modifier) {
        val barW = size.width * 0.42f
        val x = size.width - barW
        val r = CornerRadius(barW / 2)
        drawRoundRect(palette.raised, Offset(x, 0f), Size(barW, size.height), r)
        val fillH = size.height * shown
        drawRoundRect(
            Brush.verticalGradient(listOf(palette.low, palette.warn, palette.good), 0f, size.height),
            Offset(x, size.height - fillH), Size(barW, fillH), r,
        )
        // The line to stay under.
        val limitY = size.height * (1 - (com.blazify.buds.core.earprint.RoomNoise.QUIET / 80.0).toFloat())
        drawLine(palette.text, Offset(x - 6.dp.toPx(), limitY), Offset(size.width, limitY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        listOf(0, 20, 40, 60, 80).forEach { db ->
            val y = size.height * (1 - db / 80f)
            val text = measurer.measure("$db", labelStyle)
            drawText(text, topLeft = Offset(0f, (y - text.size.height / 2).coerceIn(0f, size.height - text.size.height)))
        }
    }
}

/** The beat or the person's own music, for hearing the difference. */
@Composable
private fun MusicChoice(own: Boolean, pick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Demo beat", !own, Modifier.weight(1f), centered = true) { pick(false) }
        Choice("Use my music", own, Modifier.weight(1f), centered = true) { pick(true) }
    }
}
