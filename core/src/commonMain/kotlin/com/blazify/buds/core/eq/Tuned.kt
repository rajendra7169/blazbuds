package com.blazify.buds.core.eq

import com.blazify.buds.core.model.BudsState
import kotlin.math.abs

/**
 * A correction for one model, fitted to one layout of its bands: what makes that model sound even, from lab
 * measurements of it. [keys] are the names the earbuds go by (the model code a driver reports, or the model's name),
 * [file] the measurement it came from in tools/autoeq/measurements, [step] the band step the gains were rounded to.
 */
data class TunedCurve(
    val label: String,
    val keys: List<String>,
    val file: String,
    val frequencies: List<Int>,
    val step: Double,
    val gains: List<Double>,
)

/** "Tuned for your earbuds": a built-in correction, when the app has one for these earbuds and their bands. */
object Tuned {
    val all: List<TunedCurve> get() = TUNED_TABLE

    /**
     * The correction for these earbuds, or null: only for a model with a measurement, an equalizer that takes a curve,
     * and the bands it was fitted to.
     */
    fun find(state: BudsState): TunedCurve? {
        val eq = state.equalizer ?: return null
        if (!eq.custom || eq.frequencies.isEmpty()) return null
        return TUNED_TABLE.firstOrNull { curve -> curve.frequencies == eq.frequencies && curve.keys.any { matches(state, it) } }
    }

    /** Whether [gains] is this correction, to a tenth of a decibel. */
    fun isPlaying(curve: TunedCurve, gains: List<Double>): Boolean =
        gains.size == curve.gains.size && gains.indices.all { abs(gains[it] - curve.gains[it]) < 0.06 }

    private fun matches(state: BudsState, key: String): Boolean =
        state.model.equals(key, ignoreCase = true) || bare(state.name) == bare(key) || bare(state.model) == bare(key)

    /** A name without the brand in front and with the spacing tidied: "soundcore Liberty 4 NC" reads as "liberty 4 nc". */
    private fun bare(name: String): String {
        var s = name.trim().lowercase().replace(Regex("\\s+"), " ")
        for (brand in BRANDS) if (s.startsWith("$brand ")) s = s.removePrefix("$brand ")
        return s
    }

    private val BRANDS = listOf("anker", "soundcore", "samsung", "galaxy", "sony", "nothing", "oneplus")
}
