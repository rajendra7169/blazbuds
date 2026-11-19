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

/** What each platform brings: the earbuds it has paired, a quick look at one, and a live link to one. */
interface Platform {
    suspend fun paired(): List<Device>

    /** Connected or not, and the battery the system itself reads, for the list. */
    suspend fun peek(device: Device): Pair<Boolean, Int?> = device.connected to null

    fun open(device: Device, scope: CoroutineScope): Buds

    /** Whether this platform can open its Bluetooth settings for pairing. */
    val hasBluetoothSettings: Boolean get() = false

    fun bluetoothSettings() {}
}

/** One row of the earbuds list. */
data class Pair2(val device: Device, val support: Support, val battery: Battery?)

/** The earbuds the app knows and the one it is talking to now, remembered between launches. */
class Hub(private val platform: Platform, private val scope: CoroutineScope, private val store: Settings? = Settings()) {
    private val list = MutableStateFlow<List<Pair2>>(emptyList())
    val earbuds: StateFlow<List<Pair2>> = list
    private val chosen = MutableStateFlow<Buds?>(null)
    val current: StateFlow<Buds?> = chosen
    private var address: String? = store?.getStringOrNull(CHOSEN)


    suspend fun reload() {
        val found = platform.paired().map { device ->
            val (connected, battery) = platform.peek(device)
            Pair2(device.copy(connected = connected), Catalog.model(device).support, battery?.let { Battery(it) })
        }.sortedWith(compareByDescending<Pair2> { it.device.connected }.thenBy { it.support.ordinal }.thenBy { it.device.name })
        list.value = found
        if (chosen.value == null) {
            val pick = found.firstOrNull { it.device.address == address }
                ?: found.firstOrNull { it.device.connected && it.support == Support.Full }
            if (pick != null) choose(pick.device)
        }
    }

    fun choose(device: Device) {
        chosen.value?.close()
        address = device.address
        store?.putString(CHOSEN, device.address)
        chosen.value = platform.open(device, scope).also { it.watch(watching) }
    }

    private var watching = true

    /** Whether the app is on screen; the earbuds are asked often only while it is. */
    fun watch(on: Boolean) {
        watching = on
        chosen.value?.watch(on)
    }

    fun refresh() {
        scope.launch { reload() }
    }

    fun openBluetoothSettings() = platform.bluetoothSettings()

    val canOpenSettings: Boolean get() = platform.hasBluetoothSettings

    val chosenAddress: String? get() = address

    private companion object {
        const val CHOSEN = "earbuds"
    }
}
