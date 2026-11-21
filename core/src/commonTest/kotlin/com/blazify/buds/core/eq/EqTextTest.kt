package com.blazify.buds.core.eq

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqTextTest {
    private val bands = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)

    /** AutoEq's parametric file for a Liberty 4 NC, as Equalizer APO reads it. */
    private val parametric = """
        Preamp: -2.5 dB
        Filter 1: ON LSC Fc 105 Hz Gain -6.6 dB Q 0.70
        Filter 2: ON PK Fc 579 Hz Gain 2.4 dB Q 0.94
        Filter 3: ON PK Fc 105 Hz Gain -2.1 dB Q 0.87
        Filter 4: OFF PK Fc 5504 Hz Gain -20.0 dB Q 0.77
        Filter 5: ON HSC Fc 10000 Hz Gain 2.4 dB Q 0.70
    """.trimIndent()

    @Test
    fun parametricLinesAreRead() {
        val filters = EqText.filters(parametric)
        assertEquals(4, filters.size, "the filter switched off is skipped")
        assertEquals(EqText.Filter(EqText.Kind.LowShelf, 105.0, -6.6, 0.70), filters[0])
        assertEquals(EqText.Kind.HighShelf, filters[3].kind)
    }

    @Test
    fun filtersSoundAsTheirRecipesSay() {
        val peak = EqText.Filter(EqText.Kind.Peak, 1000.0, 6.0, 1.0)
        assertEquals(6.0, EqText.response(peak, 1000.0), 0.01)
        assertTrue(abs(EqText.response(peak, 50.0)) < 0.2)
        val low = EqText.Filter(EqText.Kind.LowShelf, 200.0, -6.0, 0.71)
        assertEquals(-6.0, EqText.response(low, 20.0), 0.2)
        assertTrue(abs(EqText.response(low, 10_000.0)) < 0.1)
        val high = EqText.Filter(EqText.Kind.HighShelf, 5000.0, 4.0, 0.71)
        assertEquals(4.0, EqText.response(high, 20_000.0), 0.4)
    }

    @Test
    fun parametricBecomesACurveToFit() {
        val curve = EqText.curve(parametric)
        assertTrue(curve.size > 100)
        val gains = EqFit.fit(curve, bands, step = 0.5)
        // Less bass than the middle, as the low shelf says.
        assertTrue(gains[0] < gains[3] - 2, "$gains")
    }

    @Test
    fun anExportReadsBackAsTheSameSound() {
        val gains = listOf(3.0, 1.5, 0.0, -1.0, 0.5, 2.0, -2.0, 1.0)
        val text = EqText.export("Late night", gains, bands)
        assertTrue(text.startsWith("# Blazbuds sound \"Late night\"\nPreamp: -"), text)
        assertEquals("Late night", EqText.name(text))
        val back = EqFit.fit(EqText.curve(text), bands, step = 0.5)
        val shift = back.zip(gains).map { (a, b) -> a - b }.average()
        back.zip(gains).forEach { (a, b) -> assertTrue(abs(a - b - shift) <= 0.75, "$back against $gains") }
    }

    @Test
    fun otherTextIsNoCurve() {
        assertTrue(EqText.curve("hello there").isEmpty())
        assertTrue(EqText.curve("GraphicEQ: 20 x; y 3").isEmpty())
        assertNull(EqText.name("GraphicEQ: 20 0; 20000 0"))
    }
}
