package com.blazify.buds.ui

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatePromptTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun showsOnceThenAWeekAfterLaterThenNever() {
        val prefs = Prefs(MapSettings())
        fun offered(now: Long) = UpdatePrompt.shouldOffer("2.0.0", prefs.declinedUpdate, prefs.declinedAt, prefs.remindedUpdate, now)
        assertTrue(offered(0))
        prefs.putOffUpdate("2.0.0", 0)
        assertFalse(offered(3 * day))
        assertTrue(offered(7 * day))
        prefs.putOffUpdate("2.0.0", 7 * day)
        assertFalse(offered(30 * day))
        // A newer version starts afresh.
        assertTrue(UpdatePrompt.shouldOffer("2.1.0", prefs.declinedUpdate, prefs.declinedAt, prefs.remindedUpdate, 30 * day))
    }
}
