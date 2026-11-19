package com.blazify.buds.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blazify.buds.core.BasicBuds
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.link.LinuxBluetooth
import com.blazify.buds.core.link.info
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.SoundcoreBuds
import com.blazify.buds.ui.BudsApp
import com.blazify.buds.ui.Hub
import com.blazify.buds.ui.Platform
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension

/** Bluetooth on a Linux computer, through BlueZ. */
object LinuxPlatform : Platform {
    override suspend fun paired(): List<Device> = LinuxBluetooth.devices()

    override suspend fun peek(device: Device): Pair<Boolean, Int?> = LinuxBluetooth.info(device.address)

    override fun open(device: Device, scope: CoroutineScope): Buds = when (Catalog.model(device).support) {
        Support.Full -> SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, { LinuxBluetooth.open(device.address, A3947.SERVICE) })
            .also { it.start() }
        Support.Basic -> BasicBuds(device.name, scope) { LinuxBluetooth.info(device.address) }.also { it.start() }
    }

    override val hasBluetoothSettings: Boolean = true

    override fun bluetoothSettings() {
        runCatching { ProcessBuilder("gnome-control-center", "bluetooth").start() }
            .recoverCatching { ProcessBuilder("blueman-manager").start() }
    }
}

fun main(args: Array<String>) = application {
    val state = rememberWindowState(size = DpSize(440.dp, 900.dp))
    Window(onCloseRequest = ::exitApplication, state = state, title = "Blazbuds") {
        // Never narrower or shorter than the phone layout, so nothing breaks.
        window.minimumSize = Dimension(360, 560)
        if ("--demo" in args) {
            BudsApp(remember { DemoBuds() })
        } else {
            val scope = rememberCoroutineScope()
            BudsApp(remember { Hub(LinuxPlatform, scope) })
        }
    }
}
