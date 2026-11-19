package com.blazify.buds.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.blazify.buds.core.BasicBuds
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.SoundcoreBuds
import com.blazify.buds.ui.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/** Bluetooth on Android: the paired audio devices, and a socket to the earbuds' control service. */
private const val TAG = "Blazbuds"

@SuppressLint("MissingPermission")
class AndroidPlatform(private val context: Context) : Platform {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override suspend fun paired(): List<Device> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || Catalog.model(it.toDevice()).support == Support.Full }
            .map { it.toDevice() }
    }.getOrDefault(emptyList())

    override suspend fun peek(device: Device): Pair<Boolean, Int?> {
        val bt = adapter?.getRemoteDevice(device.address) ?: return false to null
        return bt.connectedNow() to bt.batteryNow()
    }

    override fun open(device: Device, scope: CoroutineScope): Buds {
        val bt = adapter!!.getRemoteDevice(device.address)
        val buds = when (Catalog.model(device).support) {
            Support.Full -> SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, { socket(bt) }, { Log.i(TAG, it) }).also { it.start() }
            Support.Basic -> BasicBuds(device.name, scope) { bt.connectedNow() to bt.batteryNow() }.also { it.start() }
        }
        return buds
    }

    /** The pair the app itself is showing: the widget and tile use its link, and the widget follows its state. */
    fun watchForWidget(buds: Buds, scope: CoroutineScope) {
        Remote.live = buds
        scope.launch {
            buds.state.collect { state ->
                if (state.isReady) {
                    Remote.remember(state)
                    BudsWidget.refresh(context)
                }
            }
        }
    }

    override fun bluetoothOn(): Boolean = adapter?.isEnabled == true

    override val hasBluetoothSettings: Boolean = true

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share your sound").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun bluetoothSettings() {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun socket(bt: BluetoothDevice): Connection {
        val socket = bt.createRfcommSocketToServiceRecord(UUID.fromString(A3947.SERVICE))
        try {
            socket.connect()
        } catch (e: IOException) {
            Log.w(TAG, "control channel: ${e.message}")
            runCatching { socket.close() }
            throw LinkException("Couldn't reach the earbuds: ${e.message}", busy = e.message?.contains("busy", true) == true)
        }
        return SocketConnection(socket)
    }

    private class SocketConnection(private val socket: BluetoothSocket) : Connection {
        private val input = socket.inputStream
        private val output = socket.outputStream

        override fun read(into: ByteArray): Int = try {
            input.read(into)
        } catch (_: IOException) {
            -1
        }

        override fun write(bytes: ByteArray) {
            try {
                output.write(bytes)
                output.flush()
            } catch (e: IOException) {
                throw LinkException("The earbuds stopped listening")
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private fun BluetoothDevice.toDevice() = Device(name ?: address, address, connectedNow())

    /** Android knows whether a device is connected and its battery but only says so through calls it hides. */
    private fun BluetoothDevice.connectedNow(): Boolean =
        runCatching { javaClass.getMethod("isConnected").invoke(this) as Boolean }.getOrDefault(false)

    private fun BluetoothDevice.batteryNow(): Int? =
        runCatching { (javaClass.getMethod("getBatteryLevel").invoke(this) as Int).takeIf { it in 0..100 } }.getOrNull()
}
