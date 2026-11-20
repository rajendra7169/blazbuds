package com.blazify.buds.core.link

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Bluetooth on Linux through the kernel's own sockets (BlueZ), the way any C program would: no helper programs and
 * no D-Bus, so it works on any distribution.
 */
object LinuxBluetooth : DesktopBluetooth {
    private const val AF_BLUETOOTH = 31
    private const val SOCK_STREAM = 1
    private const val SOCK_SEQPACKET = 5
    private const val BTPROTO_L2CAP = 0
    private const val BTPROTO_RFCOMM = 3
    private const val SOL_SOCKET = 1
    private const val SO_RCVTIMEO = 20
    private const val SO_SNDTIMEO = 21
    private const val SDP_PSM = 1
    private const val EBUSY = 16
    private const val ECONNREFUSED = 111
    private const val EHOSTDOWN = 112

    @Suppress("FunctionName")
    private interface C : Library {
        fun socket(domain: Int, type: Int, protocol: Int): Int
        fun connect(fd: Int, address: ByteArray, length: Int): Int
        fun read(fd: Int, buffer: ByteArray, count: Long): Long
        fun write(fd: Int, buffer: ByteArray, count: Long): Long
        fun setsockopt(fd: Int, level: Int, name: Int, value: ByteArray, length: Int): Int
        fun shutdown(fd: Int, how: Int): Int
        fun close(fd: Int): Int
    }

    private val c: C by lazy { Native.load("c", C::class.java) }

    /** Opens the earbuds' service [uuid], looking up its channel first. */
    override fun open(address: String, uuid: String, timeoutMs: Int): Connection {
        val channel = channel(address, uuid, timeoutMs)
            ?: throw LinkException("These earbuds don't offer the control service")
        return rfcomm(address, channel, timeoutMs)
    }

    fun channel(address: String, uuid: String, timeoutMs: Int = 6000): Int? {
        val fd = c.socket(AF_BLUETOOTH, SOCK_SEQPACKET, BTPROTO_L2CAP)
        check(fd >= 0) { "No Bluetooth on this computer (${Native.getLastError()})" }
        try {
            timeouts(fd, timeoutMs)
            val at = ByteArray(14)
            at[0] = AF_BLUETOOTH.toByte()
            at[2] = SDP_PSM.toByte()
            bdaddr(address).copyInto(at, 4)
            if (c.connect(fd, at, at.size) != 0) fail("look up", Native.getLastError())
            val request = Sdp.request(Sdp.uuid(uuid))
            c.write(fd, request, request.size.toLong())
            val reply = ByteArray(1024)
            val count = c.read(fd, reply, reply.size.toLong()).toInt()
            if (count <= 0) throw LinkException("The earbuds didn't answer")
            return Sdp.channel(reply.copyOf(count))
        } finally {
            c.close(fd)
        }
    }

    override fun rfcomm(address: String, channel: Int, timeoutMs: Int): Connection {
        val fd = c.socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM)
        check(fd >= 0) { "No Bluetooth on this computer (${Native.getLastError()})" }
        timeouts(fd, timeoutMs, readToo = false)
        val at = ByteArray(10)
        at[0] = AF_BLUETOOTH.toByte()
        bdaddr(address).copyInto(at, 2)
        at[8] = channel.toByte()
        if (c.connect(fd, at, at.size) != 0) {
            val error = Native.getLastError()
            c.close(fd)
            fail("connect to", error)
        }
        return Socket(fd)
    }

    private class Socket(private val fd: Int) : Connection {
        @Volatile private var closed = false

        override fun read(into: ByteArray): Int {
            if (closed) return -1
            val count = c.read(fd, into, into.size.toLong()).toInt()
            return if (count <= 0) -1 else count
        }

        override fun write(bytes: ByteArray) {
            if (c.write(fd, bytes, bytes.size.toLong()) != bytes.size.toLong()) throw LinkException("The earbuds stopped listening")
        }

        override fun close() {
            if (closed) return
            closed = true
            // Shutting down first wakes a read that is waiting on another thread.
            c.shutdown(fd, 2)
            c.close(fd)
        }
    }

    /** Linux keeps a Bluetooth address byte-reversed. */
    private fun bdaddr(address: String): ByteArray =
        address.split(":").map { it.toInt(16).toByte() }.reversed().toByteArray()

    private fun timeouts(fd: Int, ms: Int, readToo: Boolean = true) {
        val time = ByteArray(16)
        val seconds = (ms / 1000).toLong()
        val micros = ((ms % 1000) * 1000).toLong()
        for (i in 0 until 8) {
            time[i] = (seconds shr (8 * i)).toByte()
            time[8 + i] = (micros shr (8 * i)).toByte()
        }
        c.setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, time, time.size)
        if (readToo) c.setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, time, time.size)
    }

    private fun fail(what: String, error: Int): Nothing = when (error) {
        EBUSY -> throw LinkException("Another app is using the earbuds", busy = true)
        ECONNREFUSED -> throw LinkException("The earbuds refused the connection; another app may be using them", busy = true)
        EHOSTDOWN -> throw LinkException("The earbuds are out of reach or in their case")
        else -> throw LinkException("Couldn't $what the earbuds (error $error)")
    }

    /** Pairs this computer knows, and which are connected now, from BlueZ's own command-line tool. */
    override fun devices(): List<Device> {
        fun list(vararg args: String): List<Pair<String, String>> = runCatching {
            val process = ProcessBuilder("bluetoothctl", "devices", *args).redirectErrorStream(true).start()
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor()
            lines.mapNotNull { line ->
                val parts = line.split(" ", limit = 3)
                if (parts.size == 3 && parts[0] == "Device") parts[1] to parts[2] else null
            }
        }.getOrDefault(emptyList())
        val connected = list("Connected").map { it.first }.toSet()
        return list().map { (address, name) -> Device(name, address, address in connected) }
    }

    /** Whether a device is connected, and the battery BlueZ reads for any headset that reports one. */
    override fun info(address: String): Pair<Boolean, Int?> = runCatching {
        val process = ProcessBuilder("bluetoothctl", "info", address).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor()
        val connected = lines.any { it.trim() == "Connected: yes" }
        val battery = lines.firstOrNull { it.trim().startsWith("Battery Percentage:") }
            ?.substringAfter("(")?.substringBefore(")")?.trim()?.toIntOrNull()
        connected to battery
    }.getOrDefault(false to null)
}
