package com.blazify.buds.ui

import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Whose Bluetooth settings the pairing guide draws. */
enum class SystemLook { Android, Ios, Linux, Windows, Mac }

/** What each platform brings: the earbuds it has paired, a quick look at one, and a live link to one. */
interface Platform {
    val look: SystemLook get() = SystemLook.Android

    suspend fun paired(): List<Device>

    /** Connected or not, and the battery the system itself reads, for the list. */
    suspend fun peek(device: Device): Pair<Boolean, Int?> = device.connected to null

    fun open(device: Device, scope: CoroutineScope): Buds

    /** Whether Bluetooth is switched on; when it is off the system lists no paired devices at all. */
    fun bluetoothOn(): Boolean = true

    /** Showing over other apps (the connect card on a phone); null where no permission is needed. */
    val overApps: OverApps? get() = null

    /** Whether this platform can open its Bluetooth settings for pairing. */
    val hasBluetoothSettings: Boolean get() = false

    fun bluetoothSettings() {}

    /** Hands text to the system's share sheet, or copies it where there is none. */
    fun share(text: String) {}

    /** Whether this platform can play the find sound through the earbuds. */
    val canFind: Boolean get() = false

    /** Plays the find sound through the left, the right or both earbuds, loud; both false stops it. */
    fun findSound(left: Boolean, right: Boolean) {}

    /** Plays 16-bit stereo samples at 44.1 kHz through the earbuds, once or looping, in place of any sound playing. */
    fun playSound(samples: ShortArray, loop: Boolean) {}

    fun stopSound() {}

    /**
     * Hears how loud the room is, in rough decibels, about ten times a second until the returned stop is called.
     * Null where there's no microphone to hear with.
     */
    fun listenToRoom(onLevel: (Double) -> Unit): (() -> Unit)? = null

    /** Asks for the microphone where the system wants asking first; [done] says if it may be used. */
    fun askMicrophone(done: (Boolean) -> Unit) = done(true)

    /** Shows a notice outside the app, like a low battery. */
    fun notify(title: String, text: String) {}

    /** Starts the look at the batteries every so often while the app is closed, where the platform allows it. */
    fun watchBatteryInBackground() {}

    /** Holds the media volume at a set level for the hearing test, or puts it back as it was. */
    fun steadyVolume(on: Boolean) {}

    /** The automations changed: a phone plans its next wake-up for the time rules again. */
    fun rulesChanged() {}

    /**
     * Whether this platform can look over what its Bluetooth reports, for Help. The iPhone lists no paired devices
     * and reaches earbuds only through the services they offer, so when something fails there this says why.
     */
    val hasBluetoothCheck: Boolean get() = false

    /** What the Bluetooth check found, as text to paste into a report. */
    suspend fun bluetoothCheck(): String = ""
}

/** One row of the earbuds list. */
data class Pair2(val device: Device, val support: Support, val battery: Battery?)

/**
 * The earbuds the app knows and the one it is talking to now, remembered between launches. Every pair that has ever
 * connected stays in the list; one removed by a swipe comes back by itself the next time it connects.
 */
class Hub(private val platform: Platform, private val scope: CoroutineScope, private val store: Settings? = Settings()) {
    fun listenToRoom(onLevel: (Double) -> Unit): (() -> Unit)? = platform.listenToRoom(onLevel)

    fun askMicrophone(done: (Boolean) -> Unit) = platform.askMicrophone(done)

    private val list = MutableStateFlow<List<Pair2>>(emptyList())
    val earbuds: StateFlow<List<Pair2>> = list
    private val chosen = MutableStateFlow<Buds?>(null)
    val current: StateFlow<Buds?> = chosen
    private var address: String? = store?.getStringOrNull(CHOSEN)
    private var chosenName: String? = store?.getStringOrNull(CHOSEN_NAME)
    private val bluetooth = MutableStateFlow(true)
    val bluetoothOn: StateFlow<Boolean> = bluetooth

    /** Battery history and the low-battery alert, fed by every state the open pair reports. */
    private val batteryLog = store?.let { BatteryLog(it) }

    /** The person's automations, run here while the app is open (the phone also runs them while it is closed). */
    val rules: RuleBook? = store?.let { RuleBook(it) }

    /** The open pair has been ready since it last connected, so its connect rules have had their turn. */
    private var greeted = false

    /** Puts an automation's changes on the open pair, one after another. */
    private fun run(found: List<Rule>) {
        val buds = chosen.value ?: return
        found.forEach { rule ->
            com.blazify.buds.core.Logbook.add("Automation: ${rule.trigger.words()} → ${rule.action.words()}")
            scope.launch { rule.action.changes().forEach { runCatching { buds.apply(it) } } }
        }
    }

    init {
        scope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            chosen.flatMapLatest { it?.state ?: kotlinx.coroutines.flow.emptyFlow() }.collect { state ->
                if (state.link != com.blazify.buds.core.model.Link.Connected) {
                    greeted = false
                    return@collect
                }
                rules?.let { book ->
                    if (state.isReady && state.sound != null && !greeted) {
                        greeted = true
                        book.reload()
                        @OptIn(kotlin.time.ExperimentalTime::class) run(book.forConnect(kotlin.time.Clock.System.now().toEpochMilliseconds()))
                    }
                    if (state.isReady) run(book.forBattery(state))
                }
                // Kept so the page can still show these earbuds' controls, faded, while another app has them.
                if (state.isReady && (state.sound != null || state.equalizer != null || state.toggles.isNotEmpty())) address?.let { at ->
                    lastReady[at] = state
                    // The mode is kept across restarts too, so the faded page never shows a mode the earbuds aren't in.
                    state.sound?.mode?.let { store?.putString(LAST_MODE + at, it.name) }
                }
                batteryLog?.look(state)?.let { platform.notify("Low battery", it) }
            }
        }
        platform.watchBatteryInBackground()
        // Time automations, looked at twice a minute while the app runs.
        if (rules != null) scope.launch {
            while (true) {
                if (chosen.value?.state?.value?.isReady == true) {
                    rules.reload()
                    run(rules.dueAt(localMoment(), lateBy = 1))
                }
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
    val look: SystemLook get() = platform.look

    private val lastReady = mutableMapOf<String, com.blazify.buds.core.model.BudsState>()

    /**
     * What to show while the open pair can't be reached: its controls as last seen this session, or for a pair the
     * app fully controls the usual ones, with today's link, name and batteries (never old or made-up levels).
     */
    fun ghost(state: com.blazify.buds.core.model.BudsState): com.blazify.buds.core.model.BudsState? {
        if (state.isReady) return null
        val at = address ?: return null
        val seen = lastReady[at] ?: run {
            val device = com.blazify.buds.core.link.Device(state.name.ifBlank { chosenName ?: "" }, at, false)
            if (Catalog.model(device).support != Support.Full) return null
            val usual = com.blazify.buds.core.demo.DemoBuds().state.value
            val mode = store?.getStringOrNull(LAST_MODE + at)?.let { runCatching { com.blazify.buds.core.model.ListeningMode.valueOf(it) }.getOrNull() }
                ?: com.blazify.buds.core.model.ListeningMode.Normal
            usual.copy(multipoint = null, firmware = null, serial = null, sound = usual.sound?.copy(mode = mode))
        }
        return seen.copy(name = state.name, model = state.model.ifBlank { seen.model }, link = state.link, left = state.left, right = state.right, case = state.case, overall = state.overall)
    }

    val overApps: OverApps? get() = platform.overApps

    /** Every pair that has connected, newest first: address to name. */
    private val known = linkedMapOf<String, String>().apply {
        store?.getStringOrNull(KNOWN)?.split("\n")?.filter { "\t" in it }?.forEach { line ->
            val (a, n) = line.split("\t", limit = 2)
            put(a, n)
        }
    }
    private val hidden = (store?.getStringOrNull(HIDDEN)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()).toMutableSet()

    private fun save() {
        store?.putString(KNOWN, known.entries.joinToString("\n") { "${it.key}\t${it.value}" })
        store?.putString(HIDDEN, hidden.joinToString(","))
    }

    suspend fun reload() {
        // Asking the system about Bluetooth can take a while (on Linux it runs bluetoothctl once per pair), so it is
        // done away from the screen's thread; the page refreshes every few seconds and would hitch each time.
        val (on, paired, looks) = withContext(Dispatchers.IO) {
            val on = platform.bluetoothOn()
            val paired = platform.paired().associateBy { it.address }
            Triple(on, paired, paired.values.associate { device -> device.address to platform.peek(device) })
        }
        bluetooth.value = on
        val before = known.toMap() to hidden.toSet()
        // Anything connected now joins the list, and comes back if it had been removed.
        looks.filter { it.value.first }.keys.forEach { at ->
            val device = paired.getValue(at)
            if (at !in known) known[at] = device.name
            hidden.remove(at)
        }
        // On a first launch, the pairs the app can fully control are listed even before they connect.
        if (known.isEmpty()) paired.values.filter { Catalog.model(it).support != Support.Basic }.forEach { known[it.address] = it.name }
        address?.let { at -> if (at !in known && chosenName != null) known[at] = chosenName!! }
        // Written only when something changed, not on every refresh.
        if (before != (known.toMap() to hidden.toSet())) save()
        list.value = known.filterKeys { it !in hidden }.map { (at, name) ->
            val look = looks[at]
            val device = Device(paired[at]?.name ?: name, at, look?.first == true)
            Pair2(device, Catalog.model(device).support, look?.second?.let { Battery(it) })
        }.sortedByDescending { it.device.connected }
        if (chosen.value == null) {
            val pick = list.value.firstOrNull { it.device.address == address }
                ?: list.value.firstOrNull { it.device.connected && it.support != Support.Basic }
            if (pick != null) {
                choose(pick.device)
            } else if (address != null && chosenName != null) {
                // Bluetooth is off or the earbuds are away: keep them, and let them connect when they can.
                choose(Device(chosenName!!, address!!, connected = false))
            }
        }
    }

    fun choose(device: Device) {
        if (chosen.value != null && device.address == address) return
        chosen.value?.close()
        address = device.address
        store?.putString(CHOSEN, device.address)
        chosenName = device.name
        store?.putString(CHOSEN_NAME, device.name)
        chosen.value = platform.open(device, scope).also { it.watch(watching) }
    }

    /** Lets go of the earbuds, as the screen showing them closes; only one link to a pair can be open at a time. */
    fun shutdown() {
        chosen.value?.close()
        chosen.value = null
    }

    /** Takes a pair off the list until it connects again. */
    fun remove(device: Device) {
        hidden += device.address
        save()
        list.value = list.value.filterNot { it.device.address == device.address }
        if (device.address == address) {
            chosen.value?.close()
            chosen.value = null
            address = null
            store?.remove(CHOSEN)
        }
    }

    private var watching = true

    /** Whether the app is on screen; the earbuds are asked often only while it is. */
    fun watch(on: Boolean) {
        watching = on
        if (on) bluetooth.value = platform.bluetoothOn()
        chosen.value?.watch(on)
    }

    fun refresh() {
        scope.launch { reload() }
    }

    fun openBluetoothSettings() = platform.bluetoothSettings()

    fun share(text: String) = platform.share(text)

    val canFind: Boolean get() = platform.canFind

    /**
     * Find my earbuds: earbuds that can ring by themselves do (louder, and heard even when nothing plays to them);
     * the rest play the phone's or computer's sound, like music.
     */
    fun findSound(left: Boolean, right: Boolean) {
        val buds = chosen.value
        if (buds != null && buds.state.value.canRing) scope.launch { runCatching { buds.ring(left, right) } }
        else platform.findSound(left, right)
    }

    fun playSound(samples: ShortArray, loop: Boolean) = platform.playSound(samples, loop)

    fun stopSound() = platform.stopSound()

    fun steadyVolume(on: Boolean) = platform.steadyVolume(on)

    fun rulesChanged() = platform.rulesChanged()

    val canOpenSettings: Boolean get() = platform.hasBluetoothSettings

    /** The Bluetooth check for Help, where the platform has one. */
    val bluetoothCheck: (suspend () -> String)? get() = if (platform.hasBluetoothCheck) ({ platform.bluetoothCheck() }) else null

    val chosenAddress: String? get() = address

    private companion object {
        const val CHOSEN = "earbuds"
        const val CHOSEN_NAME = "earbuds_name"
        const val KNOWN = "known_earbuds"
        const val LAST_MODE = "last_mode_"
        const val HIDDEN = "removed_earbuds"
    }
}
