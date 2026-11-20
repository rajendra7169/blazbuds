package com.blazify.buds.ui

import com.blazify.buds.core.model.BudsState
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** One look at the batteries: when, and each level in percent, or -1 where it wasn't known. */
data class BatterySample(val at: Long, val left: Int, val right: Int, val case: Int)

/**
 * The batteries over time, and the low-battery alert. Every look at a pair adds to its history (at most every ten
 * minutes unless a level changed, kept for a week). When an earbud falls to the level the person chose, it says so
 * once, and not again until it has been charged.
 */
@OptIn(ExperimentalTime::class)
class BatteryLog(private val store: Settings = Settings()) {
    var alertOn: Boolean
        get() = store.getBoolean(ALERT_ON, true)
        set(value) = store.putBoolean(ALERT_ON, value)

    /** The level, in percent, at or below which the alert comes: 10 to 40. */
    var alertAt: Int
        get() = store.getInt(ALERT_AT, 20).coerceIn(MIN, MAX)
        set(value) = store.putInt(ALERT_AT, value.coerceIn(MIN, MAX))

    fun history(earbuds: String): List<BatterySample> =
        store.getString(HISTORY + earbuds, "").lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 4) null else BatterySample(p[0].toLongOrNull() ?: return@mapNotNull null, p[1].toIntOrNull() ?: -1, p[2].toIntOrNull() ?: -1, p[3].toIntOrNull() ?: -1)
        }.toList()

    /**
     * Notes the levels in [state] and returns an alert to show, if one is due: the words for it, or null.
     */
    fun look(state: BudsState): String? {
        val left = state.left?.percent ?: state.overall?.percent ?: -1
        val right = state.right?.percent ?: -1
        val case = state.case?.takeIf { it.seenAt == null }?.percent ?: -1
        if (left < 0 && right < 0) return null
        val now = Clock.System.now().epochSeconds
        val key = HISTORY + state.name
        val past = history(state.name)
        val last = past.lastOrNull()
        if (last == null || now - last.at >= 600 || last.left != left || last.right != right || (case >= 0 && last.case != case)) {
            val kept = (past + BatterySample(now, left, right, case)).filter { now - it.at <= WEEK }
            store.putString(key, kept.joinToString("\n") { "${it.at},${it.left},${it.right},${it.case}" })
        }
        return alert(state)
    }

    private fun alert(state: BudsState): String? {
        val sent = ALERTED + state.name
        val buds = listOfNotNull(state.left?.let { "Left" to it }, state.right?.let { "Right" to it }).ifEmpty { listOfNotNull(state.overall?.let { "Earbuds" to it }) }
        if (buds.isEmpty()) return null
        // Charging, or well above the line again: ready to say so next time.
        if (buds.any { it.second.charging } || buds.all { it.second.percent > alertAt + 5 }) {
            store.putBoolean(sent, false)
            return null
        }
        if (!alertOn || store.getBoolean(sent, false)) return null
        val low = buds.filter { it.second.percent <= alertAt }
        if (low.isEmpty()) return null
        store.putBoolean(sent, true)
        return when {
            low.size == 2 -> "Both earbuds are at ${low.maxOf { it.second.percent }}%. Time to put them in the case."
            low[0].first == "Earbuds" -> "${state.name} is at ${low[0].second.percent}%. Time to charge."
            else -> "${low[0].first} earbud is at ${low[0].second.percent}%. Time to put it in the case."
        }
    }

    companion object {
        const val MIN = 10
        const val MAX = 40
        private const val ALERT_ON = "battery_alert_on"
        private const val ALERT_AT = "battery_alert_at"
        private const val HISTORY = "battery_history_"
        private const val ALERTED = "battery_alerted_"
        private const val WEEK = 7 * 24 * 3600L
    }
}
