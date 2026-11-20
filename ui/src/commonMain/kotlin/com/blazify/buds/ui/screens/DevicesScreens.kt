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
                else -> CaseHero(row.device.connected, null, leftOut = true, rightOut = true, height = 84.dp, still = true, soft = true)
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
                when (row.support) { Support.Full -> "Full control"; Support.Battery -> "Battery and details"; Support.Basic -> "Basics" },
                style = MaterialTheme.typography.labelSmall,
                color = if (row.support != Support.Basic) palette.accent else palette.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (row.support != Support.Basic) palette.accent.copy(alpha = 0.12f) else palette.raised)
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
        WorksWith()
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

/** A brand the app fully controls: some of its models, and what the app can do with them. */
private data class Brand(val name: String, val models: String, val can: String)

private val BRANDS = listOf(
    Brand("Soundcore", "Liberty, Life, Space, P, A, R, Sport and Sleep series", "Battery, listening modes, sound and your own curve, touch controls, EarPrint on the Liberty 4 NC"),
    Brand("Samsung Galaxy Buds", "Buds, Buds+, Live, Pro, Buds2, Buds FE, Buds3, Buds4", "Battery, noise cancelling and ambient, presets and, from Buds3, your own curve, touch lock"),
    Brand("Sony", "WH-1000XM, WF-1000XM, LinkBuds, WF-C, WH-CH, ULT WEAR", "Battery, noise cancelling with ambient level, your own curve, speak-to-chat, touch"),
    Brand("Nothing and CMF", "Ear, Ear (a), Ear (1) to (3), Ear (open), CMF Buds", "Battery, listening modes, Advanced equalizer, fit test, touch controls"),
    Brand("OnePlus, OPPO and realme", "Buds, Nord Buds, Enco and realme Buds, 140 models", "Battery, listening modes, your own curve, 3D sound, fit test, touch"),
    Brand("Honor and Huawei", "Honor Choice Earbuds X, FreeBuds", "Battery with the case, sound presets, touch controls, dual-connect"),
    Brand("Ultima and JieLi earbuds", "Ultima Boom and many lower-priced pairs on the JieLi chip", "Battery with the case, sound presets and your own curve, touch, gaming mode"),
)

/** Which earbuds the app knows well, at the top of Add device, so people know before they pair. */
@Composable
private fun WorksWith() {
    val palette = LocalPalette.current
    var open by remember { mutableIntStateOf(-1) }
    Panel(padding = 8.dp) {
        SectionTitle("Works with", Modifier.padding(start = 8.dp, top = 6.dp))
        BRANDS.forEachIndexed { i, brand ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).press { open = if (open == i) -1 else i }.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(BudsIcons.Check, null, tint = palette.good, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(brand.name, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                        Text(brand.models, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = if (open == i) 3 else 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(if (open == i) BudsIcons.Close else BudsIcons.Plus, null, tint = palette.muted, modifier = Modifier.size(16.dp))
                }
                AnimatedVisibility(open == i) {
                    Text(brand.can, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.padding(start = 28.dp, top = 6.dp))
                }
            }
        }
        Hint("Any other Bluetooth earbuds get battery and the basics. Missing yours? Ask in Help.", Modifier.padding(8.dp))
    }
}

