package com.blazify.buds.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LogbookTest {
    @Test
    fun hidesAllButTheEndOfDeviceAddresses() {
        assertEquals("host XX:XX:XX:XX:22:FF connected", Logbook.hideAddresses("host A4:C1:38:0B:22:FF connected"))
        assertEquals("XX:XX:XX:XX:97:ee", Logbook.hideAddresses("88-0e-85-b5-97:ee").let { it })
        // Ordinary packet hex, without colons, is left alone.
        assertEquals("<- 08 ee 00 00 00 01 05", Logbook.hideAddresses("<- 08 ee 00 00 00 01 05"))
    }
}
