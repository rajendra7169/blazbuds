package com.blazify.buds.core.jieli

import kotlin.test.Test
import kotlin.test.assertEquals

/** The login as recorded between an Ultima Boom Sleek and its maker's app. */
class JieliAuthTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.text() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun answersTheWayTheEarbudsDo() {
        // The app's challenge and the earbuds' answer to it.
        assertEquals("01 02 3a 02 12 6f a2 84 87 92 36 e9 da 27 ba 0b 8d", JieliAuth.respond(hex("67 c6 69 73 51 ff 4a ec 29 cd ba ab f2 fb e3 46")).text())
    }

    @Test
    fun answersTheEarbudsChallengeTheWayTheAppDid() {
        assertEquals("01 7c 96 f6 e9 4f a3 ee 67 f9 a1 37 c0 48 80 4a 7c", JieliAuth.respond(hex("c1 8c f2 41 b3 2e 0b 03 cc ba 2c 0c 98 75 59 18")).text())
    }
}
