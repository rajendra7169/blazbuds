package com.blazify.buds.core

import kotlin.math.PI
import kotlin.math.sin

/**
 * The sound for finding earbuds, made rather than recorded: three quick rising chirps and a pause, sharp enough to
 * follow across a room. Soundcore's own app finds earbuds the same way, by playing a sound through them, so this works
 * for any pair. Stereo, so it can come from the left earbud, the right, or both.
 */
object FindTone {
    const val RATE = 44_100

    /** One cycle of the sound as 16-bit stereo samples, left then right. */
    fun samples(left: Boolean, right: Boolean): ShortArray {
        val chirp = (RATE * 0.16).toInt()
        val gap = (RATE * 0.06).toInt()
        val rest = (RATE * 0.45).toInt()
        val frames = 3 * (chirp + gap) + rest
        val out = ShortArray(frames * 2)
        var phase = 0.0
        var at = 0
        repeat(3) {
            for (i in 0 until chirp) {
                val t = i.toDouble() / chirp
                // From 2.2 kHz up to 3.8 kHz, where the ear is most sensitive.
                val hz = 2_200.0 + 1_600.0 * t
                phase += 2 * PI * hz / RATE
                // Soft edges, so it doesn't click.
                val edge = minOf(1.0, minOf(t, 1 - t) * 20)
                val value = (sin(phase) * 0.9 * edge * Short.MAX_VALUE).toInt().toShort()
                out[at * 2] = if (left) value else 0
                out[at * 2 + 1] = if (right) value else 0
                at++
            }
            at += gap
        }
        return out
    }
}
