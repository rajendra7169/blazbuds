package com.blazify.buds.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LogbookTextTest {
    @Test
    fun namesInBytesAreLeftOut() {
        // "Redmi Note 8" among the bytes of a remembered-devices reply.
        val line = "<- Packet(0b 01, 06 02 28 01 49 8d 15 7f 16 28 52 65 64 6d 69 20 4e 6f 74 65 20 38 00 00 0"
        assertEquals("<- Packet(0b 01, 06 02 28 01 49 8d 15 7f 16 … 00 00 0", Logbook.hideText(line))
    }

    @Test
    fun ordinaryBytesStay() {
        val line = "reply 1: 09 ff 00 00 01 06 01 11 00 02 50 01 01 00 01 02 78"
        assertEquals(line, Logbook.hideText(line))
    }
}
