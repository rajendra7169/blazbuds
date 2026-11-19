package com.blazify.buds.core.earprint

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The sounds EarPrint plays, made in the app: 16-bit stereo at 44.1 kHz, left then right. */
object TestSounds {
    const val RATE = 44_100

    /**
     * Three short beeps of one pitch at one loudness ([dbfs], decibels below full scale) in one ear. Each beep fades in
     * and out, so there is no click to hear instead of the tone.
     */
    fun beeps(hz: Int, dbfs: Double, left: Boolean, right: Boolean): ShortArray {
        val beep = (RATE * 0.28).toInt()
        val gap = (RATE * 0.22).toInt()
        val frames = 3 * (beep + gap)
        val out = ShortArray(frames * 2)
        val amplitude = 10.0.pow(dbfs / 20.0) * Short.MAX_VALUE
        var at = 0
        repeat(3) {
            for (i in 0 until beep) {
                val t = i.toDouble() / beep
                val edge = minOf(1.0, minOf(t, 1 - t) * 12)
                val v = (sin(2 * PI * hz * i / RATE) * amplitude * edge).toInt().toShort()
                out[at * 2] = if (left) v else 0
                out[at * 2 + 1] = if (right) v else 0
                at++
            }
            at += gap
        }
        return out
    }

    /**
     * A short beat for hearing the difference between two sounds: a deep kick and bass, warm chords, a bright melody
     * and crisp hi-hats, so every part of the sound is there. Four bars at 112 beats a minute, made to loop.
     */
    fun demo(): ShortArray {
        val bpm = 112.0
        val beat = 60.0 / bpm
        val bars = 4
        val seconds = bars * 4 * beat
        val frames = (RATE * seconds).toInt()
        val mix = Mix(DoubleArray(frames), DoubleArray(frames))
        val random = kotlin.random.Random(7)
        fun note(n: Int) = 440.0 * 2.0.pow((n - 69) / 12.0)
        // Chords: A minor, F, C, G, one a bar.
        val chords = listOf(listOf(57, 60, 64), listOf(53, 57, 60), listOf(48, 52, 55), listOf(55, 59, 62))
        val roots = listOf(33, 29, 36, 31)
        val melody = listOf(76, 72, 74, 72, 69, 72, 74, 76, 77, 76, 74, 72, 74, 71, 72, 74)
        for (bar in 0 until bars) {
            val barStart = bar * 4 * beat
            for (b in 0 until 4) {
                val t0 = barStart + b * beat
                // Kick: a falling sine with a quick decay.
                mix.add(t0, 0.35, { t -> sin(2 * PI * (50 + 90 * exp(-t * 30)) * t) * exp(-t * 9) * 0.9 })
                // Snare on two and four: noise with a short body.
                if (b % 2 == 1) mix.add(t0, 0.18, { t -> (random.nextDouble() * 2 - 1) * exp(-t * 22) * 0.35 + sin(2 * PI * 190 * t) * exp(-t * 30) * 0.2 })
                // Hi-hats on every eighth: bright noise, very short.
                for (e in 0..1) mix.add(t0 + e * beat / 2, 0.05, { t -> (random.nextDouble() * 2 - 1).let { it - 0.6 * it } * exp(-t * 90) * 0.18 }, lean = 0.4)
                // Bass on the root, a fifth on the and.
                val root = note(roots[bar])
                mix.add(t0, beat * 0.45, { t -> (sin(2 * PI * root * t) + 0.35 * sin(4 * PI * root * t)) * exp(-t * 3) * 0.32 })
                mix.add(t0 + beat / 2, beat * 0.4, { t -> sin(2 * PI * root * 1.5 * t) * exp(-t * 4) * 0.22 })
            }
            // Chord pad across the bar, a little to each side.
            chords[bar].forEachIndexed { i, n ->
                val hz = note(n)
                mix.add(barStart, 4 * beat, { t -> (sin(2 * PI * hz * t) + 0.3 * sin(4 * PI * hz * t) + 0.12 * sin(6 * PI * hz * t)) * minOf(1.0, t * 8) * exp(-t * 0.4) * 0.07 }, lean = (i - 1) * 0.5)
            }
            // Melody: a pluck on each beat.
            for (b in 0 until 4) {
                val hz = note(melody[bar * 4 + b])
                mix.add(barStart + b * beat, beat * 0.9, { t -> (sin(2 * PI * hz * t) + 0.5 * sin(4 * PI * hz * t) + 0.25 * sin(6 * PI * hz * t) + 0.1 * sin(10 * PI * hz * t)) * exp(-t * 5) * 0.12 }, lean = 0.25)
            }
        }
        val peak = mix.sound.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val out = ShortArray(frames * 2)
        for (f in 0 until frames) {
            val side = (mix.side[f] / peak).coerceIn(-0.5, 0.5)
            out[f * 2] = ((mix.sound[f] / peak * 0.8 * (1 - side * 0.5)) * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            out[f * 2 + 1] = ((mix.sound[f] / peak * 0.8 * (1 + side * 0.5)) * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** The beat being mixed: the sound, and how much of it leans to one side. */
    private class Mix(val sound: DoubleArray, val side: DoubleArray) {
        /** Adds a sound [length] seconds long at [start]; inline so working out each sample stays quick on a phone. */
        inline fun add(start: Double, length: Double, voice: (Double) -> Double, lean: Double = 0.0) {
            val from = (start * RATE).toInt()
            val count = (length * RATE).toInt()
            for (i in 0 until count) {
                val f = from + i
                if (f >= sound.size) break
                val v = voice(i.toDouble() / RATE)
                sound[f] += v
                side[f] += lean * v
            }
        }
    }
}
