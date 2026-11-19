package com.blazify.buds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import com.blazify.buds.ui.blocks.BluetoothMockup
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.ui.Pair2
import com.blazify.buds.ui.blocks.AddArt
import com.blazify.buds.ui.blocks.CardShape
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.DeviceArt
import com.blazify.buds.ui.blocks.CaseHero
import com.blazify.buds.ui.blocks.HeadphonesHero
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.painterResource
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.LogoLoader
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/**
 * Where the app opens: every pair that has connected to this device, connected ones first, each with its picture,
 * battery and what the app can do for it. Swipe one right to take it off the list; it comes back when it connects.
 */
@Composable
fun MyDevicesScreen(
    earbuds: List<Pair2>,
    chosen: String?,
    loading: Boolean,
    reload: suspend () -> Unit,
    pick: (Pair2) -> Unit,
    remove: (Pair2) -> Unit,
    add: () -> Unit,
    help: () -> Unit,
    back: (() -> Unit)?,
    bluetoothOn: Boolean = true,
    openSettings: (() -> Unit)? = null,
) {
    // Look again every few seconds, so a pair that connects shows up by itself.
    LaunchedEffect(Unit) {
        while (true) {
            reload()
            kotlinx.coroutines.delay(3_000)
        }
    }
    val palette = LocalPalette.current
    PlaceFrame("My devices", back, actions = listOf(BudsIcons.Support to ("Help" to help))) {
        if (!bluetoothOn) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(BudsIcons.Bluetooth, null, tint = palette.warn, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth is off", style = MaterialTheme.typography.titleMedium, color = palette.text)
                        Hint("Turn it on and your earbuds connect by themselves.")
                    }
                    if (openSettings != null) Choice("Turn on", true, onClick = openSettings)
                }
            }
        }
        if (loading && earbuds.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LogoLoader(size = 48.dp) }
        } else if (earbuds.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddArt(size = 140.dp)
                Text("No devices yet", style = MaterialTheme.typography.titleLarge, color = palette.text)
                Hint("Earbuds that connect to this device appear here by themselves.", Modifier.padding(horizontal = 24.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                earbuds.forEach { row ->
                    androidx.compose.runtime.key(row.device.address) {
                        SwipeAway(onRemove = { remove(row) }) {
                            DeviceRow(row, row.device.address == chosen) { pick(row) }
                        }
                    }
                }
                if (earbuds.size > 0) Hint("Swipe a device right to remove it. It comes back when it connects again.", Modifier.padding(horizontal = 4.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(28.dp)).background(palette.accent)
                .press(onClick = add).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(BudsIcons.Plus, null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
            Text("Add device", style = MaterialTheme.typography.titleMedium, color = palette.onAccent, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A row that can be swiped to the right to remove it, showing a bin as it goes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeAway(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(CardShape).background(palette.low.copy(alpha = 0.9f)).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BudsIcons.Trash, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text("Remove", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 10.dp))
            }
        },
    ) { content() }
}

@Composable
private fun DeviceRow(row: Pair2, chosen: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(palette.card)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) palette.accent else palette.line, CardShape)
            .press(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val kind = Catalog.kind(row.device.name)
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            when (kind) {
                com.blazify.buds.core.catalog.Kind.Headphones -> HeadphonesHero(row.device.connected, null, height = 84.dp, still = true)
                com.blazify.buds.core.catalog.Kind.Speaker -> DeviceArt(kind, size = 72.dp, active = row.device.connected)
                else -> CaseHero(row.device.connected, null, leftOut = true, rightOut = true, height = 84.dp, still = true)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.device.name, style = MaterialTheme.typography.titleMedium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (row.device.connected) palette.good else palette.line))
                Text(
                    if (row.device.connected) "Connected" + (row.battery?.let { " · ${it.percent}%" } ?: "") else "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )
            }
            Text(
                if (row.support == Support.Full) "Full control" else "Basics",
                style = MaterialTheme.typography.labelSmall,
                color = if (row.support == Support.Full) palette.accent else palette.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (row.support == Support.Full) palette.accent.copy(alpha = 0.12f) else palette.raised)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Icon(BudsIcons.Forward, null, tint = palette.muted, modifier = Modifier.size(20.dp))
    }
}

/**
 * For a pair that isn't connected: how to connect it, with a drawing of this device's own Bluetooth settings showing
 * exactly where to tap. Moves on to Home by itself once it connects.
 */
@Composable
fun ConnectGuideScreen(name: String, look: com.blazify.buds.ui.SystemLook, openSettings: (() -> Unit)?, lookAgain: () -> Unit, back: () -> Unit) {
    val palette = LocalPalette.current
    val steps = when (look) {
        com.blazify.buds.ui.SystemLook.Android -> listOf(
            "Take the earbuds out of the case.",
            "Open Bluetooth settings: Connected devices.",
            "Under Previously connected devices, tap \"$name\".",
        )
        com.blazify.buds.ui.SystemLook.Ios -> listOf(
            "Take the earbuds out of the case.",
            "Open Settings, then Bluetooth.",
            "Under My Devices, tap \"$name\".",
        )
        com.blazify.buds.ui.SystemLook.Windows -> listOf(
            "Take the earbuds out of the case.",
            "Open Settings, then Bluetooth & devices.",
            "Find \"$name\" and choose Connect.",
        )
        else -> listOf(
            "Take the earbuds out of the case.",
            "Open Settings, then Bluetooth.",
            "Find \"$name\" and choose Connect.",
        )
    }
    PlaceFrame("Connect $name", back) {
        BluetoothMockup(look, name, pairing = false)
        Panel {
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                    }
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                }
            }
        }
        if (openSettings != null) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(28.dp)).background(palette.accent)
                    .press(onClick = openSettings).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(BudsIcons.Bluetooth, null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
                Text("Open Bluetooth settings", style = MaterialTheme.typography.titleMedium, color = palette.onAccent, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LogoLoader(size = 22.dp)
            Text("Waiting for them to connect…", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Adding earbuds: a clear picture, the steps for the chosen kind, and the way to the system's pairing screen. */
@Composable
fun AddDeviceScreen(openSettings: (() -> Unit)?, lookAgain: () -> Unit, back: () -> Unit, look: com.blazify.buds.ui.SystemLook = com.blazify.buds.ui.SystemLook.Android) {
    val palette = LocalPalette.current
    var which by remember { mutableIntStateOf(0) }
    PlaceFrame("Add device", back) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AddArt(size = 150.dp)
            Text("Pair your earbuds", style = MaterialTheme.typography.titleLarge, color = palette.text)
            Hint("Put them in pairing mode, pair them in Bluetooth settings, then come back.", Modifier.padding(horizontal = 24.dp))
        }
        BluetoothMockup(look, "Your earbuds", pairing = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalog.pairingHelp.forEachIndexed { i, (name, _) -> Choice(name, which == i) { which = i } }
        }
        Panel {
            Catalog.pairingHelp[which].second.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                    }
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                }
            }
        }
        if (openSettings != null) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(28.dp)).background(palette.accent)
                    .press(onClick = openSettings).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(BudsIcons.Bluetooth, null, tint = palette.onAccent, modifier = Modifier.size(22.dp))
                Text("Open Bluetooth settings", style = MaterialTheme.typography.titleMedium, color = palette.onAccent, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Choice("I've paired them", false, icon = BudsIcons.Refresh, onClick = lookAgain)
        }
    }
}

private val QUESTIONS = listOf(
    "My earbuds won't connect" to "Take them out of the case so they wake up, and check they are connected in your Bluetooth settings. Then tap Try again on Home.",
    "It says another app is using them" to "Only one app on a phone can control earbuds at a time. Close the brand's own app (swipe it away from recent apps), then tap Try again.",
    "A change from another app shows late" to "The app asks your earbuds for their settings every few seconds while it is open, so a change made elsewhere shows within about three seconds.",
    "Why is battery in steps of 20%?" to "These earbuds report their battery in fifths, so the app shows exactly what they say rather than a guess.",
    "Sound changes don't work" to "The earbuds accept sound changes only while both of them are out of the case.",
    "Best quality turned two devices off" to "LDAC, the high-quality mode, works with one device at a time. Switching it on turns two-device mode off in the earbuds themselves.",
)

/** Help: answers to what goes wrong most, and the details to send when it is something else. */
@Composable
fun SupportScreen(state: BudsState?, version: String, back: () -> Unit) {
    val palette = LocalPalette.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableIntStateOf(-1) }
    var copied by remember { mutableStateOf(false) }
    PlaceFrame("Help", back) {
        Panel(padding = 8.dp) {
            QUESTIONS.forEachIndexed { i, (question, answer) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press { open = if (open == i) -1 else i }.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(question, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                        Icon(if (open == i) BudsIcons.Close else BudsIcons.Plus, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                    }
                    AnimatedVisibility(open == i) {
                        Text(answer, style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        Panel {
            SectionTitle("Something else?")
            Hint("Copy these details and send them with what happened. They hold no names and nothing personal.")
            Spacer(Modifier.height(10.dp))
            Choice(if (copied) "Copied" else "Copy details", copied, icon = if (copied) BudsIcons.Check else BudsIcons.Copy) {
                val details = buildString {
                    appendLine("Blazbuds $version")
                    appendLine("Earbuds: ${state?.model ?: "none"} · firmware ${state?.firmware ?: "?"}")
                    appendLine("Link: ${state?.link ?: "none"} · both out: ${state?.bothConnected}")
                    appendLine("Mode: ${state?.sound?.mode} · two devices: ${state?.multipoint?.enabled}")
                }
                clipboard.setText(AnnotatedString(details))
                copied = true
            }
        }
    }
}
