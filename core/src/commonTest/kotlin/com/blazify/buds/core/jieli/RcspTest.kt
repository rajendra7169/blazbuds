package com.blazify.buds.core.jieli

import kotlin.test.Test
import kotlin.test.assertEquals

/** Messages as recorded between an Ultima Boom Sleek and its maker's app. */
class RcspTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.text() = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun choosingRockIsTheSameMessageTheAppSends() {
        val rock = Rcsp(Rcsp.COMMAND, 0x08, hex("36 ff 0c 04 01 00 00 00 00 00 00 00 00 00 00"))
        assertEquals("fe dc ba c0 08 00 0f 36 ff 0c 04 01 00 00 00 00 00 00 00 00 00 00 ef", Rcsp.encode(rock).text())
    }

    @Test
    fun readsAnswersAndNewsSplitAcrossReads() {
        val bytes = hex("fe dc ba 00 08 00 02 00 36 ef fe dc ba 80 09 00 0f 07 ff 0c 04 01 fe 00 02 04 fe fe 00 00 04 04 ef")
        val reader = Rcsp.Reader()
        val first = reader.feed(bytes.copyOfRange(0, 12), 12)
        val rest = reader.feed(bytes.copyOfRange(12, bytes.size), bytes.size - 12)
        assertEquals(1, first.size)
        assertEquals(true, first[0].isAnswer)
        assertEquals(1, rest.size)
        assertEquals(0x09, rest[0].opcode)
        assertEquals(true, rest[0].isNews)
    }
}
