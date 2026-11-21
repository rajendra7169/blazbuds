package com.blazify.buds.core.eq

import com.blazify.buds.core.demo.DemoBuds
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqFitTest {
    private val soundcore = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)
    private val samsung = listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /** A curve sampled every sixth of an octave over the hearing range. */
    private fun curve(db: (Double) -> Double): List<Pair<Double, Double>> =
        (0..60).map { 20.0 * 2.0.pow(it / 6.0) }.filter { it <= 20_000 }.map { it to db(it) }

    @Test
    fun flatGivesFlat() {
        assertEquals(List(8) { 0.0 }, EqFit.fit(curve { 0.0 }, soundcore))
    }

    @Test
    fun theOverallLevelIsLeftToTheVolume() {
        // Five decibels quieter everywhere is the same shape: no band needs to move.
        EqFit.fit(curve { -5.0 }, soundcore).forEach { assertEquals(0.0, it, 0.05) }
    }

    @Test
    fun bandsAreFoundAgainFromTheirOwnCurve() {
        val gains = listOf(3.0, -2.0, 0.0, 4.0, -1.0, 2.0, -3.0, 1.0)
        val qs = EqFit.qualities(soundcore)
        val fitted = EqFit.fit(curve { EqFit.response(gains, soundcore, it, qs) }, soundcore)
        // The same shape, give or take the overall level the volume takes care of.
        val shift = fitted.zip(gains).map { (got, want) -> got - want }.average()
        fitted.zip(gains).forEach { (got, want) -> assertTrue(abs(got - want - shift) < 0.5, "$fitted against $gains") }
        assertTrue(abs(shift) < 0.5)
    }

    @Test
    fun aBandPastTheRangeIsHeldAtItsLimit() {
        // A 20 dB lift around 3.2 kHz: that band stops at +6 and the rest stay within the range.
        val fitted = EqFit.fit(curve { hz -> 20.0 * EqFit.bell(hz, 3200.0, 1.41) }, soundcore)
        assertEquals(6.0, fitted[5])
        assertTrue(fitted.all { it in -6.0..6.0 })
    }

    @Test
    fun gainsComeInTheEarbudsOwnSteps() {
        val fitted = EqFit.fit(curve { hz -> 4.0 * EqFit.bell(hz, 1000.0, 1.0) - 2.0 * EqFit.bell(hz, 8000.0, 2.0) }, samsung, step = 1.0)
        assertTrue(fitted.all { it == kotlin.math.round(it) }, "$fitted")
        assertTrue(fitted[4] >= 2.0)
    }

    @Test
    fun octaveBandsHaveTheUsualWidth() {
        EqFit.qualities(soundcore).forEach { assertEquals(1.414, it, 0.01) }
    }

    @Test
    fun graphicEqIsRead() {
        val points = EqText.graphicEq("Preamp: -2 dB\nGraphicEQ: 20 -1.5; 1000 0; 20000 2.25")
        assertEquals(listOf(20.0 to -1.5, 1000.0 to 0.0, 20000.0 to 2.25), points)
        assertEquals(-0.75, EqFit.at(points, sqrt(20.0 * 1000.0)), 1e-9)
    }

    @Test
    fun theLiberty4NcHasItsCorrection() {
        val state = DemoBuds.sample()
        val tuned = assertNotNull(Tuned.find(state))
        assertEquals("Liberty 4 NC", tuned.label)
        assertEquals(state.equalizer!!.frequencies, tuned.frequencies)
        // By its Bluetooth name too, as drivers that don't know the code report it.
        assertNotNull(Tuned.find(state.copy(model = "", name = "soundcore Liberty 4 NC")))
    }

    @Test
    fun noCorrectionWithoutAMatchingCurve() {
        val state = DemoBuds.sample()
        val eq = state.equalizer!!
        assertNull(Tuned.find(state.copy(model = "A0000", name = "Someone's earbuds")))
        assertNull(Tuned.find(state.copy(equalizer = eq.copy(custom = false))))
        assertNull(Tuned.find(state.copy(equalizer = eq.copy(frequencies = listOf(1, 2, 3)))))
        // The Liberty 4 is another model, not the Liberty 4 NC.
        assertEquals("Liberty 4", Tuned.find(state.copy(model = "A3953", name = "soundcore Liberty 4"))?.label)
    }

    @Test
    fun everyCorrectionFitsItsBandsAndRange() {
        Tuned.all.forEach { curve ->
            assertEquals(curve.frequencies.size, curve.gains.size, curve.label)
            assertTrue(curve.gains.all { it in -6.0..6.0 }, curve.label)
            assertTrue(curve.gains.any { it != 0.0 }, curve.label)
        }
    }
}
