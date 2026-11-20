package com.blazify.buds.core.earprint

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud the room is, from the phone's or computer's microphone, for the hearing check: quiet beeps are lost in
 * a noisy room and the result comes out wrong. The level is near enough to decibels of sound in the air for "quiet
 * room or not"; it isn't a calibrated sound meter.
 */
class RoomNoise {
    private var lastIn = 0.0
    private var lastOut = 0.0

    /** The level of [count] samples of 16-bit sound, in dB, after leaving out the rumble below about 100 Hz. */
    fun level(samples: ShortArray, count: Int, rate: Int): Double {
        if (count <= 0) return 0.0
        val k = 1.0 / (1.0 + 2 * kotlin.math.PI * 100.0 / rate)
        var sum = 0.0
        for (i in 0 until count) {
            val x = samples[i] / 32768.0
            // A gentle high-pass: rumble from traffic and the building doesn't hide the beeps the way chatter does.
            lastOut = k * (lastOut + x - lastIn)
            lastIn = x
            sum += lastOut * lastOut
        }
        val rms = sqrt(sum / count).coerceAtLeast(1e-7)
        return (20 * log10(rms) + OFFSET).coerceIn(0.0, 100.0)
    }

    companion object {
        /** From the microphone's numbers to roughly decibels in the room; set so a phone reads what Soundcore reads in the same room. */
        const val OFFSET = 91.0

        /** Quieter than this, the hearing check is precise. */
        const val QUIET = 60.0
    }
}
