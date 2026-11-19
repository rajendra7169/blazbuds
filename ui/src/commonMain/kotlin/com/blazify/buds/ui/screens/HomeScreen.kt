package com.blazify.buds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Scene
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.ui.blocks.BatteryRing
import com.blazify.buds.ui.blocks.BatteryRow
import com.blazify.buds.ui.blocks.BudsHero
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.EqCurve
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.LinkRow
import com.blazify.buds.ui.blocks.LogoLoader
import com.blazify.buds.ui.blocks.LogoMark
import com.blazify.buds.ui.blocks.ModeDetails
import com.blazify.buds.ui.blocks.ModeSwitch
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.ToggleTile
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/** Where Home sends the person for what doesn't fit on it. */
enum class Place { Equalizer, Controls, Sounds, About, Settings, Devices, Earbuds }

/**
 * Everything most people need, on one screen: the earbuds and their battery, the listening mode with its options,
 * scenes, the sound, the everyday switches and the two-device choice. The rest is one tap away. On a phone it is one
 * column; on a tablet or a computer window it splits in two.
 */
@Composable
fun HomeScreen(state: BudsState, change: (Change) -> Unit, open: (Place) -> Unit, retry: () -> Unit) {
    val palette = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = if (wide) 1100.dp else 560.dp).fillMaxWidth()) {
                TopBar(state, open)
                LinkBanner(state, retry)
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Earbuds(state, heroHeight = 240.dp)
                            Listening(state, change)
                            Scenes(state, change)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SoundPanel(state, change, open)
                            Switches(state, change)
                            TwoDevices(state, change, open)
                            More(state, open)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Earbuds(state, heroHeight = 190.dp)
                        Listening(state, change)
                        Scenes(state, change)
                        SoundPanel(state, change, open)
                        Switches(state, change)
                        TwoDevices(state, change, open)
                        More(state, open)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TopBar(state: BudsState, open: (Place) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).press { open(Place.Earbuds) }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LogoMark(size = 28.dp)
            Column {
                Text(state.name, style = MaterialTheme.typography.titleLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when (state.link) {
                    Link.Connected -> "Connected"
                    Link.Connecting -> "Connecting…"
                    Link.Busy -> "In use by another app"
                    Link.Lost -> "Not connected"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            when (state.link) {
                                Link.Connected -> palette.good
                                Link.Connecting -> palette.warn
                                else -> palette.low
                            },
                        ),
                    )
                    Text(status, style = MaterialTheme.typography.labelSmall, color = palette.muted)
                }
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).press { open(Place.Settings) }, contentAlignment = Alignment.Center) {
            Icon(BudsIcons.Settings, "Settings", tint = palette.text, modifier = Modifier.size(24.dp))
        }
    }
}

/** Says plainly what is wrong with the link and what to do, with one button to try again. */
@Composable
private fun LinkBanner(state: BudsState, retry: () -> Unit) {
    val palette = LocalPalette.current
    AnimatedVisibility(state.link != Link.Connected, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Panel(Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.link == Link.Connecting) LogoLoader(size = 28.dp) else Icon(BudsIcons.Info, null, tint = palette.warn)
                Column(Modifier.weight(1f)) {
                    Text(
                        when (state.link) {
                            Link.Connecting -> "Connecting to your earbuds"
                            Link.Busy -> "Another app is using your earbuds"
                            else -> "Your earbuds aren't connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text,
                    )
                    Text(
                        when (state.link) {
                            Link.Connecting -> "This takes a second or two."
                            Link.Busy -> "Only one app can control them at a time. Close the brand's own app on your phone, then try again."
                            else -> "Take them out of the case and check Bluetooth is on."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                    )
                }
                if (state.link != Link.Connecting) Choice("Try again", selected = true, onClick = retry)
            }
        }
    }
}

@Composable
private fun Earbuds(state: BudsState, heroHeight: androidx.compose.ui.unit.Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BudsHero(state.sound?.mode, leftIn = state.left != null, rightIn = state.right != null, height = heroHeight)
        if (state.left == null && state.right == null && state.overall != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BatteryRing("Earbuds", state.overall) }
        } else {
            BatteryRow(state.left, state.case, state.right)
        }
        if (state.sound == null && state.isReady) {
            Hint("These earbuds get the basics: connection and battery. Full control comes as their brand is added.", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Listening(state: BudsState, change: (Change) -> Unit) {
    val sound = state.sound ?: return
    Panel {
        SectionTitle("Listening")
        Spacer(Modifier.height(8.dp))
        ModeSwitch(sound.mode) { change(Change.Mode(it)) }
        Spacer(Modifier.height(12.dp))
        ModeDetails(
            sound,
            onCancel = { style, level, travel -> change(Change.Cancelling(style, level, travel)) },
            onTransparency = { change(Change.TransparencyKind(it)) },
            onWind = { change(Change.Wind(it)) },
        )
    }
}

@Composable
private fun Scenes(state: BudsState, change: (Change) -> Unit) {
    if (state.sound == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Scenes")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scene.DEFAULTS.forEach { scene ->
                Choice(scene.name, scene.matches(state), icon = scene.icon()) { scene.changes().forEach(change) }
            }
        }
    }
}

private fun Scene.icon() = when (id) {
    "focus" -> BudsIcons.Focus
    "commute" -> BudsIcons.Bus
    "flight" -> BudsIcons.Plane
    "talk" -> BudsIcons.Voice
    else -> BudsIcons.Moon
}

@Composable
private fun SoundPanel(state: BudsState, change: (Change) -> Unit, open: (Place) -> Unit) {
    val eq = state.equalizer ?: return
    Panel {
        SectionTitle("Sound", action = "Edit") { open(Place.Equalizer) }
        Spacer(Modifier.height(4.dp))
        EqCurve(eq.left)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Your own sound first when it is the one playing.
            if (eq.preset == null) Choice("Custom", selected = true) { open(Place.Equalizer) }
            eq.presets.forEach { preset -> Choice(preset.name, preset.id == eq.presetId) { change(Change.Preset(preset.id)) } }
        }
        if (!state.bothConnected) {
            Spacer(Modifier.height(8.dp))
            Hint("Sound changes need both earbuds out of the case.")
        }
    }
}

private val QUICK = listOf(Toggle.GamingMode, Toggle.Surround, Toggle.WearDetection, Toggle.TouchLock)

@Composable
private fun Switches(state: BudsState, change: (Change) -> Unit) {
    val shown = QUICK.filter { it in state.toggles }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quick switches")
        shown.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { toggle ->
                    val on = state.toggles[toggle] == true
                    ToggleTile(toggle.icon(), toggle.label(), on, Modifier.weight(1f), detail = toggle.detail(on)) {
                        change(Change.Switch(toggle, !on))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

fun Toggle.label() = when (this) {
    Toggle.Ldac -> "Best quality"
    Toggle.GamingMode -> "Gaming mode"
    Toggle.Surround -> "3D surround"
    Toggle.WearDetection -> "Wear detection"
    Toggle.TouchTone -> "Touch tone"
    Toggle.WearingTone -> "Wearing tone"
    Toggle.TouchLock -> "Touch lock"
    Toggle.LowBatteryPrompt -> "Low battery alert"
    Toggle.SoundLeakCompensation -> "Leak compensation"
}

fun Toggle.icon() = when (this) {
    Toggle.Ldac -> BudsIcons.Hd
    Toggle.GamingMode -> BudsIcons.Game
    Toggle.Surround -> BudsIcons.Surround
    Toggle.WearDetection -> BudsIcons.Ear
    Toggle.TouchLock -> BudsIcons.Lock
    Toggle.LowBatteryPrompt -> BudsIcons.Battery
    else -> BudsIcons.Touch
}

private fun Toggle.detail(on: Boolean): String = when (this) {
    Toggle.GamingMode -> if (on) "Low delay" else "Off"
    Toggle.WearDetection -> if (on) "Pauses when out" else "Off"
    Toggle.TouchLock -> if (on) "Taps ignored" else "Off"
    else -> if (on) "On" else "Off"
}

/** The model's real trade-off made plain: the best sound, or two devices at once. */
@Composable
private fun TwoDevices(state: BudsState, change: (Change) -> Unit, open: (Place) -> Unit) {
    val points = state.multipoint
    val ldac = state.toggles[Toggle.Ldac]
    if (points == null && ldac == null) return
    val palette = LocalPalette.current
    Panel {
        SectionTitle("Connection", action = if (points != null) "Devices" else null) { open(Place.Devices) }
        Spacer(Modifier.height(8.dp))
        if (ldac != null && points != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Two devices", points.enabled && !ldac, Modifier.weight(1f), icon = BudsIcons.Laptop) { change(Change.TwoDevices(true)) }
                Choice("Best quality", ldac, Modifier.weight(1f), icon = BudsIcons.Hd) { change(Change.Switch(Toggle.Ldac, true)) }
            }
            Spacer(Modifier.height(8.dp))
            Hint(
                if (ldac) "LDAC sound for one Android device. Uses more battery." else "Connected to two devices at once; music moves to whichever plays.",
            )
        }
        if (points != null && points.enabled && points.devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            points.devices.forEach { host ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(if (host.name.contains("book", true) || host.name.contains("pad", true) || host.name.contains("PC", true)) BudsIcons.Laptop else BudsIcons.Phone, null, tint = palette.muted, modifier = Modifier.size(20.dp))
                    Text(host.name, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            host.playing -> "Playing"
                            host.connected -> "Connected"
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (host.connected) palette.accent else palette.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun More(state: BudsState, open: (Place) -> Unit) {
    Panel(padding = 8.dp) {
        if (state.controls != null) {
            val doubles = state.controls!!.actions.filterKeys { it.second == Press.Double }.size
            LinkRow(BudsIcons.Touch, "Touch controls", "Taps, double taps and holds on each earbud".takeIf { doubles >= 0 }) { open(Place.Controls) }
        }
        LinkRow(BudsIcons.Equalizer, "Sound and prompts", "Tones, alerts, auto power off") { open(Place.Sounds) }
        LinkRow(BudsIcons.Info, "About these earbuds", listOfNotNull(state.model, state.firmware?.let { "firmware $it" }).joinToString(" · ")) { open(Place.About) }
    }
}

