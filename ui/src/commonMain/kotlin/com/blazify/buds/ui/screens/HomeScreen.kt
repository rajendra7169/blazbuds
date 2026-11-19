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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Kind
import com.blazify.buds.ui.blocks.CaseHero
import com.blazify.buds.ui.blocks.HeadphonesHero
import com.blazify.buds.ui.blocks.DeviceArt
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
import com.blazify.buds.core.model.Scene
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.ui.blocks.BatteryRing
import com.blazify.buds.ui.blocks.BatteryRow
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.EqCurve
import com.blazify.buds.ui.blocks.Hint
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

/** The tabs along the bottom. */
enum class Tab { Home, Sound, Controls, Device }

/**
 * The everyday controls on one screen: the earbuds' case and battery, the listening mode with its options, scenes,
 * the sound, quick switches and the two-device choice. The rest lives in the other tabs. On a phone it is one column;
 * on a tablet or a computer window it splits in two.
 */
@Composable
fun HomeScreen(state: BudsState, change: (Change) -> Unit, open: (Place) -> Unit, goTab: (Tab) -> Unit, retry: () -> Unit, bluetoothOn: Boolean = true, turnOnBluetooth: (() -> Unit)? = null) {
    val palette = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = if (wide) 1100.dp else 560.dp).fillMaxWidth()) {
                TopBar(state, open)
                LinkBanner(state, retry, bluetoothOn, turnOnBluetooth)
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Earbuds(state, heroHeight = 280.dp)
                            Listening(state, change)
                            Scenes(state, change)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SoundPanel(state, change, goTab)
                            Switches(state, change)
                            TwoDevices(state, change, goTab)
                            Care(state, open)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Earbuds(state, heroHeight = 230.dp)
                        Listening(state, change)
                        Scenes(state, change)
                        SoundPanel(state, change, goTab)
                        Switches(state, change)
                        TwoDevices(state, change, goTab)
                        Care(state, open)
                    }
                }
                Spacer(Modifier.height(16.dp))
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
        IconButton(BudsIcons.Support, "Help") { open(Place.Support) }
        IconButton(BudsIcons.Settings, "Settings") { open(Place.Settings) }
    }
}

/** Says plainly what is wrong with the link and what to do, with one button to try again. */
@Composable
private fun LinkBanner(state: BudsState, retry: () -> Unit, bluetoothOn: Boolean, turnOnBluetooth: (() -> Unit)?) {
    val palette = LocalPalette.current
    AnimatedVisibility(state.link != Link.Connected || !bluetoothOn, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Panel(Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.link == Link.Connecting && bluetoothOn) LogoLoader(size = 28.dp) else Icon(if (bluetoothOn) BudsIcons.Info else BudsIcons.Bluetooth, null, tint = palette.warn)
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !bluetoothOn -> "Bluetooth is off"
                            else -> when (state.link) {
                            Link.Connecting -> "Connecting to your earbuds"
                            Link.Busy -> "Another app is using your earbuds"
                            else -> "Your earbuds aren't connected"
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text,
                    )
                    Text(
                        when {
                            !bluetoothOn -> "Turn it on and your earbuds connect by themselves."
                            else -> when (state.link) {
                            Link.Connecting -> "This takes a second or two."
                            Link.Busy -> "Only one app can control earbuds at a time, and another app (often the brand's own, like Soundcore) has them. Close it from your recent apps; Blazbuds takes over by itself."
                            else -> "Take them out of the case and check Bluetooth is on."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                    )
                }
                if (!bluetoothOn && turnOnBluetooth != null) {
                    Choice("Turn on", selected = true, onClick = turnOnBluetooth)
                } else if (state.link != Link.Connecting) {
                    Choice("Try again", selected = true, onClick = retry)
                }
            }
        }
    }
}

@Composable
private fun Earbuds(state: BudsState, heroHeight: androidx.compose.ui.unit.Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (Catalog.kind(state.name)) {
            Kind.Headphones -> HeadphonesHero(connected = state.isReady, mode = state.sound?.mode, height = heroHeight)
            Kind.Speaker -> Box(Modifier.fillMaxWidth().height(heroHeight), contentAlignment = Alignment.Center) {
                DeviceArt(Kind.Speaker, size = heroHeight * 0.8f, active = state.isReady)
            }
            else -> CaseHero(
                connected = state.isReady,
                mode = state.sound?.mode,
                // A bud that is charging is in its case.
                leftOut = (state.left != null && state.left?.charging != true) || state.overall != null,
                rightOut = (state.right != null && state.right?.charging != true) || state.overall != null,
                height = heroHeight,
                soft = true,
            )
        }
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
            onEnvironment = { change(Change.Environment(it)) },
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
private fun SoundPanel(state: BudsState, change: (Change) -> Unit, goTab: (Tab) -> Unit) {
    val eq = state.equalizer ?: return
    Panel {
        SectionTitle("Sound", action = "Edit") { goTab(Tab.Sound) }
        Spacer(Modifier.height(4.dp))
        EqCurve(eq.left)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Your own sound first when it is the one playing.
            if (eq.preset == null) Choice("Custom", selected = true) { goTab(Tab.Sound) }
            eq.presets.forEach { preset -> Choice(preset.name, preset.id == eq.presetId) { change(Change.Preset(preset.id)) } }
        }
        if (!state.bothConnected) {
            Spacer(Modifier.height(8.dp))
            Hint("Sound changes need both earbuds out of the case.")
        }
    }
}

private val QUICK = listOf(Toggle.GamingMode, Toggle.Surround, Toggle.WearDetection)

/** One tile in the quick switches. */
private class Quick(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val on: Boolean, val detail: String, val flip: () -> Unit)

@Composable
private fun Switches(state: BudsState, change: (Change) -> Unit) {
    val tiles = QUICK.filter { it in state.toggles }.map { toggle ->
        val on = state.toggles[toggle] == true
        Quick(toggle.icon(), toggle.label(), on, toggle.detail(on)) { change(Change.Switch(toggle, !on)) }
    } + listOfNotNull(
        state.volumeLimit?.let { limit ->
            Quick(BudsIcons.Ear, "Safe volume", limit.enabled, if (limit.enabled) "Up to ${limit.decibels} dB" else "Off") {
                change(Change.SafeVolume(!limit.enabled, limit.decibels))
            }
        },
    )
    if (tiles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quick switches")
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tile -> ToggleTile(tile.icon, tile.title, tile.on, Modifier.weight(1f), detail = tile.detail, onToggle = tile.flip) }
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
private fun TwoDevices(state: BudsState, change: (Change) -> Unit, goTab: (Tab) -> Unit) {
    val points = state.multipoint ?: return
    val ldac = state.toggles[Toggle.Ldac] ?: return
    Panel {
        SectionTitle("Connection", action = "Devices") { goTab(Tab.Device) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Two devices", points.enabled && !ldac, Modifier.weight(1f), icon = BudsIcons.Laptop) { change(Change.TwoDevices(true)) }
            Choice("Best quality", ldac, Modifier.weight(1f), icon = BudsIcons.Hd) { change(Change.Switch(Toggle.Ldac, true)) }
        }
        Spacer(Modifier.height(8.dp))
        val connected = points.devices.count { it.connected }
        Hint(
            if (ldac) {
                "LDAC sound for one Android device. Uses more battery."
            } else {
                "Connected to two devices at once; music moves to whichever plays." + if (connected > 0) " $connected connected now." else ""
            },
        )
    }
}

/** The everyday helpers, one tap away: find them, and check the fit. */
@Composable
private fun Care(state: BudsState, open: (Place) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.canRing) Choice("Find", false, icon = BudsIcons.Find) { open(Place.Find) }
        if (state.canFitTest) Choice("Fit test", false, icon = BudsIcons.Ear) { open(Place.Fit) }
    }
}
