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
    fun theEightBandSoundIsProfileCountHeadroomThenPeaks() {
        val flat = NothingCommands.advancedCurve(NothingCommands.ADVANCED_BANDS)
        assertEquals(2 + 4 + 8 * 13, flat.size)
        // Profile 0, eight bands, minus-zero headroom; the first band a peak of 0 dB at 55 Hz, width 1.
        assertContentEquals(hex("00 08 00 00 00 80 01 00 00 00 00 00 00 5c 42 00 00 80 3f"), flat.copyOfRange(0, 19))
        // The last band at 13.2 kHz.
        assertContentEquals(hex("01 00 00 00 00 00 40 4e 46 00 00 80 3f"), flat.copyOfRange(6 + 7 * 13, 6 + 8 * 13))

        val gains = listOf(3f, 0f, 0f, 0f, 0f, 0f, 0f, -2.5f)
        val shaped = NothingCommands.advancedCurve(NothingCommands.ADVANCED_BANDS.mapIndexed { i, band -> band.copy(gain = gains[i]) })
        assertContentEquals(hex("00 00 40 c0"), shaped.copyOfRange(2, 6)) // 3 dB of headroom
        assertContentEquals(hex("00 00 20 c0"), shaped.copyOfRange(6 + 7 * 13 + 1, 6 + 7 * 13 + 5)) // -2.5 dB
    }

    @Test
    fun theEightBandSoundReadsBack() {
        val bands = NothingCommands.ADVANCED_BANDS.mapIndexed { i, band -> band.copy(hz = band.hz + i, gain = i - 3f, q = 0.7f) }
        assertEquals(bands, NothingCommands.advancedCurve(NothingCommands.advancedCurve(bands)))
        // What Nothing Linux saw when no profile was kept, and an answer cut short.
        assertEquals(null, NothingCommands.advancedCurve(hex("00 00")))
        assertEquals(null, NothingCommands.advancedCurve(NothingCommands.advancedCurve(bands).copyOf(50)))
    }

    @Test
    fun theEightBandSoundStaysInsideWhatTheEarbudsTake() {
        val wild = NothingCommands.ADVANCED_BANDS.mapIndexed { i, band -> if (i == 0) NothingCommands.Band(hz = 5f, gain = 15f, q = 30f) else band }
        val first = NothingCommands.advancedCurve(NothingCommands.advancedCurve(wild))!!.first()
        assertEquals(NothingCommands.Band(hz = 20f, gain = 12f, q = 10f), first)
        assertContentEquals(hex("00 00 40 c1"), NothingCommands.advancedCurve(wild).copyOfRange(2, 6))
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
