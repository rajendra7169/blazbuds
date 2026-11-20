package com.blazify.buds.core.link

import java.util.UUID

/**
 * The bytes of the Windows Bluetooth structures, laid out by hand exactly as the Windows headers do, so nothing
 * depends on how JNA would pad a structure on the computer running the tests.
 */
internal object WindowsLayout {
    /** SOCKADDR_BTH is `#pragma pack(1)`: USHORT family, ULONGLONG address, GUID service, ULONG port. */
    const val SOCKADDR_BTH_SIZE = 30
    const val SOCKADDR_FAMILY = 0
    const val SOCKADDR_ADDRESS = 2
    const val SOCKADDR_SERVICE = 10
    const val SOCKADDR_PORT = 26

    /** BLUETOOTH_DEVICE_INFO: the address union is 8-byte aligned, then the class, flags, two SYSTEMTIMEs, the name. */
    const val DEVICE_INFO_SIZE = 560
    const val INFO_ADDRESS = 8
    const val INFO_CLASS = 16
    const val INFO_CONNECTED = 20
    const val INFO_REMEMBERED = 24
    const val INFO_AUTHENTICATED = 28
    const val INFO_NAME = 64
    const val NAME_CHARS = 248

    /** BLUETOOTH_DEVICE_SEARCH_PARAMS: six DWORD/BOOLs, a UCHAR, then the radio HANDLE aligned to a pointer. */
    fun searchParamsSize(pointerSize: Int): Int = radioOffset(pointerSize) + pointerSize

    fun radioOffset(pointerSize: Int): Int = (25 + pointerSize - 1) / pointerSize * pointerSize

    /** BLUETOOTH_FIND_RADIO_PARAMS is just its own size. */
    const val FIND_RADIO_PARAMS_SIZE = 4

    /** Asks for every device Windows has paired or remembered, and the connected ones, on every radio, without a scan. */
    fun searchParams(pointerSize: Int): ByteArray {
        val size = searchParamsSize(pointerSize)
        return ByteArray(size).also {
            putInt(it, 0, size)
            putInt(it, 4, 1) // fReturnAuthenticated
            putInt(it, 8, 1) // fReturnRemembered
            putInt(it, 12, 0) // fReturnUnknown
            putInt(it, 16, 1) // fReturnConnected
            putInt(it, 20, 0) // fIssueInquiry
            // cTimeoutMultiplier 0, hRadio NULL = all radios.
        }
    }

    fun findRadioParams(): ByteArray = ByteArray(FIND_RADIO_PARAMS_SIZE).also { putInt(it, 0, FIND_RADIO_PARAMS_SIZE) }

    /** An empty BLUETOOTH_DEVICE_INFO with its dwSize set, for Windows to fill. */
    fun emptyDeviceInfo(): ByteArray = ByteArray(DEVICE_INFO_SIZE).also { putInt(it, 0, DEVICE_INFO_SIZE) }

    data class DeviceInfo(val address: Long, val name: String, val connected: Boolean, val remembered: Boolean, val authenticated: Boolean)

    fun deviceInfo(bytes: ByteArray): DeviceInfo {
        val chars = CharArray(NAME_CHARS) { i -> (getShort(bytes, INFO_NAME + i * 2).toInt() and 0xFFFF).toChar() }
        val end = chars.indexOf('\u0000').let { if (it < 0) NAME_CHARS else it }
        return DeviceInfo(
            address = getLong(bytes, INFO_ADDRESS) and 0xFFFF_FFFF_FFFFL,
            name = String(chars, 0, end),
            connected = getInt(bytes, INFO_CONNECTED) != 0,
            remembered = getInt(bytes, INFO_REMEMBERED) != 0,
            authenticated = getInt(bytes, INFO_AUTHENTICATED) != 0,
        )
    }

    /** SOCKADDR_BTH for a service UUID (port 0 makes Windows look the channel up) or a plain [port] with no service. */
    fun sockaddr(address: Long, service: UUID?, port: Int, family: Int = 32): ByteArray {
        val bytes = ByteArray(SOCKADDR_BTH_SIZE)
        bytes[SOCKADDR_FAMILY] = family.toByte()
        bytes[SOCKADDR_FAMILY + 1] = (family shr 8).toByte()
        putLong(bytes, SOCKADDR_ADDRESS, address)
        service?.let { guid(it).copyInto(bytes, SOCKADDR_SERVICE) }
        putInt(bytes, SOCKADDR_PORT, port)
        return bytes
    }

    /** A GUID as Windows keeps it: Data1, Data2 and Data3 little-endian, the last eight bytes as written. */
    fun guid(uuid: UUID): ByteArray {
        val big = ByteArray(16)
        for (i in 0 until 8) {
            big[i] = (uuid.mostSignificantBits shr (56 - 8 * i)).toByte()
            big[8 + i] = (uuid.leastSignificantBits shr (56 - 8 * i)).toByte()
        }
        return byteArrayOf(big[3], big[2], big[1], big[0], big[5], big[4], big[7], big[6]) + big.copyOfRange(8, 16)
    }

    fun guid(uuid: String): ByteArray = guid(UUID.fromString(uuid))

    /** Windows' 48-bit BTH_ADDR as "AA:BB:CC:DD:EE:FF", the most significant byte first like Linux prints it. */
    fun address(value: Long): String = (5 downTo 0).joinToString(":") { "%02X".format((value shr (8 * it)) and 0xFF) }

    fun address(text: String): Long {
        val parts = text.trim().split(":")
        require(parts.size == 6 && parts.all { it.length == 2 }) { "Not a Bluetooth address: $text" }
        return parts.fold(0L) { total, part -> (total shl 8) or part.toLong(16) }
    }

    fun putInt(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = (value shr (8 * i)).toByte()
    }

    fun putLong(bytes: ByteArray, at: Int, value: Long) {
        for (i in 0 until 8) bytes[at + i] = (value shr (8 * i)).toByte()
    }

    fun getInt(bytes: ByteArray, at: Int): Int = (0 until 4).fold(0) { v, i -> v or ((bytes[at + i].toInt() and 0xFF) shl (8 * i)) }

    fun getShort(bytes: ByteArray, at: Int): Short = ((bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)).toShort()

    fun getLong(bytes: ByteArray, at: Int): Long = (0 until 8).fold(0L) { v, i -> v or ((bytes[at + i].toLong() and 0xFF) shl (8 * i)) }
}
