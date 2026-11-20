package com.blazify.buds.ui

/**
 * When the "new version" popup shows, so it is seen without nagging:
 * - the first time the app opens after a version comes out;
 * - "Later" puts it off for a week; then it shows once more, and that version is never offered in a popup again.
 * The card in Settings stays until the update is in.
 */
object UpdatePrompt {
    const val REMIND_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    fun shouldOffer(version: String, declined: String?, declinedAt: Long?, reminded: String?, now: Long): Boolean {
        if (declined != version) return true
        if (reminded == version) return false
        return declinedAt != null && now >= declinedAt + REMIND_AFTER_MS
    }

    /** Whether this offer is the week-later reminder rather than the first. */
    fun isReminder(version: String, declined: String?) = declined == version
}
