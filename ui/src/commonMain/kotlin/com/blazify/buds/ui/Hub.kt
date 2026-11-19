package com.blazify.buds.ui

import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    /** Whether this platform can open its Bluetooth settings for pairing. */
    val hasBluetoothSettings: Boolean get() = false

    fun bluetoothSettings() {}

    /** Hands text to the system's share sheet, or copies it where there is none. */
    fun share(text: String) {}

    /** Whether this platform can play the find sound through the earbuds. */
    val canFind: Boolean get() = false

    /** Plays the find sound through the left, the right or both earbuds, loud; both false stops it. */
    fun findSound(left: Boolean, right: Boolean) {}
}

/** One row of the earbuds list. */
data class Pair2(val device: Device, val support: Support, val battery: Battery?)

/**
 * The earbuds the app knows and the one it is talking to now, remembered between launches. Every pair that has ever
 * connected stays in the list; one removed by a swipe comes back by itself the next time it connects.
 */
class Hub(private val platform: Platform, private val scope: CoroutineScope, private val store: Settings? = Settings()) {
    private val list = MutableStateFlow<List<Pair2>>(emptyList())
    val earbuds: StateFlow<List<Pair2>> = list
    private val chosen = MutableStateFlow<Buds?>(null)
    val current: StateFlow<Buds?> = chosen
    private var address: String? = store?.getStringOrNull(CHOSEN)
    private var chosenName: String? = store?.getStringOrNull(CHOSEN_NAME)
    private val bluetooth = MutableStateFlow(true)
    val bluetoothOn: StateFlow<Boolean> = bluetooth
    val look: SystemLook get() = platform.look

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
        bluetooth.value = platform.bluetoothOn()
        val paired = platform.paired().associateBy { it.address }
        val looks = paired.values.associate { device -> device.address to platform.peek(device) }
        // Anything connected now joins the list, and comes back if it had been removed.
        looks.filter { it.value.first }.keys.forEach { at ->
            val device = paired.getValue(at)
            if (at !in known) known[at] = device.name
            hidden.remove(at)
        }
        // On a first launch, the pairs the app can fully control are listed even before they connect.
        if (known.isEmpty()) paired.values.filter { Catalog.model(it).support == Support.Full }.forEach { known[it.address] = it.name }
        address?.let { at -> if (at !in known && chosenName != null) known[at] = chosenName!! }
        save()
        list.value = known.filterKeys { it !in hidden }.map { (at, name) ->
            val look = looks[at]
            val device = Device(paired[at]?.name ?: name, at, look?.first == true)
            Pair2(device, Catalog.model(device).support, look?.second?.let { Battery(it) })
        }.sortedByDescending { it.device.connected }
        if (chosen.value == null) {
            val pick = list.value.firstOrNull { it.device.address == address }
                ?: list.value.firstOrNull { it.device.connected && it.support == Support.Full }
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

    fun findSound(left: Boolean, right: Boolean) = platform.findSound(left, right)

    val canOpenSettings: Boolean get() = platform.hasBluetoothSettings

    val chosenAddress: String? get() = address

    private companion object {
        const val CHOSEN = "earbuds"
        const val CHOSEN_NAME = "earbuds_name"
        const val KNOWN = "known_earbuds"
        const val HIDDEN = "removed_earbuds"
    }
}
