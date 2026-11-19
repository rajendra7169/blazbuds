package com.blazify.buds.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Buds
import com.blazify.buds.ui.blocks.LocalReduceMotion
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.Change
import com.blazify.buds.ui.blocks.LogoMark
import com.blazify.buds.ui.blocks.NavBar
import com.blazify.buds.ui.blocks.NavItem
import com.blazify.buds.ui.blocks.NavRail
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.screens.AddDeviceScreen
import com.blazify.buds.ui.screens.FindScreen
import com.blazify.buds.ui.screens.FitTestScreen
import com.blazify.buds.ui.screens.ConnectGuideScreen
import androidx.compose.runtime.LaunchedEffect
import com.blazify.buds.ui.screens.ControlsScreen
import com.blazify.buds.ui.screens.DeviceTab
import com.blazify.buds.ui.screens.EqualizerScreen
import com.blazify.buds.ui.screens.HomeScreen
import com.blazify.buds.ui.screens.MyDevicesScreen
import com.blazify.buds.ui.screens.Place
import com.blazify.buds.ui.screens.SettingsScreen
import com.blazify.buds.ui.screens.SupportScreen
import com.blazify.buds.ui.screens.Tab
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val VERSION = "0.2.0"

private val TABS = listOf(
    NavItem("Home", BudsIcons.Home),
    NavItem("Sound", BudsIcons.Equalizer),
    NavItem("Controls", BudsIcons.Touch),
    NavItem("Device", BudsIcons.Earbuds),
)

/**
 * The whole app on any platform. With no earbuds chosen yet it opens on My devices. After that: four tabs, along the
 * bottom of a phone or down the side of a wider window, with Home holding the everyday controls; My devices, Add
 * device, Help and Settings slide in over them.
 */
@Composable
fun BudsApp(hub: Hub, prefs: Prefs = remember { Prefs() }, start: Place? = Place.Earbuds, startTab: Tab = Tab.Home) {
    BudsTheme(prefs.theme) {
        CompositionLocalProvider(LocalReduceMotion provides prefs.reduceMotion, LocalPrefs provides prefs, LocalShare provides { text: String -> hub.share(text) }, LocalCanFind provides hub.canFind) {
            val buds by hub.current.collectAsState()
            val earbuds by hub.earbuds.collectAsState()
            val bluetoothOn by hub.bluetoothOn.collectAsState()
            val scope = rememberCoroutineScope()
            var place by remember { mutableStateOf(start) }
            var tab by remember { mutableStateOf(startTab) }
            var guide by remember { mutableStateOf<com.blazify.buds.core.link.Device?>(null) }
            var loading by remember { mutableStateOf(true) }
            val reload: suspend () -> Unit = {
                loading = true
                hub.reload()
                loading = false
            }
            Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                val now = buds
                val state = now?.state?.collectAsState()?.value
                // Back steps back inside the app: a page closes, another tab returns Home, and only Home leaves.
                val shownPlace = if (now == null && place == null) Place.Earbuds else place
                val canGoBack = when (shownPlace) {
                    null -> tab != Tab.Home
                    Place.Earbuds -> now != null
                    else -> true
                }
                SystemBack(canGoBack) {
                    when (shownPlace) {
                        null -> tab = Tab.Home
                        Place.Add, Place.Connect -> place = Place.Earbuds
                        Place.Fit -> place = null
                        else -> place = null
                    }
                }
                AnimatedContent(
                    if (now == null && place == null) Place.Earbuds else place,
                    transitionSpec = {
                        val dir = if (targetState != null) 1 else -1
                        (slideInHorizontally(tween(Motion.SLOW)) { dir * it / 5 } + fadeIn(tween(Motion.NORMAL))) togetherWith
                            (slideOutHorizontally(tween(Motion.SLOW)) { -dir * it / 5 } + fadeOut(tween(Motion.QUICK)))
                    },
                    label = "place",
                ) { shown ->
                    val close = { place = null }
                    when (shown) {
                        null -> if (now != null && state != null) {
                            Tabs(tab, { tab = it }) {
                                AnimatedContent(tab, transitionSpec = { fadeIn(tween(Motion.NORMAL)) togetherWith fadeOut(tween(Motion.QUICK)) }, label = "tab") { t ->
                                    val change: (Change) -> Unit = { scope.launch { now.apply(it) } }
                                    when (t) {
                                        Tab.Home -> HomeScreen(
                                            state, change, open = { place = it }, goTab = { tab = it },
                                            retry = { scope.launch { hub.reload(); now.refresh() } },
                                            bluetoothOn = bluetoothOn,
                                            turnOnBluetooth = settingsAction(hub),
                                        )
                                        Tab.Sound -> EqualizerScreen(state, change)
                                        Tab.Controls -> ControlsScreen(state, change)
                                        Tab.Device -> DeviceTab(state, change) { place = it }
                                    }
                                }
                            }
                        }
                        Place.Earbuds -> MyDevicesScreen(
                            earbuds,
                            hub.chosenAddress,
                            loading,
                            reload,
                            pick = {
                                hub.choose(it.device)
                                if (it.device.connected) {
                                    place = null
                                    tab = Tab.Home
                                } else {
                                    // Not connected: show how to connect it; Home follows once it does.
                                    guide = it.device
                                    place = Place.Connect
                                }
                            },
                            remove = { hub.remove(it.device) },
                            add = { place = Place.Add },
                            help = { place = Place.Support },
                            bluetoothOn = bluetoothOn,
                            openSettings = settingsAction(hub),
                            back = if (now != null && state?.isReady == true) close else null,
                        )
                        Place.Add -> AddDeviceScreen(
                            look = hub.look,
                            openSettings = settingsAction(hub),
                            lookAgain = {
                                scope.launch { reload() }
                                place = Place.Earbuds
                            },
                            back = { place = Place.Earbuds },
                        )
                        Place.Connect -> {
                            val name = guide?.name ?: state?.name ?: "your earbuds"
                            // Straight to Home once they connect.
                            LaunchedEffect(state?.isReady) { if (state?.isReady == true) { place = null; tab = Tab.Home } }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    hub.reload()
                                    kotlinx.coroutines.delay(3_000)
                                }
                            }
                            ConnectGuideScreen(name, hub.look, settingsAction(hub), lookAgain = { scope.launch { hub.reload() } }, back = { place = Place.Earbuds })
                        }
                        Place.Settings -> SettingsScreen(close)
                        Place.Find -> if (state != null) FindScreen(state, { l, r -> hub.findSound(l, r) }, close)
                        Place.Fit -> if (now != null) FitTestScreen(now, close)
                        Place.Support -> SupportScreen(state, VERSION, close)
                    }
                }
            }
        }
    }
}

/** The tabs' frame: the bar along the bottom of a phone, a rail down the side of anything wider. */
@Composable
private fun Tabs(tab: Tab, pick: (Tab) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                NavRail(TABS, tab.ordinal, { pick(Tab.entries[it]) }) { LogoMark(size = 30.dp) }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { content() }
                NavBar(TABS, tab.ordinal) { pick(Tab.entries[it]) }
            }
        }
    }
}

private fun settingsAction(hub: Hub): (() -> Unit)? = if (hub.canOpenSettings) ({ hub.openBluetoothSettings() }) else null

/** The app around one pair of earbuds that is already there, as for the demo and the screenshots. */
@Composable
fun BudsApp(buds: Buds, prefs: Prefs = remember { Prefs() }, start: Place? = null, startTab: Tab = Tab.Home) {
    val scope = rememberCoroutineScope()
    val hub = remember(buds) { Hub(OnePair(buds), scope, null).also { it.choose(OnePair.DEVICE) } }
    BudsApp(hub, prefs, start, startTab)
}

/** One pair already there, beside a few others the device knows, as for the demo and the screenshots. */
private class OnePair(private val buds: Buds) : Platform {
    override suspend fun paired() = listOf(DEVICE) + OTHERS
    override suspend fun peek(device: Device): Pair<Boolean, Int?> = device.connected to if (device == DEVICE) 90 else null
    override fun open(device: Device, scope: CoroutineScope) = buds

    companion object {
        val DEVICE = Device("Liberty 4 NC", "00:00:00:00:00:00", connected = true)
        val OTHERS = listOf(
            Device("Galaxy Buds2 Pro", "00:00:00:00:00:01", connected = false),
            Device("WF-1000XM5", "00:00:00:00:00:02", connected = false),
        )
    }
}
