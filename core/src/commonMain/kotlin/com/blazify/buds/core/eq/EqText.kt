package com.blazify.buds.core.eq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Equalizer settings as text, the way Equalizer APO and AutoEq write them: a "GraphicEQ:" line of points, or
 * "Filter: ON PK Fc 100 Hz Gain -3 dB Q 1.41" lines of filters (peaks and shelves). Read into a curve that EqFit fits
 * to the earbuds' bands; a sound written back out as a GraphicEQ line.
 */
object EqText {
    /** The points of a "GraphicEQ: 20 -1.2; 21 -1.1; …" line, in hertz and decibels, rising; empty when there is none. */
    fun graphicEq(text: String): List<Pair<Double, Double>> {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("GraphicEQ:", ignoreCase = true) } ?: return emptyList()
        return line.substringAfter(':').split(';').mapNotNull { pair ->
            val parts = pair.trim().split(Regex("\\s+"))
            if (parts.size != 2) return@mapNotNull null
            val hz = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val db = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (hz <= 0 || !db.isFinite()) null else hz to db
        }.sortedBy { it.first }
    }

    enum class Kind { Peak, LowShelf, HighShelf }

    data class Filter(val kind: Kind, val hz: Double, val gain: Double, val q: Double)

    private val FILTER = Regex(
        "Filter\\s*\\d*\\s*:\\s*ON\\s+(PK|PEQ|Modal|LSC|LSQ|LS|HSC|HSQ|HS)(?:\\s+\\d+\\s*dB)?\\s+Fc\\s+([0-9.]+)\\s*Hz\\s+Gain\\s+([-+]?[0-9.]+)\\s*dB(?:\\s+Q\\s+([0-9.]+))?",
        RegexOption.IGNORE_CASE,
    )

    /** The filters switched on in Equalizer APO's parametric lines; others (off, or kinds that aren't peaks or shelves) are skipped. */
    fun filters(text: String): List<Filter> = FILTER.findAll(text).mapNotNull { m ->
        val type = m.groupValues[1].uppercase()
        val kind = when {
            type.startsWith("LS") -> Kind.LowShelf
            type.startsWith("HS") -> Kind.HighShelf
            else -> Kind.Peak
        }
        val hz = m.groupValues[2].toDoubleOrNull()?.takeIf { it in 10.0..24_000.0 } ?: return@mapNotNull null
        val gain = m.groupValues[3].toDoubleOrNull()?.takeIf { it in -40.0..40.0 } ?: return@mapNotNull null
        val q = m.groupValues[4].toDoubleOrNull()?.takeIf { it in 0.05..30.0 } ?: if (kind == Kind.Peak) 1.41 else 0.71
        Filter(kind, hz, gain, q)
    }.toList()

    /** How loud one filter makes [hz], in decibels: the usual audio filter recipes, at 48 kHz. */
    fun response(filter: Filter, hz: Double): Double {
        val fs = 48_000.0
        val a = 10.0.pow(filter.gain / 40)
        val w0 = 2 * PI * filter.hz / fs
        val alpha = sin(w0) / (2 * filter.q)
        val c = cos(w0)
        val root = 2 * sqrt(a) * alpha
        val (b, d) = when (filter.kind) {
            Kind.Peak -> doubleArrayOf(1 + alpha * a, -2 * c, 1 - alpha * a) to doubleArrayOf(1 + alpha / a, -2 * c, 1 - alpha / a)
            Kind.LowShelf -> doubleArrayOf(a * ((a + 1) - (a - 1) * c + root), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - root)) to
                doubleArrayOf((a + 1) + (a - 1) * c + root, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - root)
            Kind.HighShelf -> doubleArrayOf(a * ((a + 1) + (a - 1) * c + root), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - root)) to
                doubleArrayOf((a + 1) - (a - 1) * c + root, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - root)
        }
        val w = 2 * PI * hz / fs
        fun power(k: DoubleArray): Double {
            val re = k[0] + k[1] * cos(w) + k[2] * cos(2 * w)
            val im = -k[1] * sin(w) - k[2] * sin(2 * w)
            return re * re + im * im
        }
        return 10 * log10(power(b) / power(d))
    }

    /** Points every twelfth of an octave from 20 Hz to 20 kHz. */
    private fun hearing(): List<Double> = (0..120).map { 20.0 * 2.0.pow(it / 12.0) }.filter { it <= 20_000.0 }

    /**
     * The curve a piece of text describes, whichever of the two forms it is in, as points (hertz, decibels); empty when
     * it holds neither.
     */
    fun curve(text: String): List<Pair<Double, Double>> {
        graphicEq(text).takeIf { it.size >= 2 }?.let { return it }
        val filters = filters(text)
        if (filters.isEmpty()) return emptyList()
        return hearing().map { hz -> hz to filters.sumOf { response(it, hz) } }
    }

    /**
     * A sound as Equalizer APO text: a line naming it, a preamp that keeps the loudest point from clipping, and a
     * GraphicEQ line of how the bands add up, a sixth of an octave apart.
     */
    fun export(name: String, gains: List<Double>, bands: List<Int>): String {
        val qs = EqFit.qualities(bands)
        val points = ((0..59).map { 20.0 * 2.0.pow(it / 6.0) } + 20_000.0).map { hz ->
            kotlin.math.round(hz).toInt() to EqFit.round(EqFit.response(gains, bands, hz, qs), 0.1)
        }
        val loudest = points.maxOf { it.second }.coerceAtLeast(0.0)
        return buildString {
            append("# Blazbuds sound \"").append(name.replace("\"", "'")).append("\"\n")
            append("Preamp: ").append(number(-loudest)).append(" dB\n")
            append("GraphicEQ: ").append(points.joinToString("; ") { (hz, db) -> "$hz ${number(db)}" }).append('\n')
        }
    }

    /** The name a Blazbuds export gave its sound, if the text has one. */
    fun name(text: String): String? = Regex("#\\s*Blazbuds sound \"([^\"\\n]{1,40})\"").find(text)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    private fun number(x: Double): String {
        val v = EqFit.round(x, 0.1)
        return if (v == kotlin.math.floor(v)) "${v.toInt()}.0" else v.toString()
    }
}
