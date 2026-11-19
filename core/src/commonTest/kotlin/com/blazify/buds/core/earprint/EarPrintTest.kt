package com.blazify.buds.core.earprint

import com.blazify.buds.core.model.Ear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarPrintTest {
    @Test
    fun hearingTestFindsTheQuietestHeardBeep() {
        val test = HearingTest(listOf(1_000))
        // Heard at -40 and -50, not at -60: -50 is kept for the left ear.
        test.answer(true); test.answer(true); test.answer(false)
        assertEquals(-50.0, test.thresholds(Ear.Left)[1_000])
        // Right ear: not heard at -40, heard at -35, then not at -45.
        test.answer(false); test.answer(true); test.answer(false)
        assertEquals(-35.0, test.thresholds(Ear.Right)[1_000])
        assertTrue(test.done)
    }

    @Test
    fun anEarThatNeedsTrebleLouderGetsTrebleLifted() {
        val flat = EarPrint.TEST_PITCHES.associateWith { -60.0 }
        assertTrue(EarPrint.curve(flat).all { it == 0.0 })
        val weakTreble = flat + mapOf(4_000 to -45.0, 8_000 to -40.0)
        val curve = EarPrint.curve(weakTreble)
        assertTrue(curve.last() > 3.0, "treble lifted: $curve")
        assertTrue(curve.first() == 0.0, "bass unchanged: $curve")
    }

    @Test
    fun tasteTestKeepsWhatIsChosen() {
        val taste = TasteTest(List(8) { 0.0 })
        taste.choose(true)
        assertEquals(2.0, taste.sound.first())
        repeat(5) { taste.choose(null) }
        assertTrue(taste.done)
        assertEquals(2.0, taste.sound.first())
    }
}
