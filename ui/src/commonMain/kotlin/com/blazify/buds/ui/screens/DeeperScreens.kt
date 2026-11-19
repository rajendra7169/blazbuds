package com.blazify.buds.ui.screens

import androidx.compose.foundation.horizontalScroll
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
fun EqualizerScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit) = PlaceFrame("Sound", back) {
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
    Panel {
        SectionTitle("Presets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            eq.presets.forEach { preset -> Choice(preset.name, preset.id == eq.presetId) { change(Change.Preset(preset.id)) } }
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

@Composable
fun ControlsScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit) = PlaceFrame("Touch controls", back) {
    val controls = state.controls
    if (controls == null) {
        Hint("These earbuds' controls can't be changed from the app.")
        return@PlaceFrame
    }
    val palette = LocalPalette.current
    var ear by remember { mutableStateOf(Ear.Left) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Left earbud", ear == Ear.Left) { ear = Ear.Left }
        Choice("Right earbud", ear == Ear.Right) { ear = Ear.Right }
    }
    Press.entries.forEach { press ->
        val now = controls.actions[ear to press] ?: return@forEach
        Panel {
            SectionTitle(press.label())
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                controls.available.forEach { action ->
                    Choice(action.label(), action == now) { change(Change.Touch(ear, press, action)) }
                }
            }
        }
    }
    state.sound?.let { sound ->
        Panel {
            SectionTitle("Change listening mode cycles through")
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
    Panel(padding = 8.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Put every control back as it came", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.weight(1f))
            Choice("Reset", false) { change(Change.ResetControls) }
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
fun SoundsScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit) = PlaceFrame("Sound and prompts", back) {
    Panel {
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
fun DevicesScreen(state: BudsState, change: (Change) -> Unit, back: () -> Unit) = PlaceFrame("Devices", back) {
    val palette = LocalPalette.current
    val points = state.multipoint
    if (points == null) {
        Hint("These earbuds connect to one device at a time.")
        return@PlaceFrame
    }
    Panel {
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
                    Icon(BudsIcons.Phone, null, tint = palette.muted, modifier = Modifier.size(20.dp))
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
fun AboutScreen(state: BudsState, back: () -> Unit) = PlaceFrame("About these earbuds", back) {
    val palette = LocalPalette.current
    Panel {
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
