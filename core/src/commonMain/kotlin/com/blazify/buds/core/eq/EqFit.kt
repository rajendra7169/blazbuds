package com.blazify.buds.core.eq

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Fitting a sound curve to a pair's own bands. A curve is any number of points (hertz, decibels), like an AutoEq
 * correction or an Equalizer APO file; the earbuds only have their few bands, each lifting or cutting a bell around its
 * centre. The fit picks the band gains whose bells together come closest to the curve, by least squares over points a
 * twelfth of an octave apart. The overall level is left free, since only the shape is heard (the volume sets the rest),
 * and a band that would go past the earbuds' range is held at its limit while the others make up for it.
 *
 * tools/autoeq/refresh.py does the same sums in Python to build [TunedTable]; TunedTableTest checks the two agree.
 */
object EqFit {
    /** A light pull towards no change, so neighbouring bands don't fight each other with large opposite gains. */
    const val RIDGE = 0.003

    /** How wide each band's bell is: an octave apart gives a Q of about 1.41, wider spacing a wider bell. */
    fun qualities(bands: List<Int>): List<Double> = bands.indices.map { k ->
        val gaps = listOfNotNull(
            bands.getOrNull(k - 1)?.let { log2(bands[k].toDouble() / it) },
            bands.getOrNull(k + 1)?.let { log2(it.toDouble() / bands[k]) },
        ).filter { it > 0 }
        if (gaps.isEmpty()) 1.0 else {
            val octaves = gaps.average()
            val ratio = 2.0.pow(octaves)
            sqrt(ratio) / (ratio - 1)
        }
    }

    /** How much of a band's gain is heard at [hz]: all of it at the centre, less and less away from it. */
    fun bell(hz: Double, centre: Double, q: Double): Double {
        val x = q * (hz / centre - centre / hz)
        return 1.0 / (1.0 + x * x)
    }

    /** The sound a set of band gains makes at [hz], as the bells add up. */
    fun response(gains: List<Double>, bands: List<Int>, hz: Double, qs: List<Double> = qualities(bands)): Double =
        bands.indices.sumOf { gains.getOrElse(it) { 0.0 } * bell(hz, bands[it].toDouble(), qs[it]) }

    /** A curve's value at [hz]: straight between its neighbours on a log scale, held flat past either end. */
    fun at(points: List<Pair<Double, Double>>, hz: Double): Double {
        if (points.isEmpty()) return 0.0
        if (hz <= points.first().first) return points.first().second
        if (hz >= points.last().first) return points.last().second
        var i = 1
        while (points[i].first < hz) i++
        val (f0, g0) = points[i - 1]
        val (f1, g1) = points[i]
        if (f1 <= f0) return g1
        val t = (ln(hz) - ln(f0)) / (ln(f1) - ln(f0))
        return g0 + t * (g1 - g0)
    }

    /** The frequencies the fit compares at: a twelfth of an octave apart, from an octave below the lowest band to an octave above the highest. */
    fun grid(bands: List<Int>): List<Double> {
        val low = maxOf(20.0, bands.first() / 2.0)
        val high = minOf(20_000.0, bands.last() * 2.0)
        val out = ArrayList<Double>()
        var j = 0
        while (true) {
            val f = low * 2.0.pow(j / 12.0)
            if (f > high * 1.000001) break
            out += f
            j++
        }
        return out
    }

    /**
     * The band gains for [bands] that come closest to the curve [points] (hertz, decibels, rising), each within [min]
     * and [max] and rounded to [step].
     */
    fun fit(points: List<Pair<Double, Double>>, bands: List<Int>, min: Double = -6.0, max: Double = 6.0, step: Double = 0.1): List<Double> {
        if (bands.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.first }
        if (sorted.isEmpty()) return List(bands.size) { 0.0 }
        val qs = qualities(bands)
        val grid = grid(bands)
        val target = grid.map { at(sorted, it) }
        val shape = grid.map { f -> bands.indices.map { k -> bell(f, bands[k].toDouble(), qs[k]) } }
        val fixed = HashMap<Int, Double>()
        var gains = DoubleArray(bands.size)
        repeat(bands.size + 1) {
            gains = solve(shape, target, fixed)
            var worst = -1
            var by = 1e-9
            for (k in bands.indices) {
                if (k in fixed) continue
                val over = maxOf(gains[k] - max, min - gains[k])
                if (over > by) { by = over; worst = k }
            }
            if (worst < 0) return gains.map { round(it.coerceIn(min, max), step) }
            fixed[worst] = if (gains[worst] > max) max else min
        }
        return gains.map { round(it.coerceIn(min, max), step) }
    }

    /**
     * Least squares for the free bands and the overall level: the mean squared miss over the grid, plus [RIDGE] times
     * each free band's gain squared. Bands in [fixed] keep their value.
     */
    private fun solve(shape: List<List<Double>>, target: List<Double>, fixed: Map<Int, Double>): DoubleArray {
        val bands = shape.first().size
        val free = (0 until bands).filter { it !in fixed }
        val n = free.size + 1 // the free bands, then the level
        val a = Array(n) { DoubleArray(n) }
        val b = DoubleArray(n)
        val count = target.size.toDouble()
        for (j in target.indices) {
            val row = DoubleArray(n)
            free.forEachIndexed { i, k -> row[i] = shape[j][k] }
            row[n - 1] = 1.0
            var rest = target[j]
            for ((k, v) in fixed) rest -= shape[j][k] * v
            for (r in 0 until n) {
                b[r] += row[r] * rest / count
                for (c in 0 until n) a[r][c] += row[r] * row[c] / count
            }
        }
        for (i in 0 until n - 1) a[i][i] += RIDGE
        val x = gauss(a, b)
        val out = DoubleArray(bands)
        for ((k, v) in fixed) out[k] = v
        free.forEachIndexed { i, k -> out[k] = x[i] }
        return out
    }

    /** Solves a small square system by Gaussian elimination with partial pivoting. */
    private fun gauss(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (pivot != col) {
                val t = a[col]; a[col] = a[pivot]; a[pivot] = t
                val u = b[col]; b[col] = b[pivot]; b[pivot] = u
            }
            val p = a[col][col]
            if (abs(p) < 1e-12) continue
            for (r in col + 1 until n) {
                val factor = a[r][col] / p
                if (factor == 0.0) continue
                for (c in col until n) a[r][c] -= factor * a[col][c]
                b[r] -= factor * b[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (c in r + 1 until n) s -= a[r][c] * x[c]
            x[r] = if (abs(a[r][r]) < 1e-12) 0.0 else s / a[r][r]
        }
        return x
    }

    /** Rounds to [step], halves away from zero, and to the tenth so 0.30000000000000004 reads as 0.3. */
    fun round(x: Double, step: Double): Double {
        val steps = sign(x) * floor(abs(x) / step + 0.5)
        val v = kotlin.math.round(steps * step * 10) / 10.0
        return if (v == 0.0) 0.0 else v
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)
}
