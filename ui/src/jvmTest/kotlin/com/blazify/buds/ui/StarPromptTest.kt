package com.blazify.buds.ui

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarPromptTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun asksTenAndThirtyDaysInThenStopsForGood() {
        val prefs = Prefs(MapSettings())
        assertFalse(StarPrompt.onOpened(prefs, 0, "1.2.0"), "the first launch only starts the clock")
        assertFalse(StarPrompt.onOpened(prefs, 9 * day, "1.2.0"))
        assertTrue(StarPrompt.onOpened(prefs, 10 * day, "1.2.0"))
        prefs.starAsked()
        assertFalse(StarPrompt.onOpened(prefs, 20 * day, "1.2.0"))
        assertTrue(StarPrompt.onOpened(prefs, 30 * day, "1.2.0"))
        prefs.starAsked()
        assertFalse(StarPrompt.onOpened(prefs, 90 * day, "1.2.0"), "two asks per version at most")
        // A new version starts a new clock; once starred it never asks again.
        assertFalse(StarPrompt.onOpened(prefs, 91 * day, "1.3.0"))
        assertTrue(StarPrompt.onOpened(prefs, 101 * day, "1.3.0"))
        prefs.starStop()
        assertFalse(StarPrompt.onOpened(prefs, 200 * day, "1.3.0"))
    }
}
