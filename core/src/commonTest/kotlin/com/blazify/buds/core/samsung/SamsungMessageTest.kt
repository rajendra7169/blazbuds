package com.blazify.buds.core.samsung

import com.blazify.buds.core.soundcore.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real messages from Galaxy Buds, as recorded in GalaxyBudsClient's tests (GalaxyBudsClient.Tests/TestData,
 * GPL-3.0): one full status from each model it has, straight off the earbuds.
 */
object SamsungCaptures {
    val BUDS_REV3 = hex("fe 01 12 61 03 00 46 5f 01 00 00 00 00 02 01 07 00 22 01 a5 75 ee")
    val BUDS_PLUS_REV13 = hex("fd 1b 10 61 0d 00 57 54 01 00 11 65 00 00 00 03 00 22 01 04 01 04 01 00 00 00 03 00 1e 55 dd")
    val BUDS_LIVE_REV9 = hex("fd 1c 10 61 09 01 0f 61 01 00 11 64 00 00 00 22 01 00 17 01 17 01 00 00 03 00 10 00 00 02 81 dd")
    val BUDS_PRO_REV10 = hex(
        "fd 29 0c 61 0a 02 59 5e 01 01 13 64 00 01 00 22 00 00 2a 01 2a 01 01 00 03 55 00 02 01 00 00 02 " +
            "00 10 00 01 00 00 22 02 00 00 9d 24 dd",
    )
    val BUDS2_REV10 = hex(
        "fd 28 10 61 0a 03 64 64 01 01 11 33 00 03 3f 22 01 00 3c 01 3c 01 02 00 03 66 00 01 00 10 00 00 " +
            "00 00 11 02 01 01 00 00 00 be a3 dd",
    )
    val BUDS2_PRO_REV13 = hex(
        "fd 31 00 61 0d 04 64 64 01 00 33 58 00 01 bf 22 00 01 46 01 46 01 00 00 03 35 00 03 00 10 01 01 " +
            "01 01 32 03 01 01 01 00 0c ae 01 00 03 00 01 00 01 01 b6 37 dd",
    )
    val BUDS_FE_REV2 = hex(
        "fd 31 10 61 02 06 3a 59 01 00 11 00 00 00 8f 33 00 00 4a 01 4a 01 03 00 04 65 00 01 00 10 00 00 " +
            "01 01 11 03 00 00 00 00 e8 03 00 00 9d 0e 1d 00 50 00 c2 cf dd",
    )
}

class SamsungMessageTest {
    @Test
    fun readsEveryRecordedStatusWithItsChecksum() {
        val all = listOf(
            SamsungCaptures.BUDS_PLUS_REV13, SamsungCaptures.BUDS_LIVE_REV9, SamsungCaptures.BUDS_PRO_REV10,
            SamsungCaptures.BUDS2_REV10, SamsungCaptures.BUDS2_PRO_REV13, SamsungCaptures.BUDS_FE_REV2,
        )
        for (bytes in all) {
            val message = SamsungMessage.Reader(legacy = false).feed(bytes).single()
            assertEquals(SamsungStatus.EXTENDED_STATUS, message.id)
            assertEquals(bytes.size - 7, message.payload.size)
        }
        val old = SamsungMessage.Reader(legacy = true).feed(SamsungCaptures.BUDS_REV3).single()
        assertEquals(SamsungStatus.EXTENDED_STATUS, old.id)
        assertEquals(15, old.payload.size)
    }

    @Test
    fun writesAMessageTheWayTheEarbudsDo() {
        // Re-encoding a recorded message's id and payload gives back the very bytes, checksum included; only the
        // header's flag bits, which say who sent it, differ.
        val recorded = SamsungCaptures.BUDS2_PRO_REV13
        val message = SamsungMessage.Reader(legacy = false).feed(recorded).single()
        assertContentEquals(recorded, SamsungMessage.encode(message, legacy = false))
        val old = SamsungMessage.Reader(legacy = true).feed(SamsungCaptures.BUDS_REV3).single()
        val again = SamsungMessage.encode(old, legacy = true)
        assertContentEquals(SamsungCaptures.BUDS_REV3.copyOfRange(2, 22), again.copyOfRange(2, 22))
    }

    @Test
    fun checksumIsXmodem() {
        // The standard check value of CRC-16/XMODEM.
        assertEquals(0x31C3, SamsungMessage.crc16("123456789".encodeToByteArray()))
    }

    @Test
    fun findsMessagesSplitAcrossReadsAndPastNoise() {
        val reader = SamsungMessage.Reader(legacy = false)
        val bytes = hex("00 dd fd 13") + SamsungCaptures.BUDS_LIVE_REV9 + SamsungCaptures.BUDS_PLUS_REV13
        val first = reader.feed(bytes.copyOfRange(0, 20))
        assertTrue(first.isEmpty())
        val rest = reader.feed(bytes.copyOfRange(20, bytes.size))
        assertEquals(2, rest.size)
    }

    @Test
    fun skipsAMessageWithABadChecksum() {
        val broken = SamsungCaptures.BUDS_LIVE_REV9.copyOf().also { it[10] = 0x55 }
        val messages = SamsungMessage.Reader(legacy = false).feed(broken + SamsungCaptures.BUDS_PLUS_REV13)
        assertEquals(1, messages.size)
        assertEquals(0x0d, messages.single().payload[0].toInt())
    }

    @Test
    fun simpleCommandsMatchGadgetbridgesLayout() {
        // Find my earbuds on a Buds+: FD, size 3, 00, the id A0, its checksum, DD.
        val find = SamsungMessage.encode(SamsungMessage(0xA0), legacy = false)
        assertEquals(7, find.size)
        assertContentEquals(hex("fd 03 00 a0"), find.copyOfRange(0, 4))
        assertEquals(0xDD.toByte(), find.last())
        val crc = SamsungMessage.crc16(byteArrayOf(0xA0.toByte()))
        assertEquals(crc and 0xFF, find[4].toInt() and 0xFF)
        assertEquals(crc shr 8, find[5].toInt() and 0xFF)
        // The 2019 Buds put the type before the size.
        assertContentEquals(hex("fe 00 04 86 01"), SamsungMessage.encode(SamsungMessage(0x86, byteArrayOf(1)), legacy = true).copyOfRange(0, 5))
    }
}
