package com.blazify.buds.core.eq

import kotlin.math.abs

/**
 * Left and right balance on earbuds whose equalizer takes each ear's bands on its own: the sound moves towards one
 * ear by turning every band of the other ear down by the same amount. Nothing is lifted, so nothing gets louder than
 * the curve itself. The balance travels inside the bands, so it stays with the curve on the earbuds.
 *
 * A balance is in decibels: below zero towards the left (the right ear quieter), above zero towards the right.
 */
object Balance {
    const val MAX = 6.0

    /** The two ears' bands for a curve at a balance. */
    fun split(curve: List<Double>, balance: Double): Pair<List<Double>, List<Double>> {
        val b = balance.coerceIn(-MAX, MAX)
        val left = curve.map { round1(it - maxOf(0.0, b)) }
        val right = curve.map { round1(it - maxOf(0.0, -b)) }
        return left to right
    }

    /** The balance the two ears' bands hold, to the half decibel; zero when they don't differ by one amount. */
    fun of(left: List<Double>, right: List<Double>): Double {
        if (left.isEmpty() || left.size != right.size) return 0.0
        val gaps = left.indices.map { right[it] - left[it] }
        val mean = gaps.average()
        if (gaps.any { abs(it - mean) > 0.15 }) return 0.0
        return (kotlin.math.round(mean * 2) / 2.0).coerceIn(-MAX, MAX)
    }

    /** The curve under a balance: the louder ear's bands. */
    fun curve(left: List<Double>, right: List<Double>): List<Double> =
        if (left.size != right.size || of(left, right) == 0.0) left else left.indices.map { maxOf(left[it], right[it]) }

    private fun round1(x: Double) = kotlin.math.round(x * 10) / 10.0
}
