package com.blazify.buds.ui.screens

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

@Composable
fun EqualizerScreen(state: BudsState, change: (Change) -> Unit, back: (() -> Unit)? = null) = PlaceFrame("Sound", back) {
    val eq = state.equalizer
    if (eq == null) {
        Hint("These earbuds have no equalizer the app can reach.")
        return@PlaceFrame
    }
    val palette = LocalPalette.current
    // The bands move under the finger and are sent once the finger lifts, so the earbuds aren't flooded.
    var draft by remember { mutableStateOf(eq.left) }
    LaunchedEffect(eq.left) { draft = eq.left }
    Panel {
        EqCurve(draft)
        Spacer(Modifier.height(12.dp))
        Text(eq.preset?.name ?: "Custom", style = MaterialTheme.typography.titleMedium, color = palette.text)
    }
    SectionTitle("Presets")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            eq.presets.chunked(columns).forEachIndexed { row, group ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.forEachIndexed { i, preset ->
                        com.blazify.buds.ui.blocks.PresetTile(row * columns + i, preset.name, preset.gains, preset.id == eq.presetId, Modifier.weight(1f)) {
                            change(Change.Preset(preset.id))
                        }
                    }
                    repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Panel {
        SectionTitle("Bands", action = "Flat") { change(Change.Bands(List(eq.left.size) { 0.0 })) }
        eq.frequencies.forEachIndexed { i, hz ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hz.hertz(), style = MaterialTheme.typography.labelMedium, color = palette.muted, modifier = Modifier.width(56.dp))
                BandSlider(
                    value = draft.getOrElse(i) { 0.0 },
                    min = Equalizer.MIN_DB,
                    max = Equalizer.MAX_DB,
                    label = "${hz.hertz()} band",
                    modifier = Modifier.weight(1f),
                    onChange = { v -> draft = draft.toMutableList().also { it[i] = v } },
                    onDone = { change(Change.Bands(draft)) },
                )
                val db = draft.getOrElse(i) { 0.0 }
                Text(
                    (if (db > 0) "+" else if (db < 0) "−" else "") + "${(abs(db) * 10).roundToInt() / 10.0}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = palette.text,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp),
                )
            }
        }
        if (!state.bothConnected) Hint("Take both earbuds out of the case to change the sound.")
    }
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
            SwitchRow(toggle.icon(), toggle.label(), state.toggles[toggle] == true, { change(Change.Switch(toggle, it)) }, detail = detail)
        }
    }
    state.autoPowerOff?.let { off ->
        Panel {
            SwitchRow(BudsIcons.Power, "Turn off when not in use", off.enabled, { change(Change.PowerOff(it, off.minutes)) })
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
