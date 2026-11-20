package com.blazify.buds.core.link

import com.sun.jna.IntegerType
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bluetooth on Windows through its own sockets (Winsock's AF_BTH) and Bluetooth API, the way a C program would: no
 * drivers, no helper programs, nothing to install. Windows looks up the service's channel itself when a connection
 * names a service UUID instead of a channel.
 */
object WindowsBluetooth : DesktopBluetooth {
    private const val AF_BTH = 32
    private const val SOCK_STREAM = 1
    private const val BTHPROTO_RFCOMM = 3
    private const val SOL_SOCKET = 0xFFFF
    private const val SO_SNDTIMEO = 0x1005
    private const val SD_BOTH = 2

    private const val WSAEINTR = 10004
    private const val WSAENOTSOCK = 10038
    private const val WSAEADDRINUSE = 10048
    private const val WSAENETDOWN = 10050
    private const val WSAENETUNREACH = 10051
    private const val WSAETIMEDOUT = 10060
    private const val WSAECONNREFUSED = 10061
    private const val WSAEHOSTDOWN = 10064
    private const val WSAEHOSTUNREACH = 10065
    private const val WSASERVICE_NOT_FOUND = 10108

    /** Winsock's SOCKET: an unsigned number as wide as a pointer. */
    class Socket(value: Long = 0) : IntegerType(Native.POINTER_SIZE, value, true) {
        val invalid: Boolean get() = toLong() == -1L || (Native.POINTER_SIZE == 4 && toLong() == 0xFFFF_FFFFL)

        override fun toByte(): Byte = toLong().toByte()

        override fun toShort(): Short = toLong().toShort()
    }

    @Suppress("FunctionName")
    private interface Winsock : StdCallLibrary {
        fun WSAStartup(version: Short, data: Pointer): Int
        fun socket(family: Int, type: Int, protocol: Int): Socket
        fun connect(socket: Socket, address: ByteArray, length: Int): Int
        fun recv(socket: Socket, buffer: ByteArray, length: Int, flags: Int): Int
        fun send(socket: Socket, buffer: ByteArray, length: Int, flags: Int): Int
        fun setsockopt(socket: Socket, level: Int, name: Int, value: ByteArray, length: Int): Int
        fun shutdown(socket: Socket, how: Int): Int
        fun closesocket(socket: Socket): Int
    }

    @Suppress("FunctionName")
    private interface BluetoothApis : StdCallLibrary {
        fun BluetoothFindFirstRadio(params: ByteArray, radio: PointerByReference): Pointer?
        fun BluetoothFindRadioClose(find: Pointer): Int
        fun BluetoothFindFirstDevice(params: ByteArray, info: ByteArray): Pointer?
        fun BluetoothFindNextDevice(find: Pointer, info: ByteArray): Int
        fun BluetoothFindDeviceClose(find: Pointer): Int
    }

    @Suppress("FunctionName")
    private interface Kernel32 : StdCallLibrary {
        fun CloseHandle(handle: Pointer): Int
    }

    private val winsock: Winsock by lazy {
        Native.load("ws2_32", Winsock::class.java).also {
            // WSADATA is about 400 bytes on 64-bit Windows; a little more room costs nothing.
            val result = it.WSAStartup(0x0202, Memory(1024))
            check(result == 0) { "Windows sockets didn't start (error $result)" }
        }
    }

    /** BluetoothApis.dll since Windows 8; older systems keep the same calls in bthprops.cpl. */
    private val bluetooth: BluetoothApis by lazy {
        runCatching { Native.load("BluetoothApis", BluetoothApis::class.java) }
            .getOrElse { Native.load("bthprops.cpl", BluetoothApis::class.java) }
    }

    private val kernel32: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

    /** Opens the earbuds' service [uuid]; Windows looks up its channel, so this waits for both steps. */
    override fun open(address: String, uuid: String, timeoutMs: Int): Connection =
        connect(address, UUID.fromString(uuid), 0, timeoutMs * 2)

    override fun rfcomm(address: String, channel: Int, timeoutMs: Int): Connection {
        require(channel in 1..30) { "RFCOMM channels run from 1 to 30" }
        return connect(address, null, channel, timeoutMs)
    }

    private fun connect(address: String, service: UUID?, port: Int, timeoutMs: Int): Connection {
        val ws = winsock
        val socket = ws.socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        check(!socket.invalid) { "No Bluetooth on this computer (${Native.getLastError()})" }
        // Writes give up like on Linux; reads wait until the earbuds speak or the link closes, also like on Linux.
        val ms = ByteArray(4).also { WindowsLayout.putInt(it, 0, timeoutMs) }
        ws.setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, ms, ms.size)
        val at = WindowsLayout.sockaddr(WindowsLayout.address(address), service, port, AF_BTH)

        // Windows has no connect timeout on a blocking socket, so a watchdog closes it if connecting hangs.
        val step = AtomicInteger(CONNECTING)
        val watchdog = Thread({
            try {
                Thread.sleep(timeoutMs.toLong())
                if (step.compareAndSet(CONNECTING, GAVE_UP)) ws.closesocket(socket)
            } catch (_: InterruptedException) {
            }
        }, "blazbuds-connect").apply { isDaemon = true; start() }
        val result = ws.connect(socket, at, at.size)
        val error = if (result != 0) Native.getLastError() else 0
        val gaveUp = !step.compareAndSet(CONNECTING, DONE)
        watchdog.interrupt()
        if (gaveUp) throw LinkException("The earbuds are out of reach or in their case")
        if (result != 0) {
            ws.closesocket(socket)
            fail(error)
        }
        return Stream(socket)
    }

    private const val CONNECTING = 0
    private const val DONE = 1
    private const val GAVE_UP = 2

    private class Stream(private val socket: Socket) : Connection {
        @Volatile private var closed = false

        override fun read(into: ByteArray): Int {
            if (closed) return -1
            val count = winsock.recv(socket, into, into.size, 0)
            return if (count <= 0) -1 else count
        }

        override fun write(bytes: ByteArray) {
            var sent = 0
            while (sent < bytes.size) {
                val rest = if (sent == 0) bytes else bytes.copyOfRange(sent, bytes.size)
                val count = winsock.send(socket, rest, rest.size, 0)
                if (count <= 0) throw LinkException("The earbuds stopped listening")
                sent += count
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            // Shutting down first wakes a read that is waiting on another thread.
            winsock.shutdown(socket, SD_BOTH)
            winsock.closesocket(socket)
        }
    }

    internal fun fail(error: Int): Nothing = when (error) {
        WSAECONNREFUSED -> throw LinkException("The earbuds refused the connection; another app may be using them", busy = true)
        WSAEADDRINUSE -> throw LinkException("Another app is using the earbuds", busy = true)
        WSASERVICE_NOT_FOUND -> throw LinkException("These earbuds don't offer the control service")
        WSAETIMEDOUT, WSAEHOSTDOWN, WSAEHOSTUNREACH, WSAEINTR, WSAENOTSOCK ->
            throw LinkException("The earbuds are out of reach or in their case")
        WSAENETDOWN, WSAENETUNREACH -> throw LinkException("Bluetooth is off")
        else -> throw LinkException("Couldn't connect to the earbuds (error $error)")
    }

    /** Every pair Windows has paired or remembered, and which are connected now; no scan, so it answers at once. */
    override fun devices(): List<Device> = runCatching {
        val api = bluetooth
        val info = WindowsLayout.emptyDeviceInfo()
        val find = api.BluetoothFindFirstDevice(WindowsLayout.searchParams(Native.POINTER_SIZE), info) ?: return emptyList()
        val found = LinkedHashMap<Long, Device>()
        try {
            do {
                val device = WindowsLayout.deviceInfo(info)
                val text = WindowsLayout.address(device.address)
                found.putIfAbsent(device.address, Device(device.name.ifBlank { text }, text, device.connected))
                info.fill(0)
                WindowsLayout.putInt(info, 0, WindowsLayout.DEVICE_INFO_SIZE)
            } while (api.BluetoothFindNextDevice(find, info) != 0)
        } finally {
            api.BluetoothFindDeviceClose(find)
        }
        found.values.toList()
    }.getOrDefault(emptyList())

    /** Connected or not; Windows keeps headset batteries where only a device-manager walk reaches, so no battery here. */
    override fun info(address: String): Pair<Boolean, Int?> =
        (devices().firstOrNull { it.address.equals(address, ignoreCase = true) }?.connected ?: false) to null

    /** False when Windows finds no Bluetooth radio to use; when it can't tell, true, as on Linux. */
    override fun radioOn(): Boolean = runCatching {
        val radio = PointerByReference()
        val find = bluetooth.BluetoothFindFirstRadio(WindowsLayout.findRadioParams(), radio) ?: return false
        radio.value?.let { kernel32.CloseHandle(it) }
        bluetooth.BluetoothFindRadioClose(find)
        true
    }.getOrDefault(true)
}
