package com.blazify.buds.ui

/**
 * When the app asks for a star on GitHub: 10 days and then 30 days after each version is first opened, at most once
 * per launch and never on a launch that offered an update. Starred, or already starred, and it never asks again.
 */
object StarPrompt {
    val ASK_DAYS = listOf(10L, 30L)
    private const val DAY = 24 * 60 * 60 * 1000L

    /** Whether an ask is due: [shown] asks of this version so far, the version first opened at [since]. */
    fun isDue(now: Long, since: Long, shown: Int): Boolean {
        val days = ASK_DAYS.getOrNull(shown) ?: return false
        return now >= since + days * DAY
    }

    /** Records this launch and says whether to ask now. */
    fun onOpened(prefs: Prefs, now: Long, version: String = VERSION): Boolean {
        if (prefs.starDone) return false
        if (prefs.starVersion != version) {
            // A new version, or the first launch: start its clock, ask nothing yet.
            prefs.startStarClock(version, now)
            return false
        }
        return isDue(now, prefs.starSince, prefs.starShown)
    }
}
