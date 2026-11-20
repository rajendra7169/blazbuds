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
import com.blazify.buds.core.catalog.Family
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.huawei.HuaweiBuds
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
private const val SOUNDCORE_PREFIX = "0cf12d31-fac3-4553-bd80-d6832e7"

@SuppressLint("MissingPermission")
class AndroidPlatform(
    private val context: Context,
    private val askMic: ((Boolean) -> Unit) -> Unit = { it(false) },
) : Platform {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override suspend fun paired(): List<Device> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || Catalog.model(it.toDevice()).support != Support.Basic }
            .map { it.toDevice() }
    }.getOrDefault(emptyList())

    override suspend fun peek(device: Device): Pair<Boolean, Int?> {
        val bt = adapter?.getRemoteDevice(device.address) ?: return false to null
        return bt.connectedNow() to bt.batteryNow()
    }

    override fun open(device: Device, scope: CoroutineScope): Buds {
        val bt = adapter!!.getRemoteDevice(device.address)
        val model = Catalog.model(device)
        val buds = when (model.family) {
            Family.Soundcore -> model.soundcore?.let { soundcore ->
                com.blazify.buds.core.soundcore.generic.GenericSoundcoreBuds(
                    device.name.trim().removePrefix("soundcore").removePrefix("Soundcore").trim().ifEmpty { soundcore.name },
                    soundcore, scope, { soundcoreSocket(bt, soundcore.service) }, { note(it) },
                ).also { it.start() }
            } ?: SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, { socket(bt) }, { note(it) }).also { it.start() }
            Family.Huawei -> HuaweiBuds(
                device.name, scope,
                // The serial-port service first; where it lands on the wrong channel, the usual ones by number.
                listOf({ serial(bt, null) }, { serial(bt, 1) }, { serial(bt, 16) }),
                { note(it) },
            ).also { it.start() }
            Family.Jieli -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, { serial(bt, null) }, { note(it) }).also { it.start() }
            Family.Samsung -> com.blazify.buds.core.samsung.SamsungModel.of(device.name)!!.let { model ->
                com.blazify.buds.core.samsung.SamsungBuds(device.name, model, scope, { socket(bt, model.service) }, { note(it) }).also { it.start() }
            }
            Family.Nothing -> com.blazify.buds.core.nothing.NothingBuds(device.name, scope, { socket(bt, com.blazify.buds.core.nothing.NothingBuds.SERVICE) }, { note(it) }).also { it.start() }
            Family.Melody -> com.blazify.buds.core.oneplus.MelodyBuds(
                device.name, scope,
                // HeyMelody's own channel, then the plain serial port some realme earbuds use.
                listOf({ socket(bt, com.blazify.buds.core.oneplus.Melody.SERVICE) }, { serial(bt, null) }),
                { note(it) },
            ).also { it.start() }
            Family.Sony -> com.blazify.buds.core.sony.SonyBuds(
                device.name, scope,
                listOf({ service(bt, com.blazify.buds.core.sony.Sony.SERVICE_V2) }, { service(bt, com.blazify.buds.core.sony.Sony.SERVICE_V1) }),
                { note(it) },
            ).also { it.start() }
            Family.None -> unknown(device, bt, scope)
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
    override val overApps: com.blazify.buds.ui.OverApps = object : com.blazify.buds.ui.OverApps {
        override fun allowed() = ConnectPopup.allowed(context)
        override fun ask() {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

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

    override fun notify(title: String, text: String) = BatteryJob.notifyLow(context, text)

    override fun watchBatteryInBackground() = BatteryJob.schedule(context)

    override fun askMicrophone(done: (Boolean) -> Unit) {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) done(true)
        else askMic(done)
    }

    override fun listenToRoom(onLevel: (Double) -> Unit): (() -> Unit)? {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val rate = 16_000
        val size = maxOf(android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT), rate / 5 * 2)
        // Voice recognition: the microphone's sound before the phone smooths it for calls.
        val record = runCatching {
            @Suppress("MissingPermission")
            android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, size)
        }.getOrNull()?.takeIf { it.state == android.media.AudioRecord.STATE_INITIALIZED } ?: return null
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val going = java.util.concurrent.atomic.AtomicBoolean(true)
        val worker = Thread({
            val noise = com.blazify.buds.core.earprint.RoomNoise()
            val chunk = ShortArray(rate / 10)
            runCatching { record.startRecording() }
            while (going.get()) {
                val got = record.read(chunk, 0, chunk.size)
                if (got > 0) {
                    val level = noise.level(chunk, got, rate)
                    main.post { if (going.get()) onLevel(level) }
                }
            }
            runCatching { record.stop() }
            record.release()
        }, "blazbuds-room")
        worker.start()
        return { going.set(false) }
    }

    override fun steadyVolume(on: Boolean) {
        // Full volume for the hearing check, as the beeps are made quiet on purpose; back to before afterwards.
        if (on) setVolume(1.0) else if (volumeBefore >= 0) {
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

    /**
     * Earbuds the app doesn't know by name. Many budget pairs (boAt, Noise, QCY, Ultima…) use a JieLi chip: if these
     * have a serial port, try JieLi's login once and remember the answer; otherwise, the basics the phone knows.
     */
    private fun unknown(device: Device, bt: BluetoothDevice, scope: CoroutineScope): Buds {
        val basic = { BasicBuds(device.name, scope) { bt.connectedNow() to bt.batteryNow() }.also { it.start() } }
        val key = "jieli_" + device.address
        val known = triedStore.getBooleanOrNull(key)
        val hasSerial = bt.uuids?.any { it.uuid.toString().equals(com.blazify.buds.core.huawei.HuaweiBuds.SERIAL, true) } == true
        return when {
            known == false || !hasSerial -> basic()
            known == true -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, { serial(bt, null) }, { note(it) }).also { it.start() }
            else -> com.blazify.buds.core.TryingBuds(
                scope,
                { verdict -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, { serial(bt, null) }, { note(it) }, verdict).also { it.start() } },
                basic,
                { works -> triedStore.putBoolean(key, works) },
            )
        }
    }

    private val triedStore by lazy { com.russhwolf.settings.Settings() }

    /** A link to the service with [uuid]. */
    private fun service(bt: BluetoothDevice, uuid: String): Connection {
        val socket = bt.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw LinkException("Couldn't reach the earbuds: ${e.message}", busy = false)
        }
        return SocketConnection(socket)
    }

    /** A serial-port link: by its service, or straight to a channel number where [channel] is given. */
    private fun serial(bt: BluetoothDevice, channel: Int?): Connection {
        val socket = if (channel == null) bt.createRfcommSocketToServiceRecord(UUID.fromString(com.blazify.buds.core.huawei.HuaweiBuds.SERIAL))
        else bt.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(bt, channel) as android.bluetooth.BluetoothSocket
        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw LinkException("Couldn't reach the earbuds: ${e.message}", busy = false)
        }
        return object : Connection {
            override fun read(into: ByteArray): Int = try { socket.inputStream.read(into) } catch (e: IOException) { -1 }
            override fun write(bytes: ByteArray) { socket.outputStream.write(bytes); socket.outputStream.flush() }
            override fun close() { runCatching { socket.close() } }
        }
    }

    /**
     * A Soundcore model's control channel: its own service first, then, as OpenSCQ30 does, any Soundcore service the
     * earbuds announce, then the plain serial port.
     */
    private fun soundcoreSocket(bt: BluetoothDevice, service: String): Connection {
        val announced = bt.uuids.orEmpty().map { it.uuid.toString().lowercase() }.filter { it.startsWith(SOUNDCORE_PREFIX) }
        val tries = (listOf(service.lowercase()) + announced + com.blazify.buds.core.soundcore.generic.SoundcoreModel.SERIAL_PORT).distinct()
        var first: LinkException? = null
        for (uuid in tries) {
            try {
                return socket(bt, uuid)
            } catch (e: LinkException) {
                // The model's own service says best why it failed (most often another app holding it).
                if (first == null) first = e
            }
        }
        throw first ?: LinkException("Couldn't reach the earbuds")
    }

    /** The earbuds' control channel, by its service. */
    private fun socket(bt: BluetoothDevice, service: String = A3947.SERVICE): Connection {
        val socket = bt.createRfcommSocketToServiceRecord(UUID.fromString(service))
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

/** A line for the system log and for the app's own "Report a problem" log. */
private fun note(line: String) {
    Log.i(TAG, line)
    com.blazify.buds.core.Logbook.add(line)
}
