package com.blazify.buds.core.earprint

import com.blazify.buds.core.model.Ear
import kotlin.math.abs

/**
 * EarPrint: a sound shaped to one person's ears. A hearing test finds the quietest beep each ear hears at six pitches;
 * where an ear needs it louder, that ear's sound is lifted a little there. A short taste test then tunes the result to
 * what the person prefers. Not a medical hearing test: it personalises sound.
 */
object EarPrint {
    /** The pitches tested, in hertz. */
    val TEST_PITCHES = listOf(250, 500, 1_000, 2_000, 4_000, 8_000)

    /** The earbuds' eight bands, in hertz. */
    val BANDS = listOf(100, 200, 400, 800, 1_600, 3_200, 6_400, 12_800)

    /** Loudest and quietest beep, in decibels below full scale. */
    const val LOUDEST = -20.0
    const val QUIETEST = -75.0

    /**
     * The curve for one ear from its quietest heard beeps (in dB below full scale; higher means it needed louder).
     * Each pitch is compared with that ear's own middle, so a quiet room or a loud phone matters less than the shape,
     * and lifted by about a third of the difference, never more than 6 dB. Ages past 45 get a little more treble, where
     * hearing usually fades first.
     */
    fun curve(thresholds: Map<Int, Double>, age: AgeRange = AgeRange.Under30): List<Double> {
        val known = TEST_PITCHES.filter { it in thresholds }
        if (known.isEmpty()) return List(BANDS.size) { 0.0 }
        val middle = known.map { thresholds.getValue(it) }.sorted().let { it[it.size / 2] }
        val lift = known.associateWith { ((thresholds.getValue(it) - middle) * 0.35).coerceIn(-3.0, 6.0) }
        return BANDS.map { hz ->
            val base = interpolate(lift, hz)
            val ageLift = if (hz >= 3_200) age.trebleLift * (if (hz >= 6_400) 1.0 else 0.6) else 0.0
            round1((base + ageLift).coerceIn(-6.0, 6.0))
        }
    }

    /** A value at [hz] from values at the tested pitches, straight between neighbours on a log scale. */
    private fun interpolate(at: Map<Int, Double>, hz: Int): Double {
        val keys = at.keys.sorted()
        if (hz <= keys.first()) return at.getValue(keys.first())
        if (hz >= keys.last()) return at.getValue(keys.last())
        val upper = keys.first { it >= hz }
        val lower = keys.last { it <= hz }
        if (upper == lower) return at.getValue(upper)
        val t = (kotlin.math.ln(hz.toDouble()) - kotlin.math.ln(lower.toDouble())) / (kotlin.math.ln(upper.toDouble()) - kotlin.math.ln(lower.toDouble()))
        return at.getValue(lower) + t * (at.getValue(upper) - at.getValue(lower))
    }

    fun round1(x: Double) = kotlin.math.round(x * 10) / 10.0

    /** Adds a change to a curve, kept within ±6 dB. */
    fun add(curve: List<Double>, tweak: List<Double>) = curve.zip(tweak) { a, b -> round1((a + b).coerceIn(-6.0, 6.0)) }

    fun same(a: List<Double>, b: List<Double>) = a.size == b.size && a.indices.all { abs(a[it] - b[it]) < 0.05 }
}

enum class AgeRange(val label: String, val trebleLift: Double) {
    Under30("Under 30", 0.0),
    From30("30–44", 0.5),
    From45("45–59", 1.5),
    From60("60 or over", 2.5),
}

/**
 * The hearing test as steps: each ear, each pitch, beeps that start clear and get quieter while they are heard. The
 * first beep not heard ends that pitch, and the quietest one heard is kept; one heard nowhere near the start is played
 * louder until it is. About three answers a pitch.
 */
class HearingTest(private val pitches: List<Int> = EarPrint.TEST_PITCHES) {
    data class Step(val ear: Ear, val hz: Int, val level: Double, val number: Int, val total: Int)

    private val ears = listOf(Ear.Left, Ear.Right)
    private var earIndex = 0
    private var pitchIndex = 0
    private var level = START
    private var heardAny = false
    private var quietestHeard = EarPrint.LOUDEST
    private var answers = 0

    /** Answers given on the pitch being tested now. */
    var answersOnPitch = 0
        private set

    /** How many pitches of the ear being tested are done. */
    val pitchesDone: Int get() = pitchIndex

    val pitchCount: Int get() = pitches.size
    private val results = mutableMapOf<Ear, MutableMap<Int, Double>>()

    val done: Boolean get() = earIndex >= ears.size

    /** Roughly how far along, from 0 to 1, by pitches finished. */
    val progress: Float get() = ((earIndex * pitches.size + pitchIndex).toFloat() / (ears.size * pitches.size)).coerceIn(0f, 1f)

    fun current(): Step? = if (done) null else Step(ears[earIndex], pitches[pitchIndex], level, earIndex * pitches.size + pitchIndex + 1, ears.size * pitches.size)

    fun thresholds(ear: Ear): Map<Int, Double> = results[ear].orEmpty()

    /** The person's answer to the beep just played. */
    fun answer(heard: Boolean) {
        if (done) return
        answers++
        answersOnPitch++
        if (heard) {
            heardAny = true
            quietestHeard = level
            val next = level - STEP_DOWN
            if (next < EarPrint.QUIETEST) finishPitch(level) else level = next
        } else if (heardAny) {
            finishPitch(quietestHeard)
        } else {
            val next = level + STEP_UP
            if (next > EarPrint.LOUDEST) finishPitch(EarPrint.LOUDEST) else level = next
        }
    }

    private fun finishPitch(threshold: Double) {
        results.getOrPut(ears[earIndex]) { mutableMapOf() }[pitches[pitchIndex]] = threshold
        heardAny = false
        answersOnPitch = 0
        level = START
        pitchIndex++
        if (pitchIndex >= pitches.size) {
            pitchIndex = 0
            earIndex++
        }
    }

    companion object {
        const val START = -40.0
        const val STEP_DOWN = 10.0
        const val STEP_UP = 5.0
    }
}

/**
 * The taste test: six rounds of "this or that". Each round offers the sound so far against it with one change; the
 * one chosen carries on. "No difference" keeps the sound as it is.
 */
class TasteTest(start: List<Double>) {
    data class Round(val number: Int, val total: Int, val name: String, val a: List<Double>, val b: List<Double>)

    private val tweaks = listOf(
        "More bass" to listOf(2.0, 1.5, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0),
        "Brighter" to listOf(0.0, 0.0, 0.0, 0.0, 0.5, 1.5, 2.0, 2.0),
        "Clearer voices" to listOf(0.0, -0.5, 0.0, 1.0, 2.0, 1.0, 0.0, 0.0),
        "Less bass" to listOf(-1.5, -1.0, -0.5, 0.0, 0.0, 0.0, 0.0, 0.0),
        "Softer treble" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -1.5, -1.5),
        "More air" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5),
    )
    private var index = 0
    var sound: List<Double> = start
        private set

    val done: Boolean get() = index >= tweaks.size

    fun current(): Round? = if (done) null else Round(index + 1, tweaks.size, tweaks[index].first, sound, EarPrint.add(sound, tweaks[index].second))

    /** true for B, false for A, null for no difference. */
    fun choose(b: Boolean?) {
        val round = current() ?: return
        if (b == true) sound = round.b
        index++
    }
}
