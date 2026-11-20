package com.blazify.buds.core.huawei

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Checked against an Honor Choice Earbuds X7e Active (ORF-ME00, firmware V0.0.5.1). */
class HuaweiPacketTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun askingForTheBatteryMatchesWhatTheEarbudsAnswer() {
        val ask = HuaweiPacket.encode(HuaweiPacket.ask(0x01, 0x08, 1, 2, 3))
        assertContentEquals(hex("5a 00 09 00 01 08 01 00 02 00 03 00 fb b9"), ask)
    }

    @Test
    fun readsTheBatteryAnswerAndThePushAfterIt() {
        val bytes = hex("5a 00 10 00 01 08 01 01 2d 02 03 5a 5f 2d 03 03 00 00 00 96 9f 5a 00 10 00 01 27 01 01 2d 02 03 5a 5f 2d 03 03 00 00 00 90 3d")
        val packets = HuaweiPacket.Reader().feed(bytes, bytes.size)
        assertEquals(2, packets.size)
        assertEquals(0x0108, packets[0].id)
        assertContentEquals(hex("5a 5f 2d"), packets[0].field(2)) // left 90 %, right 95 %, case 45 %
        assertEquals(0x0127, packets[1].id)
    }

    @Test
    fun skipsWhatIsntThisProtocol() {
        val noise = hex("7f 80 80 80 a8 81 81 02 83 a0 58 c3 d0 84")
        assertEquals(0, HuaweiPacket.Reader().feed(noise, noise.size).size)
    }
}
