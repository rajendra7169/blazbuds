package com.blazify.buds.core.link

import com.blazify.buds.core.soundcore.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdpTest {
    @Test
    fun findsTheChannelInARealReply() {
        // A Liberty 4 NC's answer for its control service.
        val reply = hex("07 00 01 00 18 00 15 35 13 35 11 09 00 04 35 0c 35 03 19 01 00 35 05 19 00 03 08 07 00")
        assertEquals(7, Sdp.channel(reply))
    }

    @Test
    fun noServiceMeansNoChannel() {
        assertNull(Sdp.channel(hex("07 00 01 00 05 00 02 35 00 00")))
    }

    @Test
    fun requestAsksForTheProtocolList() {
        val request = Sdp.request(Sdp.uuid("0cf12d31-fac3-4553-bd80-d6832e7b3947"))
        assertEquals(5 + 27, request.size)
        assertEquals(0x06, request[0].toInt())
    }
}
