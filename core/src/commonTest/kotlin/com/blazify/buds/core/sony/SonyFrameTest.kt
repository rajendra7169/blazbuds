package com.blazify.buds.core.sony

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sony's message format: the acknowledgements Sony's app holds as constants, checksums and escaping. */
class SonyFrameTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun acknowledgementsAreTheOnesSonysAppSends() {
        assertEquals("3e 01 01 00 00 00 00 02 3c", SonyFrame.hex(SonyFrame.ack(0)))
        assertEquals("3e 01 00 00 00 00 00 01 3c", SonyFrame.hex(SonyFrame.ack(1)))
    }

    @Test
    fun theFirstQuestionIsFramedWithItsChecksum() {
        val frame = SonyFrame.encode(SonyFrame.TABLE1, 0, Sony.protocolInfo())
        assertEquals("3e 0c 00 00 00 00 02 00 00 0e 3c", SonyFrame.hex(frame))
        val back = SonyFrame.decode(frame)!!
        assertEquals(SonyFrame.TABLE1, back.kind)
        assertEquals(0, back.seq)
        assertEquals("00 00", SonyFrame.hex(back.payload))
    }

    @Test
    fun markerBytesAreEscapedBothWays() {
        // A payload holding all three marker bytes, and a checksum that comes out as 3C itself.
        val frame = SonyFrame.encode(SonyFrame.TABLE1, 1, hex("58 00 3c 3d 3e"))
        assertEquals("3e 0c 01 00 00 00 05 58 00 3d 2c 3d 2d 3d 2e", SonyFrame.hex(frame).substring(0, 44))
        assertEquals("58 00 3c 3d 3e", SonyFrame.hex(SonyFrame.decode(frame)!!.payload))
        val checksum = SonyFrame.encode(SonyFrame.TABLE1, 0, hex("2f"))
        assertEquals("3e 0c 00 00 00 00 01 2f 3d 2c 3c", SonyFrame.hex(checksum))
        assertEquals("2f", SonyFrame.hex(SonyFrame.decode(checksum)!!.payload))
    }

    @Test
    fun aWrongChecksumIsDropped() {
        assertNull(SonyFrame.decode(hex("3e 0c 00 00 00 00 02 00 00 0f 3c")))
    }

    @Test
    fun readsFramesSplitAcrossReadsAndSkipsBrokenOnes() {
        // The WF-1000XM5's case battery and its noise control news, with a broken frame between them.
        val bytes = SonyFrame.encode(SonyFrame.TABLE1, 1, hex("23 0a 28 00 1e")) +
            hex("3e 0c 00 00 00 00 01 00 99 3c") +
            SonyFrame.encode(SonyFrame.TABLE1, 0, hex("69 17 01 01 00 01 0a"))
        val reader = SonyFrame.Reader()
        val first = reader.feed(bytes.copyOfRange(0, 9), 9)
        val rest = reader.feed(bytes.copyOfRange(9, bytes.size), bytes.size - 9)
        assertEquals(0, first.size)
        assertEquals(listOf("23 0a 28 00 1e", "69 17 01 01 00 01 0a"), rest.map { SonyFrame.hex(it.payload) })
        assertEquals(listOf(1, 0), rest.map { it.seq })
    }
}
