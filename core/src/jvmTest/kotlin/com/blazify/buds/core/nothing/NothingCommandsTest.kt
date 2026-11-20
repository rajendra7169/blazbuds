package com.blazify.buds.core.nothing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The bytes of Nothing's sound settings, against what nada, ear (web), BudsLink and Nothing Linux send. */
class NothingCommandsTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val ear = NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 1.0f)

    @Test
    fun theThreeBandSoundIsMidTrebleBassWithHeadroom() {
        assertContentEquals(
            hex(
                "03 00 00 40 c0" + // three filters, 3 dB of headroom for the 3 dB treble
                    " 01 00 00 80 bf 00 00 75 44 33 33 33 3f" + // mid: -1 dB at 980 Hz, width 0.7
                    " 02 00 00 40 40 00 80 54 45 00 00 80 3f" + // treble: +3 dB from 3400 Hz, width 1
                    " 00 00 00 00 c0 00 00 0c 43 cd cc 4c 3f" + // bass: -2 dB below 140 Hz, width 0.8
                    " 00 00 00 00 00 00 00 00 00",
            ),
            NothingCommands.curve(bass = -2.0, mid = -1.0, treble = 3.0, shape = ear),
        )
    }

    @Test
    fun nothingBoostedSendsMinusZeroHeadroomLikeNothingsApp() {
        val payload = NothingCommands.curve(bass = -3.0, mid = 0.0, treble = -1.0, shape = ear)
        assertContentEquals(hex("00 00 00 80"), payload.copyOfRange(1, 5))
    }

    @Test
    fun theThreeBandSoundReadsBackAsBassMidTreble() {
        val payload = NothingCommands.curve(bass = 4.0, mid = -2.0, treble = 1.0, shape = ear)
        assertEquals(listOf(4.0, -2.0, 1.0), NothingCommands.curve(payload))
        assertEquals(null, NothingCommands.curve(hex("00")))
    }

    @Test
    fun anEightBandSoundBecomesBassMidAndTreble() {
        // 100 and 200 Hz are the bass, 400 to 1600 Hz the mid, 3.2 to 12.8 kHz the treble.
        assertEquals(listOf(4.0, 1.0, -2.0), NothingCommands.threeBands(listOf(5.0, 3.0, 0.0, 1.0, 2.0, -1.0, -2.0, -3.0)))
        assertEquals(listOf(1.0, 2.0, 3.0), NothingCommands.threeBands(listOf(1.0, 2.0, 3.0)))
        assertEquals(listOf(0.0, 0.0, 0.0), NothingCommands.threeBands(emptyList()))
    }

    @Test
    fun aCurveIsReadBetweenItsBands() {
        val eight = listOf(0.0, 2.0, 4.0, 6.0, 6.0, 6.0, 6.0, 6.0)
        assertEquals(0.0, NothingCommands.gainAt(eight, 55.0))
        assertEquals(1.0, NothingCommands.gainAt(eight, 100.0 * kotlin.math.sqrt(2.0)), 1e-9)
        assertEquals(4.0, NothingCommands.gainAt(eight, 400.0), 1e-9)
        assertEquals(6.0, NothingCommands.gainAt(eight, 13_200.0))
    }
}
