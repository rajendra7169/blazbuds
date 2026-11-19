package com.blazify.buds.core.soundcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketTest {
    @Test
    fun stateRequestIsFramedLikeTheOfficialApp() {
        // Asking for everything: command 01 01, no body, 10 bytes long.
        assertEquals("08 ee 00 00 00 01 01 0a 00 02", PacketCodec.encode(Packet(0x01, 0x01)).toHex())
    }

    @Test
    fun checksumWraps() {
        assertEquals(0x01.toByte(), PacketCodec.checksum(byteArrayOf(0xFF.toByte(), 0x02), 2))
    }

    @Test
    fun readerJoinsSplitPacketsAndSkipsNoise() {
        val one = PacketCodec.encodeFromEarbuds(Packet(0x01, 0x03, byteArrayOf(3, 4)))
        val two = PacketCodec.encodeFromEarbuds(Packet(0x06, 0x01, byteArrayOf(1)))
        val stream = byteArrayOf(0x55, 0x09) + one + two
        val reader = PacketCodec.Reader()
        val first = reader.feed(stream.copyOfRange(0, 9))
        assertTrue(first.isEmpty())
        val rest = reader.feed(stream.copyOfRange(9, stream.size))
        assertEquals(listOf("01 03", "06 01"), rest.map { Packet.commandName(it.command) })
        assertEquals("03 04", rest[0].body.toHex())
    }

    @Test
    fun readerDropsAPacketWithABadSum() {
        val good = PacketCodec.encodeFromEarbuds(Packet(0x01, 0x03, byteArrayOf(5, 5)))
        val bad = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val packets = PacketCodec.Reader().feed(bad + good)
        assertEquals(1, packets.size)
    }
}
