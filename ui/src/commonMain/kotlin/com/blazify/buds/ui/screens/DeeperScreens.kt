package com.blazify.buds.ui.screens

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.blazify.buds.ui.blocks.press
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.ui.blocks.BandSlider
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.EqCurve
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.blocks.label
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/** Whether two sets of bands are the same sound, to the tenth of a decibel. */
private fun same(a: List<Double>, b: List<Double>): Boolean =
    a.size >= 8 && b.size >= 8 && (0 until 8).all { kotlin.math.abs(a[it] - b[it]) < 0.06 }

/**
 * The Sound tab: the sound itself as a curve you drag, your own saved sounds, which you can share as a short code, and
 * the presets as tiles.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(state: BudsState, change: (Change) -> Unit, back: (() -> Unit)? = null, earPrint: () -> Unit = {}) = PlaceFrame("Sound", back) {
    state.earPrint?.let { print -> EarPrintCard(print, state.bothConnected, earPrint) { change(Change.EarPrintOn(it)) } }
    val eq = state.equalizer
    if (eq == null) {
        Hint("These earbuds have no equalizer the app can reach.")
        return@PlaceFrame
    }
    val palette = LocalPalette.current
    val prefs = com.blazify.buds.ui.LocalPrefs.current
    val share = com.blazify.buds.ui.LocalShare.current
    // The bands move under the finger and are sent once the finger lifts, so the earbuds aren't flooded.
    var draft by remember { mutableStateOf(eq.left) }
    LaunchedEffect(eq.left) { draft = eq.left }
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.blazify.buds.ui.SavedSound?>(null) }
    // Built-in presets stay as they are; only the person's own sounds can be reshaped. The one being shaped is
    // remembered by name, as its bands change under the finger.
    var editing by remember { mutableStateOf<String?>(null) }
    val matching = if (eq.preset == null) prefs.sounds.firstOrNull { same(it.gains, eq.left) } else null
    LaunchedEffect(eq.preset, matching?.name) {
        if (eq.preset != null) editing = null else if (matching != null) editing = matching.name
    }
    val mine = editing?.takeIf { eq.preset == null }?.let { n -> prefs.sounds.firstOrNull { it.name == n } }
    var custom by remember { mutableStateOf(eq.preset == null) }
    fun shareSound(sound: com.blazify.buds.ui.SavedSound) =
        share("My Blazbuds sound \"${sound.name}\". In Blazbuds, open Sound › Custom › Add a code and paste:\n${sound.code()}")

    ThreeDCard(state, change)
    Spacer(Modifier.height(14.dp))
    TwoWay("Presets", "Custom", onSecond = custom) { custom = it }
    Spacer(Modifier.height(14.dp))
    if (!custom) {
        // Presets: just the sounds to pick from, each with its picture.
        TileGrid(eq.presets) { i, preset, modifier ->
            com.blazify.buds.ui.blocks.PresetTile(i, preset.name, preset.gains, preset.id == eq.presetId, modifier, showCurve = false) { change(Change.Preset(preset.id)) }
        }
    } else {
        // Custom: the curve big, the person's own sounds underneath.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mine?.name ?: "Custom", style = MaterialTheme.typography.titleLarge, color = palette.text)
                Text(
                    when {
                        !state.bothConnected -> "Take both earbuds out of the case to change it"
                        mine != null -> "Drag a point, or tap where you want it"
                        else -> "Tap + to make your own sound"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
            if (mine != null) {
                IconButton(BudsIcons.Undo, "Back to flat") {
                    val flat = List(draft.size) { 0.0 }
                    draft = flat
                    prefs.saveSound(mine.name, flat)
                    change(Change.Bands(flat))
                }
                IconButton(BudsIcons.Share, "Share ${mine.name}") { shareSound(mine.copy(gains = draft)) }
            }
        }
        com.blazify.buds.ui.blocks.EqEditor(
            draft,
            eq.frequencies,
            editable = state.bothConnected && mine != null,
            height = 320.dp,
            onChange = { draft = it },
            onDone = {
                mine?.let { prefs.saveSound(it.name, draft) }
                change(Change.Bands(draft))
            },
        )
        Spacer(Modifier.height(10.dp))
        // Two rows that scroll sideways: + first, then a shared code, then the person's sounds.
        val cards = buildList<@Composable () -> Unit> {
            add { SoundSquare("New", icon = BudsIcons.Plus, dashed = true) { naming = true } }
            add { SoundSquare("Add a code", icon = BudsIcons.Download, dashed = true) { importing = true } }
            prefs.sounds.forEach { sound ->
                add {
                    SoundSquare(sound.name, gains = sound.gains, chosen = mine?.name == sound.name, onLongClick = { deleting = sound }) {
                        editing = sound.name
                        change(Change.Bands(sound.gains))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(2).forEach { pair ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { it() } }
            }
        }
        if (prefs.sounds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Hint("Press and hold one of your sounds to delete it.")
        }
    }
    if (naming) {
        NameDialog(
            title = "New sound",
            start = "",
            hint = "Name, like Gym or Late night",
            confirm = "Save",
            onDone = { text ->
                val base = text.trim()
                if (base.isNotEmpty()) {
                    // A new name each time, so an earlier sound is never written over. It starts from what's playing.
                    var n = 1
                    var unique = base
                    while (prefs.sounds.any { it.name.equals(unique, ignoreCase = true) }) unique = "$base ${++n}"
                    prefs.saveSound(unique, draft)
                    editing = unique
                    change(Change.Bands(draft))
                }
                naming = false
            },
        )
    }
    deleting?.let { sound ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = palette.card,
            title = { Text("Delete ${sound.name}?", color = palette.text) },
            text = { Text("It goes from your sounds. If it's playing now, the earbuds keep it until you choose another.", color = palette.muted) },
            confirmButton = {
                Choice("Delete", true) {
                    prefs.deleteSound(sound.name)
                    if (editing == sound.name) editing = null
                    deleting = null
                }
            },
            dismissButton = { Choice("Cancel", false) { deleting = null } },
        )
    }
    if (importing) {
        NameDialog(
            title = "Add a shared sound",
            start = "",
            hint = "Paste the code or the whole message",
            confirm = "Add",
            onDone = { text ->
                com.blazify.buds.ui.SavedSound.fromCode(text)?.let { sound ->
                    prefs.saveSound(sound.name, sound.gains)
                    editing = sound.name
                    change(Change.Bands(sound.gains))
                }
                importing = false
            },
        )
    }
}

/** Two choices as one pill that slides between them; [onSecond] is true when the second one is picked. */
@Composable
private fun TwoWay(first: String, second: String, onSecond: Boolean, onPick: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    val x by androidx.compose.animation.core.animateFloatAsState(if (onSecond) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 420f), label = "two")
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(palette.raised).padding(4.dp),
    ) {
        val half = maxWidth / 2
        Box(Modifier.offset(x = half * x).width(half).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(palette.accent))
        Row(Modifier.fillMaxSize()) {
            listOf(false to first, true to second).forEach { (which, name) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).press(role = androidx.compose.ui.semantics.Role.Tab) { onPick(which) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = if (which == onSecond) palette.onAccent else palette.muted)
                }
            }
        }
    }
}

/** A square card for one of the person's sounds, or for making one: its name, and its shape drawn small. */
@Composable
private fun SoundSquare(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    gains: List<Double>? = null,
    chosen: Boolean = false,
    dashed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val ink = if (chosen) androidx.compose.ui.graphics.Color.White else palette.text
    Box(
        Modifier.size(104.dp).clip(shape)
            .background(
                if (chosen) androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.accent, palette.cancelling))
                else androidx.compose.ui.graphics.SolidColor(if (dashed) androidx.compose.ui.graphics.Color.Transparent else palette.raised),
            )
            .then(
                if (dashed) Modifier.drawBehind {
                    drawRoundRect(
                        palette.line,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        ),
                    )
                } else Modifier,
            )
            .semantics { contentDescription = name; selected = chosen }
            .press(onLongClick = onLongClick, onClick = onClick),
    ) {
        if (gains != null && gains.size > 1) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(46.dp).align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 12.dp)) {
                val step = size.width / (gains.size - 1)
                val points = gains.mapIndexed { i, g -> androidx.compose.ui.geometry.Offset(i * step, size.height / 2 - (g / 6.0).toFloat() * size.height / 2) }
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points.getOrElse(i - 1) { points[i] }
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points.getOrElse(i + 2) { p2 }
                        cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
                    }
                }
                drawPath(path, if (chosen) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f) else palette.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                maxLines = 2,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                icon?.let { Icon(it, null, tint = palette.muted, modifier = Modifier.size(if (it == BudsIcons.Plus) 30.dp else 22.dp)) }
                if (icon != BudsIcons.Plus) Text(name, style = MaterialTheme.typography.labelMedium, color = palette.muted)
            }
        }
    }
}

/** Tiles two to a row on a phone, three on anything wider. */
@Composable
private fun <T> TileGrid(items: List<T>, tile: @Composable (Int, T, Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(columns).forEachIndexed { row, group ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.forEachIndexed { i, item -> tile(row * columns + i, item, Modifier.weight(1f)) }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
    }
}

/** A small dialog with one text field. */
@Composable
private fun NameDialog(title: String, start: String, hint: String, confirm: String, onDone: (String) -> Unit) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(start) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone("") },
        containerColor = palette.card,
        title = { Text(title, color = palette.text) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(400) },
                placeholder = { Text(hint, color = palette.muted) },
                singleLine = confirm == "Save",
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Choice(confirm, true) { onDone(text) } },
        dismissButton = { Choice("Cancel", false) { onDone("") } },
    )
}

private fun Int.hertz() = if (this >= 1000) "${this / 1000}${if (this % 1000 >= 100) "." + (this % 1000) / 100 else ""} kHz" else "$this Hz"

fun TouchAction.label() = when (this) {
    TouchAction.VolumeUp -> "Volume up"
    TouchAction.VolumeDown -> "Volume down"
    TouchAction.PreviousSong -> "Previous song"
    TouchAction.NextSong -> "Next song"
    TouchAction.PlayPause -> "Play or pause"
    TouchAction.VoiceAssistant -> "Voice assistant"
    TouchAction.ListeningMode -> "Change listening mode"
    TouchAction.None -> "Nothing"
}

fun Press.label() = when (this) {
    Press.Single -> "Tap"
    Press.Double -> "Double tap"
    Press.Long -> "Press and hold"
    Press.Triple -> "Triple tap"
}

/** The mark for each kind of touch: one dot, two, three, or a bar for holding. */
private fun Press.glyph(): String = when (this) {
    Press.Single -> "•"
    Press.Double -> "••"
    Press.Triple -> "•••"
    Press.Long -> "▬"
}

/**
 * Touch controls, the way people think about them: an ear with the earbud in it and the spot to touch, a switch
 * between left and right, and one row for each kind of touch showing what it does. A row opens the choices.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ControlsScreen(state: BudsState, change: (Change) -> Unit, back: (() -> Unit)? = null) = PlaceFrame("Controls", back) {
    val controls = state.controls
    if (controls == null) {
        Hint("These earbuds' controls can't be changed from the app.")
        return@PlaceFrame
    }
    val palette = LocalPalette.current
    var ear by remember { mutableStateOf(Ear.Left) }
    var picking by remember { mutableStateOf<Press?>(null) }
    com.blazify.buds.ui.blocks.EarArt(ear, height = 210.dp)
    SideSwitch(ear) { ear = it }
    Panel(padding = 6.dp) {
        Press.entries.forEach { press ->
            val now = controls.actions[ear to press] ?: return@forEach
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).press { picking = press }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(press.glyph(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, letterSpacing = 1.sp), color = palette.accent, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(press.label(), style = MaterialTheme.typography.bodyLarge, color = palette.text)
                    Text(now.label(), style = MaterialTheme.typography.bodyMedium, color = if (now == TouchAction.None) palette.muted else palette.accent)
                }
                Icon(BudsIcons.Forward, null, tint = palette.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    state.sound?.let { sound ->
        Panel {
            SectionTitle("Change listening mode goes through")
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListeningMode.entries.forEach { mode ->
                    val on = mode in sound.cycle
                    Choice(mode.label(), on) {
                        val next = if (on) sound.cycle - mode else sound.cycle + mode
                        if (next.size >= 2) change(Change.ModeCycle(next))
                    }
                }
            }
            Hint("At least two modes stay in the cycle.")
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Reset to how they came",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).press { change(Change.ResetControls) }.padding(12.dp),
        )
    }
    val press = picking
    if (press != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { picking = null },
            containerColor = palette.card,
            contentColor = palette.text,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("${if (ear == Ear.Left) "Left" else "Right"} earbud · ${press.label()}", style = MaterialTheme.typography.titleMedium, color = palette.text, modifier = Modifier.padding(bottom = 8.dp))
                controls.available.forEach { action ->
                    val chosen = controls.actions[ear to press] == action
                    Row(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).press {
                            change(Change.Touch(ear, press, action))
                            picking = null
                        }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(action.label(), style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                        if (chosen) Icon(BudsIcons.Check, "Chosen", tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Left or right, as one pill that slides between them. */
@Composable
private fun SideSwitch(ear: Ear, onPick: (Ear) -> Unit) {
    val palette = LocalPalette.current
    val x by androidx.compose.animation.core.animateFloatAsState(if (ear == Ear.Left) 0f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 420f), label = "side")
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(palette.raised).padding(4.dp),
    ) {
        val half = maxWidth / 2
        Box(Modifier.offset(x = half * x).width(half).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(palette.card))
        Row(Modifier.fillMaxSize()) {
            listOf(Ear.Left to "Left", Ear.Right to "Right").forEach { (side, name) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).press(role = androidx.compose.ui.semantics.Role.Tab) { onPick(side) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = if (side == ear) palette.text else palette.muted)
                }
            }
        }
    }
}

private val PROMPTS = listOf(
    Toggle.TouchTone to "A click on each touch",
    Toggle.WearingTone to "A tone as you put them in",
    Toggle.LowBatteryPrompt to "A voice when the battery runs low",
    Toggle.SoundLeakCompensation to "Keeps the bass when the fit isn't perfect",
    Toggle.WearDetection to "Pauses when you take one out",
    Toggle.TouchLock to "Ignores taps until you turn it off",
    Toggle.GamingMode to "Less delay for games and videos",
    Toggle.Surround to "A wider, spatial sound",
)

@Composable
fun PromptsSection(state: BudsState, change: (Change) -> Unit) {
    Panel {
        SectionTitle("Sound and prompts")
        PROMPTS.filter { it.first in state.toggles }.forEach { (toggle, detail) ->
            SwitchRow(toggle.icon(), toggle.label(), state.toggles[toggle] == true, { change(Change.Switch(toggle, it)) }, detail = detail, color = toggle.hue())
        }
    }
    state.autoPowerOff?.let { off ->
        Panel {
            SwitchRow(BudsIcons.Power, "Turn off when not in use", off.enabled, { change(Change.PowerOff(it, off.minutes)) }, color = com.blazify.buds.ui.blocks.Hue.Red)
            if (off.enabled) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    off.choices.forEach { minutes -> Choice("$minutes min", minutes == off.minutes) { change(Change.PowerOff(true, minutes)) } }
                }
            }
        }
    }
}

@Composable
fun HostsSection(state: BudsState, change: (Change) -> Unit) {
    val palette = LocalPalette.current
    val points = state.multipoint
    if (points == null) return
    Panel {
        SectionTitle("Connection")
        SwitchRow(
            BudsIcons.Laptop,
            "Two devices at once",
            points.enabled,
            { change(Change.TwoDevices(it)) },
            color = com.blazify.buds.ui.blocks.Hue.Blue,
            detail = if (state.toggles[Toggle.Ldac] == true) "Turns off Best quality (LDAC)" else "Music moves to whichever device plays",
        )
    }
    if (points.devices.isNotEmpty()) {
        Panel {
            SectionTitle("Remembered devices")
            points.devices.forEach { host ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (host.looksLikeComputer()) BudsIcons.Laptop else BudsIcons.Phone, null, tint = palette.muted, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(host.name, style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (host.connected) "Connected" else "Not connected", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    }
                    Choice(if (host.connected) "Disconnect" else "Connect", false) { change(Change.HostConnection(host.address, !host.connected)) }
                    Choice("Forget", false) { change(Change.Forget(host.address)) }
                }
            }
        }
    }
}

@Composable
fun AboutSection(state: BudsState) {
    val palette = LocalPalette.current
    Panel {
        SectionTitle("About these earbuds")
        listOfNotNull(
            "Name" to state.name,
            "Model" to state.model,
            state.firmware?.let { "Firmware" to it },
            state.serial?.let { "Serial number" to it },
            state.left?.let { "Left battery" to "${it.percent}%" + if (it.charging) ", charging" else "" },
            state.right?.let { "Right battery" to "${it.percent}%" + if (it.charging) ", charging" else "" },
            state.case?.let { "Case battery" to "${it.percent}%" + if (it.charging) ", charging" else "" },
        ).forEach { (name, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = palette.text)
            }
        }
    }
    Hint("Battery is shown in the steps the earbuds report: fifths, so 20, 40, 60, 80 or 100%. Firmware updates come from the brand's own app.")
}

/** Bluetooth doesn't say what a host is; computers usually say so in their name. */
private fun com.blazify.buds.core.model.Host.looksLikeComputer(): Boolean =
    listOf("book", "pad", "pc", "laptop", "desktop", "loq", "legion", "thinkpad", "imac", "mac", "dell", "hp ", "asus", "acer").any { it in name.lowercase() }

/** A cap on how loud the earbuds go, with a word on what is safe. */
@Composable
fun SafeVolumeSection(state: BudsState, change: (Change) -> Unit) {
    val limit = state.volumeLimit ?: return
    Panel {
        SectionTitle("Safe volume")
        SwitchRow(BudsIcons.Ear, "Limit the volume", limit.enabled, { change(Change.SafeVolume(it, limit.decibels)) }, detail = "The earbuds never play louder than this", color = com.blazify.buds.ui.blocks.Hue.Green)
        if (limit.enabled) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                limit.choices.forEach { db -> Choice("$db dB", db == limit.decibels) { change(Change.SafeVolume(true, db)) } }
            }
            Hint("85 dB or less is safe for hours of listening; above that, the louder it is, the shorter the time.", Modifier.padding(top = 8.dp))
        }
    }
}

private fun Toggle.hue() = when (this) {
    Toggle.TouchTone -> com.blazify.buds.ui.blocks.Hue.Purple
    Toggle.WearingTone -> com.blazify.buds.ui.blocks.Hue.Pink
    Toggle.LowBatteryPrompt -> com.blazify.buds.ui.blocks.Hue.Amber
    Toggle.SoundLeakCompensation -> com.blazify.buds.ui.blocks.Hue.Indigo
    Toggle.WearDetection -> com.blazify.buds.ui.blocks.Hue.Teal
    Toggle.TouchLock -> com.blazify.buds.ui.blocks.Hue.Slate
    Toggle.GamingMode -> com.blazify.buds.ui.blocks.Hue.Red
    Toggle.Surround -> com.blazify.buds.ui.blocks.Hue.Blue
    Toggle.Ldac -> com.blazify.buds.ui.blocks.Hue.Orange
}

/** The EarPrint at the top of the Sound tab: an invitation to make one, or the one there is with a switch. */
@Composable
private fun EarPrintCard(print: com.blazify.buds.core.model.EarPrintState, ready: Boolean, open: () -> Unit, turn: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    val made = print.madeAt > 0
    Row(
        Modifier.fillMaxWidth().clip(com.blazify.buds.ui.blocks.CardShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.cancelling.copy(alpha = 0.22f), palette.accent.copy(alpha = 0.18f))))
            .press(enabled = ready, onClick = open).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.blazify.buds.ui.blocks.FingerprintEar(if (made) 1f else 0.35f, size = 76.dp, done = made && print.on)
        Column(Modifier.weight(1f)) {
            Text("EarPrint", style = MaterialTheme.typography.titleLarge, color = palette.text)
            Text(
                when {
                    !made -> "A sound made for your ears. About four minutes."
                    print.on -> "On: your sound, shaped to your ears"
                    else -> "Off. Turn it on, or test again"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
            )
        }
        if (made) {
            androidx.compose.material3.Switch(
                print.on,
                onCheckedChange = { turn(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent),
            )
        } else {
            Icon(BudsIcons.Forward, null, tint = palette.text, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Real 3D, to hear the difference: a sound made in the app that travels round the head, drawn as a dot circling a
 * head seen from above. Below it, the earbuds' own surround switch, which only widens the sound a little.
 */
@Composable
private fun ThreeDCard(state: BudsState, change: (Change) -> Unit) {
    val palette = LocalPalette.current
    val sound = com.blazify.buds.ui.LocalSoundOut.current
    var playing by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf<ShortArray?>(null) }
    var seconds by remember { mutableStateOf(0.0) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val made = samples ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.blazify.buds.core.earprint.TestSounds.circling()
        }.also { samples = it }
        sound.play(made, loop = true)
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { seconds = (it - start) / 1e9 }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (playing) sound.stop() } }
    Panel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HeadFromAbove(if (playing) com.blazify.buds.core.earprint.TestSounds.angleAt(seconds).toFloat() else null, Modifier.size(92.dp))
            Column(Modifier.weight(1f)) {
                Text("3D sound", style = MaterialTheme.typography.titleLarge, color = palette.text)
                Text(
                    if (playing) "Close your eyes: the sound goes round your head, to the right first." else "Hear a sound travel round your head, made by this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
                Spacer(Modifier.height(8.dp))
                Choice(if (playing) "Stop" else "Play", playing, icon = if (playing) null else BudsIcons.Play) {
                    if (playing) sound.stop()
                    playing = !playing
                    seconds = 0.0
                }
            }
        }
        if (Toggle.Surround in state.toggles) {
            SwitchRow(
                Toggle.Surround.icon(), "Earbuds' surround", state.toggles[Toggle.Surround] == true,
                { change(Change.Switch(Toggle.Surround, it)) },
                detail = "Built into the earbuds: it only widens the sound a little, it doesn't move it round you",
                color = Toggle.Surround.hue(),
            )
        }
    }
}

/** A head seen from above, nose up, with the sound as a glowing dot going round it at [angle] radians. */
@Composable
private fun HeadFromAbove(angle: Float?, modifier: Modifier) {
    val palette = LocalPalette.current
    androidx.compose.foundation.Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2
        val head = r * 0.36f
        // The path the sound takes.
        drawCircle(palette.line, r * 0.82f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        // Head, nose and ears.
        drawCircle(palette.raised, head, c)
        drawCircle(palette.muted, head, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        val nose = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - head * 0.22f, c.y - head * 0.92f); lineTo(c.x, c.y - head * 1.3f); lineTo(c.x + head * 0.22f, c.y - head * 0.92f)
        }
        drawPath(nose, palette.muted, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
        for (sideX in listOf(-1f, 1f)) {
            drawRoundRect(
                palette.accent,
                topLeft = androidx.compose.ui.geometry.Offset(c.x + sideX * head - (if (sideX < 0) head * 0.22f else 0f), c.y - head * 0.3f),
                size = androidx.compose.ui.geometry.Size(head * 0.22f, head * 0.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(head * 0.11f),
            )
        }
        if (angle != null) {
            // Clockwise from straight ahead, seen from above.
            val p = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.sin(angle) * r * 0.82f, c.y - kotlin.math.cos(angle) * r * 0.82f)
            drawCircle(palette.accent.copy(alpha = 0.25f), r * 0.2f, p)
            drawCircle(palette.accent, r * 0.1f, p)
        }
    }
}
