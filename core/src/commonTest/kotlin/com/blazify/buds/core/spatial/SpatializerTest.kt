package com.blazify.buds.core.spatial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class SpatializerTest {
    private fun level(x: FloatArray, channel: Int) = sqrt(x.filterIndexed { i, _ -> i % 2 == channel }.sumOf { (it * it).toDouble() } / (x.size / 2))

    @Test
    fun leftOnlySoundStaysLeftButReachesTheOtherEar() {
        val rate = 48_000
        val frames = rate
        val input = FloatArray(frames * 2) { if (it % 2 == 0) (0.5 * sin(2 * PI * 440 * (it / 2) / rate)).toFloat() else 0f }
        val output = FloatArray(frames * 2)
        Spatializer(rate).process(input, output, frames)
        assertTrue(output.all { it.isFinite() && abs(it) <= 1f })
        val l = level(output, 0)
        val r = level(output, 1)
        // A speaker on the left is heard by both ears, the left more.
        assertTrue(l > r * 1.3, "left $l right $r")
        assertTrue(r > l * 0.2, "left $l right $r")
    }

    @Test
    fun orbitMovesTheSound() {
        val rate = 48_000
        val spatial = Spatializer(rate).also { it.mode = SpatialMode.Orbit; it.orbitSeconds = 4.0 }
        val quarter = rate // a quarter of the way round each second
        val tone = { i: Int -> (0.4 * sin(2 * PI * 300 * i / rate)).toFloat() }
        val levels = (0 until 4).map { q ->
            val input = FloatArray(quarter * 2) { tone(q * quarter + it / 2) }
            val output = FloatArray(quarter * 2)
            spatial.process(input, output, quarter)
            level(output, 1) / level(output, 0)
        }
        // Right-heavy while it is on the right (the first half round), left-heavy on the way back.
        assertTrue(levels[0] > 1.05 && levels[3] < 0.95, levels.toString())
    }
}
