package com.blazify.buds.core.link

import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.PacketCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs only with BUDS_ADDRESS set to a connected Liberty 4 NC; it reads and changes nothing. */
class RealEarbudsTest {
    @Test
    fun readsTheStateFromRealEarbuds() {
        val address = System.getenv("BUDS_ADDRESS") ?: return
        val link = LinuxBluetooth.open(address, "0cf12d31-fac3-4553-bd80-d6832e7b3947")
        try {
            link.write(PacketCodec.encode(Packet(0x01, 0x01)))
            val reader = PacketCodec.Reader()
            val buffer = ByteArray(1024)
            var state: Packet? = null
            while (state == null) {
                val count = link.read(buffer)
                assertTrue(count > 0, "the link closed")
                state = reader.feed(buffer, count).firstOrNull { it.command == Packet.command(0x01, 0x01) }
            }
            println("state: $state")
            assertEquals(159, state.body.size)
        } finally {
            link.close()
        }
    }
}
