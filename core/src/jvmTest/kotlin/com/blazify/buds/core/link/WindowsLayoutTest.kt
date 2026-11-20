package com.blazify.buds.core.link

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Windows structures, checked against the sizes and offsets in Microsoft's headers, on any computer. */
class WindowsLayoutTest {
    @Test
    fun sockaddrBthIsPackedToThirtyBytes() {
        assertEquals(30, WindowsLayout.SOCKADDR_BTH_SIZE)
        assertEquals(2, WindowsLayout.SOCKADDR_ADDRESS)
        assertEquals(10, WindowsLayout.SOCKADDR_SERVICE)
        assertEquals(26, WindowsLayout.SOCKADDR_PORT)
        assertEquals(30, WindowsLayout.sockaddr(0, null, 1).size)
    }

    @Test
    fun searchParamsAreFortyBytesOn64BitAndThirtyTwoOn32Bit() {
        assertEquals(40, WindowsLayout.searchParamsSize(8))
        assertEquals(32, WindowsLayout.radioOffset(8))
        assertEquals(32, WindowsLayout.searchParamsSize(4))
        assertEquals(28, WindowsLayout.radioOffset(4))
        val params = WindowsLayout.searchParams(8)
        assertEquals(40, params.size)
        assertEquals(40, WindowsLayout.getInt(params, 0))
        assertEquals(1, WindowsLayout.getInt(params, 4))
        assertEquals(1, WindowsLayout.getInt(params, 8))
        assertEquals(0, WindowsLayout.getInt(params, 12))
        assertEquals(1, WindowsLayout.getInt(params, 16))
        assertEquals(0, WindowsLayout.getInt(params, 20))
        assertTrue(params.copyOfRange(24, 40).all { it == 0.toByte() }, "no scan, no timeout, every radio")
    }

    @Test
    fun deviceInfoIs560BytesWithTheNameAt64() {
        assertEquals(560, WindowsLayout.DEVICE_INFO_SIZE)
        assertEquals(560, WindowsLayout.INFO_NAME + WindowsLayout.NAME_CHARS * 2)
        // Two SYSTEMTIMEs of 16 bytes sit between the flags and the name.
        assertEquals(WindowsLayout.INFO_AUTHENTICATED + 4 + 16 + 16, WindowsLayout.INFO_NAME)
        assertEquals(4, WindowsLayout.FIND_RADIO_PARAMS_SIZE)
        assertEquals(560, WindowsLayout.getInt(WindowsLayout.emptyDeviceInfo(), 0))
    }

    @Test
    fun readsADeviceInfoWindowsFilledIn() {
        val bytes = WindowsLayout.emptyDeviceInfo()
        WindowsLayout.putLong(bytes, WindowsLayout.INFO_ADDRESS, 0xE8EECC123456L)
        WindowsLayout.putInt(bytes, WindowsLayout.INFO_CONNECTED, 1)
        WindowsLayout.putInt(bytes, WindowsLayout.INFO_AUTHENTICATED, 1)
        "soundcore Liberty 4 NC".forEachIndexed { i, c ->
            bytes[WindowsLayout.INFO_NAME + i * 2] = c.code.toByte()
            bytes[WindowsLayout.INFO_NAME + i * 2 + 1] = (c.code shr 8).toByte()
        }
        val info = WindowsLayout.deviceInfo(bytes)
        assertEquals("soundcore Liberty 4 NC", info.name)
        assertEquals("E8:EE:CC:12:34:56", WindowsLayout.address(info.address))
        assertTrue(info.connected)
        assertFalse(info.remembered)
        assertTrue(info.authenticated)
    }

    @Test
    fun namesKeepLettersBeyondAscii() {
        val bytes = WindowsLayout.emptyDeviceInfo()
        "Écouteurs 耳机".forEachIndexed { i, c ->
            bytes[WindowsLayout.INFO_NAME + i * 2] = c.code.toByte()
            bytes[WindowsLayout.INFO_NAME + i * 2 + 1] = (c.code shr 8).toByte()
        }
        assertEquals("Écouteurs 耳机", WindowsLayout.deviceInfo(bytes).name)
    }

    @Test
    fun addressesGoBothWays() {
        assertEquals(0xAABBCCDDEEFFL, WindowsLayout.address("AA:BB:CC:DD:EE:FF"))
        assertEquals(0xAABBCCDDEEFFL, WindowsLayout.address("aa:bb:cc:dd:ee:ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", WindowsLayout.address(0xAABBCCDDEEFFL))
        assertEquals("00:00:00:00:00:01", WindowsLayout.address(1L))
        val text = "E8:EE:CC:01:02:03"
        assertEquals(text, WindowsLayout.address(WindowsLayout.address(text)))
        assertFailsWith<IllegalArgumentException> { WindowsLayout.address("E8:EE:CC") }
    }

    @Test
    fun theAddressGoesLeastSignificantByteFirst() {
        val bytes = WindowsLayout.sockaddr(WindowsLayout.address("AA:BB:CC:DD:EE:FF"), null, 1)
        assertContentEquals(byteArrayOf(32, 0), bytes.copyOfRange(0, 2))
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte(), 0, 0),
            bytes.copyOfRange(2, 10),
        )
    }

    @Test
    fun aGuidKeepsItsFirstThreePartsLittleEndian() {
        // The serial port service, 00001101-0000-1000-8000-00805F9B34FB.
        assertContentEquals(
            byteArrayOf(0x01, 0x11, 0, 0, 0, 0, 0x00, 0x10, 0x80.toByte(), 0, 0, 0x80.toByte(), 0x5F, 0x9B.toByte(), 0x34, 0xFB.toByte()),
            WindowsLayout.guid("00001101-0000-1000-8000-00805F9B34FB"),
        )
        assertContentEquals(
            byteArrayOf(
                0x31, 0x2d, 0xf1.toByte(), 0x0c, 0xc3.toByte(), 0xfa.toByte(), 0x53, 0x45,
                0xbd.toByte(), 0x80.toByte(), 0xd6.toByte(), 0x83.toByte(), 0x2e, 0x7b, 0x39, 0x47,
            ),
            WindowsLayout.guid("0cf12d31-fac3-4553-bd80-d6832e7b3947"),
        )
    }

    @Test
    fun aServiceSockaddrAsksWindowsToFindTheChannel() {
        val service = java.util.UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3947")
        val bytes = WindowsLayout.sockaddr(1L, service, 0)
        assertContentEquals(WindowsLayout.guid(service), bytes.copyOfRange(10, 26))
        assertEquals(0, WindowsLayout.getInt(bytes, 26))
        val channel = WindowsLayout.sockaddr(1L, null, 16)
        assertTrue(channel.copyOfRange(10, 26).all { it == 0.toByte() })
        assertEquals(16, WindowsLayout.getInt(channel, 26))
    }
}

/** What a failed Windows connection tells the app: busy errors let it wait, the rest say why. */
class WindowsErrorsTest {
    private fun error(code: Int) = assertFailsWith<LinkException> { WindowsBluetooth.fail(code) }

    @Test
    fun refusedOrInUseMeansBusy() {
        assertTrue(error(10061).busy)
        assertTrue(error(10048).busy)
    }

    @Test
    fun aMissingServiceIsNotBusySoSoundcoreCanTryItsSerialPort() {
        val e = error(10108)
        assertFalse(e.busy)
        assertEquals("These earbuds don't offer the control service", e.message)
    }

    @Test
    fun outOfReachAndUnknownErrorsAreNotBusy() {
        listOf(10060, 10064, 10065, 10004, 10038, 10050, 1234).forEach { assertFalse(error(it).busy, "error $it") }
        assertEquals("Couldn't connect to the earbuds (error 1234)", error(1234).message)
    }

    @Test
    fun anInvalidSocketIsAllOnes() {
        assertTrue(WindowsBluetooth.Socket(-1).invalid)
        assertFalse(WindowsBluetooth.Socket(0x1A4).invalid)
    }

    @Test
    fun jnaCanReturnASocketAsWideAsAPointer() {
        // JNA builds a returned SOCKET through its no-argument constructor.
        val converter = com.sun.jna.NativeMappedConverter.getInstance(WindowsBluetooth.Socket::class.java)
        val expected = if (com.sun.jna.Native.POINTER_SIZE == 8) java.lang.Long::class.java else java.lang.Integer::class.java
        assertEquals(expected, converter.nativeType())
        val socket = converter.fromNative(-1L, null) as WindowsBluetooth.Socket
        assertTrue(socket.invalid)
    }
}
