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
import com.blazify.buds.ui.screens.EarPrintScreen
import com.blazify.buds.ui.screens.ConnectGuideScreen
import androidx.compose.runtime.LaunchedEffect
import com.blazify.buds.ui.screens.ControlsScreen
import com.blazify.buds.ui.screens.DeviceTab
import com.blazify.buds.ui.screens.EqualizerScreen
import com.blazify.buds.ui.screens.HomeScreen
import com.blazify.buds.ui.screens.MyDevicesScreen
import com.blazify.buds.ui.screens.Place
import com.blazify.buds.ui.screens.AutomationsScreen
import com.blazify.buds.ui.blocks.drawRocket
import com.blazify.buds.ui.blocks.drawCaseArt
import com.blazify.buds.ui.blocks.drawStar
import com.blazify.buds.ui.screens.ChangelogScreen
import com.blazify.buds.ui.screens.SettingsScreen
import com.blazify.buds.ui.screens.SupportScreen
import com.blazify.buds.ui.screens.Tab
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val VERSION = "1.3.0"

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
fun BudsApp(hub: Hub, prefs: Prefs = remember { Prefs() }, start: Place? = Place.Earbuds, startTab: Tab = Tab.Home, watchUpdates: Boolean = true) {
    BudsTheme(prefs.theme) {
        CompositionLocalProvider(LocalReduceMotion provides prefs.reduceMotion, LocalPrefs provides prefs, LocalLook provides hub.look, LocalOverApps provides hub.overApps, LocalShare provides { text: String -> hub.share(text) }, LocalCanFind provides hub.canFind,
            LocalRoomEar provides remember(hub) {
                object : RoomEar {
                    override fun listen(onLevel: (Double) -> Unit) = hub.listenToRoom(onLevel)
                    override fun ask(done: (Boolean) -> Unit) = hub.askMicrophone(done)
                }
            },
            LocalSoundOut provides remember(hub) {
                object : SoundOut {
                    override fun play(samples: ShortArray, loop: Boolean) = hub.playSound(samples, loop)
                    override fun stop() = hub.stopSound()
                    override fun steadyVolume(on: Boolean) = hub.steadyVolume(on)
                }
            },
        ) {
            val buds by hub.current.collectAsState()
            val earbuds by hub.earbuds.collectAsState()
            if (watchUpdates) UpdateWatch(prefs)
            UpdatePopup(prefs, hub.look)
            OverAppsPopup(prefs, hub.overApps)
            StarPopup(prefs)
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
                // Back steps back inside the app: a page closes, another tab returns Home, Home goes to My devices,
                // and only My devices leaves.
                val shownPlace = if (now == null && place == null) Place.Earbuds else place
                val canGoBack = when (shownPlace) {
                    Place.Earbuds -> false
                    else -> true
                }
                // Earbuds that were connected and stay lost for a while: back to My devices, rather than a page of
                // settings that can't reach them. A short drop (some pairs reconnect after a change) doesn't count.
                var wasConnected by remember(now) { mutableStateOf(false) }
                LaunchedEffect(now, state?.link) {
                    when (state?.link) {
                        com.blazify.buds.core.model.Link.Connected -> wasConnected = true
                        com.blazify.buds.core.model.Link.Lost -> if (wasConnected && place == null) {
                            kotlinx.coroutines.delay(8_000)
                            wasConnected = false
                            place = Place.Earbuds
                        }
                        else -> {}
                    }
                }
                SystemBack(canGoBack) {
                    when (shownPlace) {
                        null -> if (tab != Tab.Home) tab = Tab.Home else place = Place.Earbuds
                        Place.Add, Place.Connect -> place = Place.Earbuds
                        Place.Fit -> place = null
                        // What's new is opened from Settings, so back returns there.
                        Place.Changelog -> place = Place.Settings
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
                                    // Out of reach (another app has them, or away): the controls stay in view, faded, and
                                    // changes go nowhere until the earbuds answer again.
                                    val ghost = hub.ghost(state)
                                    val view = ghost ?: state
                                    val inactive = ghost != null
                                    val change: (Change) -> Unit = { if (!inactive) scope.launch { now.apply(it) } }
                                    when (t) {
                                        Tab.Home -> HomeScreen(
                                            view, change, open = { place = it }, goTab = { tab = it }, inactive = inactive,
                                            retry = { scope.launch { hub.reload(); now.refresh() } },
                                            bluetoothOn = bluetoothOn,
                                            turnOnBluetooth = settingsAction(hub),
                                        )
                                        Tab.Sound -> com.blazify.buds.ui.blocks.Inactive(inactive) { EqualizerScreen(view, change, earPrint = { place = Place.EarPrint }) }
                                        Tab.Controls -> com.blazify.buds.ui.blocks.Inactive(inactive) { ControlsScreen(view, change) }
                                        Tab.Device -> DeviceTab(view, change, inactive) { place = it }
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
                        Place.Settings -> SettingsScreen(close, openChangelog = { place = Place.Changelog })
                        Place.Changelog -> ChangelogScreen(back = { place = Place.Settings })
                        Place.Find -> if (state != null) FindScreen(state, { l, r -> hub.findSound(l, r) }, close)
                        Place.Fit -> if (now != null) FitTestScreen(now, close)
                        Place.EarPrint -> if (now != null && state != null) EarPrintScreen(state, { c -> scope.launch { now.apply(c) } }, close)
                        Place.Support -> SupportScreen(state, VERSION, close)
                        Place.Automations -> hub.rules?.let { book ->
                            @OptIn(kotlin.time.ExperimentalTime::class)
                            AutomationsScreen(book, now = { kotlin.time.Clock.System.now().toEpochMilliseconds() }, changed = { hub.rulesChanged() }, back = close)
                        }
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
                // Popups are centred on the whole window; moving them half the rail puts them over the page.
                Box(Modifier.weight(1f)) {
                    androidx.compose.runtime.CompositionLocalProvider(com.blazify.buds.ui.blocks.LocalPopupShift provides com.blazify.buds.ui.blocks.RailWidth / 2) { content() }
                }
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

/**
 * Looks for a newer version while the person lets it: when the app starts, then every few hours while it keeps
 * running. It asks GitHub at most every six hours however often the app is opened, and what it found is kept. The
 * computer app runs this for its whole life, not just while its window is open, since it lives in the tray.
 */
@Composable
fun UpdateWatch(prefs: Prefs) {
    LaunchedEffect(prefs.checkUpdates) {
        while (prefs.checkUpdates) {
            @OptIn(kotlin.time.ExperimentalTime::class)
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            if (now - prefs.lastUpdateCheck >= 6 * HOUR) {
                Updates.latest()?.let { found ->
                    prefs.lastUpdateCheck = now
                    prefs.foundUpdate(found)
                }
            }
            kotlinx.coroutines.delay(HOUR)
        }
    }
}

private const val HOUR = 60 * 60 * 1000L

/**
 * "Blazbuds x.y is here": the popup when a newer version is known, at most as often as [UpdatePrompt] allows. Update
 * opens this system's download (the release page where there is none); Later, or tapping outside, puts it off.
 */
@Composable
private fun UpdatePopup(prefs: Prefs, look: SystemLook) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val update = prefs.knownUpdate ?: return
    @OptIn(kotlin.time.ExperimentalTime::class)
    val now = remember(update.version) { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    var open by remember(update.version) {
        mutableStateOf(UpdatePrompt.shouldOffer(update.version, prefs.declinedUpdate, prefs.declinedAt, prefs.remindedUpdate, now).also { if (it) LaunchPopups.used = true })
    }
    if (!open) return
    val later = {
        prefs.putOffUpdate(update.version, now)
        open = false
    }
    com.blazify.buds.ui.blocks.PromptCard(
        title = "Blazbuds ${update.version} is here",
        body = "New features and fixes are ready. Your sounds and settings stay as they are.",
        buttons = com.blazify.buds.ui.blocks.PromptButtons(
            primary = "Update",
            onPrimary = {
                open = false
                runCatching { uri.openUri(update.fileFor(look) ?: update.page) }
            },
            secondary = "Later",
            onSecondary = later,
        ),
        onDismissRequest = later,
    ) { time -> drawRocket(time) }
}

/**
 * "Enjoying Blazbuds?": the star ask, when [StarPrompt] says it is due. It waits a little after the app opens, so the
 * update check has spoken first, and stays away on a launch where a newer version is waiting.
 */
@Composable
private fun StarPopup(prefs: Prefs) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        @OptIn(kotlin.time.ExperimentalTime::class)
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (!StarPrompt.onOpened(prefs, now)) return@LaunchedEffect
        kotlinx.coroutines.delay(15_000)
        if (prefs.knownUpdate != null || LaunchPopups.used) return@LaunchedEffect
        LaunchPopups.used = true
        prefs.starAsked()
        open = true
    }
    if (!open) return
    com.blazify.buds.ui.blocks.PromptCard(
        title = "Enjoying Blazbuds?",
        body = "A star on GitHub helps others find it. It's free, with no ads.",
        buttons = com.blazify.buds.ui.blocks.PromptButtons(
            primary = "Star it",
            onPrimary = {
                prefs.starStop()
                open = false
                runCatching { uri.openUri(REPO_URL) }
            },
            secondary = "Already starred",
            onSecondary = {
                prefs.starStop()
                open = false
            },
        ),
        // Tapping outside only closes it; the next ask comes on its day.
        onDismissRequest = { open = false },
    ) { time -> drawStar(time) }
}

/** One popup per launch at most: the update first, then the connect-card ask, then (on another day) the star. */
private object LaunchPopups {
    var used = false
}

/**
 * "See your battery as you open the case": on a phone where the connect card is on but not yet allowed over other
 * apps, asked when the app opens, at most twice a week apart. Allow opens the phone's own page for it.
 */
@Composable
private fun OverAppsPopup(prefs: Prefs, over: OverApps?) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(over) {
        if (over == null || !prefs.connectPopup || over.allowed() || prefs.overAppsAsks >= 2) return@LaunchedEffect
        @OptIn(kotlin.time.ExperimentalTime::class)
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (prefs.overAppsAsks == 1 && now - prefs.overAppsAskedAt < UpdatePrompt.REMIND_AFTER_MS) return@LaunchedEffect
        // A moment after opening, and only if the update popup didn't take this launch.
        kotlinx.coroutines.delay(1_500)
        if (LaunchPopups.used || over.allowed()) return@LaunchedEffect
        LaunchPopups.used = true
        prefs.overAppsAsked(now)
        open = true
    }
    if (!open || over == null) return
    com.blazify.buds.ui.blocks.PromptCard(
        title = "See your battery as you open the case",
        body = "Allow Blazbuds to show over other apps, and the case and each battery slide up as your earbuds connect.",
        buttons = com.blazify.buds.ui.blocks.PromptButtons(
            primary = "Allow",
            onPrimary = {
                open = false
                over.ask()
            },
            secondary = "Not now",
            onSecondary = { open = false },
        ),
        onDismissRequest = { open = false },
    ) { time -> drawCaseArt(time) }
}

