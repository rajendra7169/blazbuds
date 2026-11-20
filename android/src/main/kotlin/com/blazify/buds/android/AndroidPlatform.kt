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
class AndroidPlatform(private val context: Context, override val spatial: com.blazify.buds.ui.Spatial? = null) : Platform {
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

    override val canFind: Boolean = true

    private var tone: android.media.AudioTrack? = null
    private var volumeBefore = -1
    private val audio get() = context.getSystemService(android.media.AudioManager::class.java)

    /** Plays the find sound as media, so it goes to the earbuds, louder while it plays and back after. */
    override fun findSound(left: Boolean, right: Boolean) {
        if (!left && !right) {
            stopSound()
            steadyVolume(false)
            return
        }
        setVolume(0.85)
        playSound(com.blazify.buds.core.FindTone.samples(left, right), loop = true)
    }

    override fun playSound(samples: ShortArray, loop: Boolean) {
        stopSound()
        val track = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(com.blazify.buds.core.FindTone.RATE)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        if (loop) track.setLoopPoints(0, samples.size / 2, -1)
        track.play()
        tone = track
    }

    override fun stopSound() {
        tone?.let {
            runCatching { it.stop() }
            it.release()
        }
        tone = null
    }

    override fun steadyVolume(on: Boolean) {
        if (on) setVolume(0.6) else if (volumeBefore >= 0) {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, volumeBefore, 0)
            volumeBefore = -1
        }
    }

    private fun setVolume(part: Double) {
        if (volumeBefore < 0) volumeBefore = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (max * part).toInt(), 0)
    }

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
            // Connected for sound but the control channel refused: another app on this phone, most often the brand's
            // own one, holds it, as only one may at a time.
            throw LinkException("Couldn't reach the earbuds: ${e.message}", busy = bt.connectedNow() || e.message?.contains("busy", true) == true)
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
