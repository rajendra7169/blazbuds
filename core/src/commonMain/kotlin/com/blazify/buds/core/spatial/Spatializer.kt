package com.blazify.buds.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** How the sound is placed round the listener. */
enum class SpatialMode {
    /** Like a pair of speakers in front of you in a room, instead of inside your head. */
    Speakers,

    /** The pair of speakers slowly travels round your head. */
    Orbit,
}

/**
 * Turns stereo sound into sound that comes from round the head. Each channel is played from a virtual speaker, and
 * each ear hears each speaker the way it would in a room: the speaker on the other side a moment later, quieter and
 * duller (the head is in the way), darker from behind, with a few wall echoes and a little room after it so the
 * sound sits outside the head. Works on live sound, a block at a time, as interleaved stereo floats.
 */
class Spatializer(private val rate: Int) {
    var mode: SpatialMode = SpatialMode.Speakers

    /** Seconds for one trip round the head in [SpatialMode.Orbit]. */
    var orbitSeconds = 12.0

    private val sources = arrayOf(Source(rate), Source(rate))
    private val echoes = arrayOf(Source(rate), Source(rate), Source(rate), Source(rate))
    private val echoLine = Delay((rate * 0.05).toInt() + 2)
    private val echoLineR = Delay((rate * 0.05).toInt() + 2)
    private val room = Room(rate)
    private var time = 0L

    /** Processes [frames] frames from [input] into [output]; both interleaved left, right. */
    fun process(input: FloatArray, output: FloatArray, frames: Int) {
        val orbit = mode == SpatialMode.Orbit
        for (f in 0 until frames) {
            val l = input[f * 2].toDouble()
            val r = input[f * 2 + 1].toDouble()
            val turn = if (orbit) 2 * PI * ((time.toDouble() / rate / orbitSeconds) % 1.0) else 0.0
            time++
            // The two speakers, 30° either side of where you face (or of where the orbit has got to).
            var outL = 0.0
            var outR = 0.0
            sources[0].render(l, turn - SPREAD).let { outL += it.first; outR += it.second }
            sources[1].render(r, turn + SPREAD).let { outL += it.first; outR += it.second }
            // Echoes off the side walls, arriving from wider angles a few milliseconds later.
            echoLine.push(l)
            echoLineR.push(r)
            for ((k, echo) in echoes.withIndex()) {
                val fromLeft = k % 2 == 0
                val delay = ECHO_DELAYS[k] * rate
                val s = (if (fromLeft) echoLine else echoLineR).read(delay) * ECHO_GAINS[k]
                val angle = turn + (if (fromLeft) -1 else 1) * ECHO_ANGLES[k]
                echo.render(s, angle).let { outL += it.first; outR += it.second }
            }
            // A small room after it all.
            val (wl, wr) = room.process(l + r)
            outL += wl
            outR += wr
            output[f * 2] = soft(outL * GAIN).toFloat()
            output[f * 2 + 1] = soft(outR * GAIN).toFloat()
        }
    }

    private fun soft(x: Double) = if (abs(x) < 0.9) x else tanh(x)

    /** One sound at an angle round the head, as heard by each ear. */
    private class Source(rate: Int) {
        private val line = Delay((rate * 0.0012).toInt() + 4)
        private var farLow = 0.0
        private var darkL = 0.0
        private var darkR = 0.0
        private val sampleTime = 1.0 / rate

        /** Left and right ear for [x] coming from [angle] radians clockwise from straight ahead. */
        fun render(x: Double, angle: Double): Pair<Double, Double> {
            line.push(x)
            val side = sin(angle)
            val front = cos(angle)
            // Woodworth's round head: how much later it reaches the far ear.
            val lag = HEAD / SOUND * (abs(asin(side.coerceIn(-1.0, 1.0))) + abs(side)) / sampleTime
            val near = x
            val far = line.read(lag)
            val shade = abs(side)
            // The far ear sits in the head's shadow: quieter, and duller the more the sound is to one side.
            farLow += (0.95 - 0.8 * shade) * (far - farLow)
            val farEar = farLow * (1 - 0.65 * shade)
            var left = if (side >= 0) farEar else near
            var right = if (side >= 0) near else farEar
            // From behind, the outer ear hides the treble.
            val behind = (-front).coerceAtLeast(0.0)
            if (behind > 0) {
                val k = 0.25 + 0.6 * (1 - behind)
                darkL += k * (left - darkL)
                darkR += k * (right - darkR)
                left = left * (1 - 0.35 * behind) + darkL * 0.35 * behind
                right = right * (1 - 0.35 * behind) + darkR * 0.35 * behind
            } else {
                darkL = left
                darkR = right
            }
            return left to right
        }
    }

    /** A short delay line that can be read between samples. */
    private class Delay(size: Int) {
        private val buffer = DoubleArray(size)
        private var at = 0
        fun push(x: Double) {
            buffer[at] = x
            at = (at + 1) % buffer.size
        }

        /** The sample [samples] ago (0 = the one just pushed). */
        fun read(samples: Double): Double {
            val back = samples.coerceIn(0.0, buffer.size - 2.0)
            val whole = back.toInt()
            val frac = back - whole
            val i0 = (at - 1 - whole + buffer.size * 2) % buffer.size
            val i1 = (i0 - 1 + buffer.size) % buffer.size
            return buffer[i0] * (1 - frac) + buffer[i1] * frac
        }
    }

    /**
     * A small, soft room: a few feedback combs and all-passes for each ear, a little different on each side so the
     * two ears don't hear the same tail (Schroeder's reverb).
     */
    private class Room(rate: Int) {
        private val scale = rate / 44_100.0
        private val combsL = intArrayOf(1116, 1188, 1277, 1356).map { Comb((it * scale).toInt()) }
        private val combsR = intArrayOf(1139, 1211, 1300, 1379).map { Comb((it * scale).toInt()) }
        private val passL = intArrayOf(556, 441).map { AllPass((it * scale).toInt()) }
        private val passR = intArrayOf(579, 464).map { AllPass((it * scale).toInt()) }
        private val pre = DoubleArray((rate * 0.012).toInt())
        private var preAt = 0

        fun process(x: Double): Pair<Double, Double> {
            val delayed = pre[preAt]
            pre[preAt] = x
            preAt = (preAt + 1) % pre.size
            val input = delayed * 0.015
            var l = combsL.sumOf { it.process(input) }
            var r = combsR.sumOf { it.process(input) }
            for (p in passL) l = p.process(l)
            for (p in passR) r = p.process(r)
            return l * WET to r * WET
        }

        private class Comb(size: Int) {
            private val buffer = DoubleArray(size.coerceAtLeast(1))
            private var at = 0
            private var low = 0.0
            fun process(x: Double): Double {
                val out = buffer[at]
                low = out * 0.6 + low * 0.4 // walls soak up the treble
                buffer[at] = x + low * 0.78
                at = (at + 1) % buffer.size
                return out
            }
        }

        private class AllPass(size: Int) {
            private val buffer = DoubleArray(size.coerceAtLeast(1))
            private var at = 0
            fun process(x: Double): Double {
                val held = buffer[at]
                val out = -x + held
                buffer[at] = x + held * 0.5
                at = (at + 1) % buffer.size
                return out
            }
        }

        companion object {
            const val WET = 1.0
        }
    }

    companion object {
        private const val HEAD = 0.0875
        private const val SOUND = 343.0
        private const val SPREAD = PI / 6
        private val ECHO_DELAYS = doubleArrayOf(0.009, 0.011, 0.017, 0.021)
        private val ECHO_GAINS = doubleArrayOf(0.28, 0.28, 0.18, 0.18)
        private val ECHO_ANGLES = doubleArrayOf(PI * 0.4, PI * 0.4, PI * 0.6, PI * 0.6)
        private const val GAIN = 0.72
    }
}
