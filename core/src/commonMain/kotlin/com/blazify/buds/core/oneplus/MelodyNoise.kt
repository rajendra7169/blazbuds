package com.blazify.buds.core.oneplus

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.ListeningMode

/**
 * One model's noise modes as the app shows them. Each mode is set by its own bit; the earbuds report back a bit of
 * the same tree, sometimes a child of the mode that was set (Buds 4: off is set as bit 0 and reported as bit 3).
 * A wrong bit sets another mode without a word, so a model with no tree gets no noise control at all.
 *
 * The levels (light, medium, strong) show as strength steps; smart, where the earbuds pick the level, shows as
 * Adaptive.
 */
class MelodyNoise(private val modes: List<MelodyModel.Mode>) {
    private fun top(kind: Int) = modes.firstOrNull { it.kind == kind }?.bit
    private fun any(kind: Int) = modes.flatMap { listOf(it) + it.children }.firstOrNull { it.kind == kind }?.bit

    val off: Int? = top(OFF)
    val transparency: Int? = top(TRANSPARENCY)

    /** Noise cancelling at the last level used. */
    val cancel: Int? = top(CANCEL)

    /** The levels these earbuds have, light to strong, as bits. */
    val levels: List<Int> = listOf(LIGHT, MEDIUM, STRONG).mapNotNull { any(it) }
    val smart: Int? = any(SMART) ?: top(ADAPTIVE)

    /** Bit to kind, for reading a report: each top mode, then its children, which win where they share a bit. */
    private val report: Map<Int, Int> = buildMap {
        for (mode in modes) {
            put(mode.bit, if (mode.kind == VOICE) TRANSPARENCY else mode.kind)
            for (child in mode.children) put(child.bit, if (child.kind == VOICE) TRANSPARENCY else child.kind)
        }
    }

    val isEmpty: Boolean get() = off == null && transparency == null && cancel == null && levels.isEmpty() && smart == null

    val listening: List<ListeningMode> = buildList {
        if (off != null) add(ListeningMode.Normal)
        if (transparency != null) add(ListeningMode.Transparency)
        if (cancel != null || levels.isNotEmpty() || smart != null) add(ListeningMode.NoiseCancelling)
    }

    val styles: List<CancelStyle> = buildList {
        if (smart != null) add(CancelStyle.Adaptive)
        if (levels.isNotEmpty()) add(CancelStyle.Manual)
    }

    /**
     * Whether the hold's cycle can be shown: only where the top modes are exactly off, transparency and noise
     * cancelling (an adaptive mode beside them is kept as it is).
     */
    val cycles: Boolean = off != null && transparency != null && cancel != null &&
        modes.all { it.kind in setOf(OFF, TRANSPARENCY, CANCEL, ADAPTIVE) }

    /** What a reported value means: the mode, the way of cancelling and the level's place in [levels]. */
    data class Reading(val mode: ListeningMode, val style: CancelStyle, val level: Int?)

    fun read(raw: Int): Reading? {
        if (raw <= 0) return null
        val kind = report[raw.countTrailingZeroBits()] ?: return null
        return when (kind) {
            OFF -> Reading(ListeningMode.Normal, CancelStyle.Manual, null)
            TRANSPARENCY -> Reading(ListeningMode.Transparency, CancelStyle.Manual, null)
            SMART, ADAPTIVE -> Reading(ListeningMode.NoiseCancelling, CancelStyle.Adaptive, null)
            LIGHT, MEDIUM, STRONG -> Reading(ListeningMode.NoiseCancelling, CancelStyle.Manual, levels.indexOf(any(kind)).takeIf { it >= 0 })
            CANCEL -> Reading(ListeningMode.NoiseCancelling, CancelStyle.Manual, null)
            else -> null
        }
    }

    /** The bit to set a mode; for noise cancelling alone, the plain mode, else the last level, else smart. */
    fun bitFor(mode: ListeningMode, lastLevel: Int?): Int? = when (mode) {
        ListeningMode.Normal -> off
        ListeningMode.Transparency -> transparency
        ListeningMode.NoiseCancelling -> cancel ?: lastLevel?.let { levels.getOrNull(it) } ?: smart ?: levels.firstOrNull()
    }

    fun bitFor(style: CancelStyle, step: Int?, lastLevel: Int?): Int? = when (style) {
        CancelStyle.Adaptive -> smart
        CancelStyle.Manual -> levels.getOrNull(step?.let { levelAt(it) } ?: lastLevel ?: levels.lastIndex)
        CancelStyle.Travel -> null
    }

    /** A level's place shown as a step from 1 to 5, spread out: three levels show as 1, 3 and 5. */
    fun stepOf(level: Int): Int = if (levels.size <= 1) 3 else 1 + level * 4 / (levels.size - 1)

    /** The level nearest a step from 1 to 5. */
    fun levelAt(step: Int): Int = if (levels.size <= 1) 0 else ((step.coerceIn(1, 5) - 1) * (levels.size - 1) + 2) / 4

    /** The bits the hold's cycle uses for each mode. */
    fun cycleBits(): Map<ListeningMode, Int> = if (!cycles) emptyMap() else mapOf(
        ListeningMode.Normal to off!!,
        ListeningMode.Transparency to transparency!!,
        ListeningMode.NoiseCancelling to cancel!!,
    )

    companion object {
        const val OFF = 1
        const val TRANSPARENCY = 2
        const val LIGHT = 3
        const val STRONG = 4
        const val CANCEL = 5
        const val VOICE = 6
        const val SMART = 7
        const val MEDIUM = 8
        const val ADAPTIVE = 10
    }
}
