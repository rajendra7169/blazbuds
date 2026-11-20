package com.blazify.buds.core.nothing

import com.blazify.buds.core.model.Battery
import kotlin.math.pow

/**
 * The commands Blazbuds sends to Nothing and CMF earbuds, and how to read and write what they carry. The numbers and
 * layouts follow nada and nadamas (MIT) and, for facts only, Gadgetbridge, ear (web) and PairFree's notes on Nothing's
 * own app.
 */
object NothingCommands {
    /** The protocol version; asking first is how Nothing's app wakes the protocol up. */
    const val READ_VERSION = 0xC001
    const val ACTIVATE = 0xF001
    const val RING = 0xF002
    const val SET_KEYS = 0xF003
    const val SET_EXTRA = 0xF004
    const val READ_BATTERY = 0xC007
    const val READ_EXTRA = 0xC00E
    const val SET_NOISE = 0xF00F
    const val SET_EQ = 0xF010
    const val READ_KEYS = 0xC018
    const val READ_MODEL = 0xC01C
    const val SET_DIRAC = 0xF01D
    const val READ_NOISE = 0xC01E
    const val READ_EQ = 0xC01F
    const val SET_LOW_LAG = 0xF040
    const val SET_CURVE = 0xF041
    const val READ_LOW_LAG = 0xC041
    const val READ_FIRMWARE = 0xC042
    const val READ_CURVE = 0xC044
    const val READ_BASS = 0xC04E
    const val READ_SPATIAL = 0xC04F
    const val READ_DIRAC = 0xC050
    const val SET_BASS = 0xF051
    const val SET_SPATIAL = 0xF052

    /** News the earbuds send by themselves. */
    const val BATTERY_NEWS = 0xE001
    const val NOISE_NEWS = 0xE003

    /** Asks for the noise report's three groups. */
    val NOISE_GROUPS = byteArrayOf(3)

    /** Which part a battery level or a ring is for. */
    const val LEFT = 2
    const val RIGHT = 3
    const val CASE = 4
    const val WHOLE = 6

    /** Extra feature 1 is pausing when an earbud comes out. */
    const val IN_EAR = 1

    /** The preset number that means "the person's own curve". */
    fun customPreset(eq: NothingEq) = if (eq == NothingEq.Dirac) 6 else 5

    /** The product number from a C01C answer, `71 B1` read backwards as `B171`. */
    fun productId(payload: ByteArray): String? =
        if (payload.size < 2) null else NothingFrame.hex4(((payload[1].toInt() and 0xFF) shl 8) or (payload[0].toInt() and 0xFF)).uppercase()

    /** Battery levels: a count, then each part and its level, with the top bit set while it charges. */
    fun batteries(payload: ByteArray): Map<Int, Battery> {
        val count = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return emptyMap()
        val out = mutableMapOf<Int, Battery>()
        for (i in 0 until count) {
            val at = 1 + i * 2
            if (at + 1 >= payload.size) break
            val level = payload[at + 1].toInt() and 0xFF
            val percent = level and 0x7F
            if (percent in 1..100) out[payload[at].toInt() and 0xFF] = Battery(percent, charging = level and 0x80 != 0)
        }
        return out
    }

    /**
     * The noise report: groups of (kind, value, 0). Kind 1 is the mode now, kind 2 the cancelling strength it goes
     * back to, which it keeps while cancelling is off. Returns the two, either null when missing.
     */
    fun noise(payload: ByteArray): Pair<Int?, Int?> {
        var mode: Int? = null
        var strength: Int? = null
        var at = 0
        while (at + 1 < payload.size) {
            val value = payload[at + 1].toInt() and 0xFF
            when (payload[at].toInt()) {
                1 -> mode = value
                2 -> strength = value
            }
            at += 3
        }
        return mode to strength
    }

    /** Extra features: a count, then each feature and its value. */
    fun extras(payload: ByteArray): Map<Int, Int> {
        val count = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return emptyMap()
        val out = mutableMapOf<Int, Int>()
        for (i in 0 until count) {
            val at = 1 + i * 2
            if (at + 1 >= payload.size) break
            out[payload[at].toInt() and 0xFF] = payload[at + 1].toInt() and 0xFF
        }
        return out
    }

    /** One touch setting: which part, which button, which kind of press, and the action. */
    data class Key(val part: Int, val button: Int, val gesture: Int, val action: Int)

    /** The touch settings: a count, then four bytes for each. */
    fun keys(payload: ByteArray): List<Key> {
        val count = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val at = 1 + i * 4
            if (at + 3 >= payload.size) null
            else Key(payload[at].toInt() and 0xFF, payload[at + 1].toInt() and 0xFF, payload[at + 2].toInt() and 0xFF, payload[at + 3].toInt() and 0xFF)
        }
    }

    fun setKey(key: Key) = byteArrayOf(1, key.part.toByte(), key.button.toByte(), key.gesture.toByte(), key.action.toByte())

    /** Gesture numbers: 2 a double press, 3 a triple, 7 press and hold. */
    const val DOUBLE = 2
    const val TRIPLE = 3
    const val HOLD = 7

    /** The button that is the press itself, as opposed to a dial or a slider. */
    const val TOUCH = 1

    /**
     * The person's own sound: a count of three, the overall gain (minus the highest boost, so nothing clips), then
     * for each filter its kind (0 low shelf, 1 peak, 2 high shelf) and its gain, centre and width as little-endian
     * floats, mid then treble then bass as Nothing's app sends them, and nine zero bytes after. Gains are whole
     * decibels from -6 to 6.
     */
    fun curve(bass: Double, mid: Double, treble: Double, shape: NothingCurve): ByteArray {
        fun whole(db: Double) = kotlin.math.round(db.coerceIn(-6.0, 6.0)).toFloat()
        val (b, m, t) = Triple(whole(bass), whole(mid), whole(treble))
        // Minus zero when nothing is boosted, as Nothing's app, ear (web) and BudsLink all send it.
        val total = -maxOf(0f, b, m, t)
        val out = ArrayList<Byte>()
        out += 3
        out += float(total)
        out += 1; out += float(m); out += float(shape.midHz); out += float(shape.midQ)
        out += 2; out += float(t); out += float(shape.highHz); out += float(shape.highQ)
        out += 0; out += float(b); out += float(shape.lowHz); out += float(shape.lowQ)
        repeat(9) { out += 0 }
        return out.toByteArray()
    }

    /** Reads a C044 answer back into bass, mid and treble; null when the earbuds hold no curve. */
    fun curve(payload: ByteArray): List<Double>? {
        val count = payload.firstOrNull()?.toInt()?.and(0xFF) ?: return null
        if (count == 0) return null
        val gains = DoubleArray(3)
        var found = 0
        for (i in 0 until count) {
            val at = 5 + i * 13
            if (at + 13 > payload.size) break
            val kind = payload[at].toInt() and 0xFF
            if (kind !in 0..2) continue
            gains[kind] = readFloat(payload, at + 1).toDouble()
            found++
        }
        return if (found == 0) null else gains.toList()
    }

    /**
     * A curve of any other length (one of the person's saved sounds, most often eight bands) as the earbuds' bass,
     * mid and treble: each is the average of the bands that fall in its part, the bands taken as spread evenly from
     * 100 Hz to 12.8 kHz the way the app's eight-band sounds are. Bass is below 300 Hz, treble from 2.4 kHz up.
     */
    fun threeBands(gains: List<Double>): List<Double> {
        if (gains.size == 3) return gains
        if (gains.isEmpty()) return listOf(0.0, 0.0, 0.0)
        val at = spread(gains.size)
        fun part(from: Double, to: Double, middle: Double): Double {
            val inside = gains.indices.filter { at[it] >= from && at[it] < to }
            return if (inside.isNotEmpty()) inside.sumOf { gains[it] } / inside.size else gainAt(gains, middle)
        }
        return listOf(part(0.0, 300.0, 140.0), part(300.0, 2_400.0, 980.0), part(2_400.0, Double.MAX_VALUE, 5_000.0))
    }

    /** The gain a curve of [gains] (spread as in [threeBands]) has at [hz], read along a straight line between bands. */
    fun gainAt(gains: List<Double>, hz: Double): Double {
        if (gains.isEmpty()) return 0.0
        if (gains.size == 1) return gains[0]
        val at = spread(gains.size)
        if (hz <= at.first()) return gains.first()
        if (hz >= at.last()) return gains.last()
        val i = at.indexOfLast { it <= hz }
        val share = kotlin.math.ln(hz / at[i]) / kotlin.math.ln(at[i + 1] / at[i])
        return gains[i] + (gains[i + 1] - gains[i]) * share
    }

    /** Where [count] bands sit when spread evenly, on a log scale, from 100 Hz to 12.8 kHz. */
    private fun spread(count: Int): List<Double> =
        if (count == 1) listOf(1_000.0) else List(count) { 100.0 * 128.0.pow(it / (count - 1.0)) }

    private fun float(f: Float): List<Byte> {
        val bits = f.toRawBits()
        return listOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
    }

    private fun readFloat(b: ByteArray, at: Int): Float = Float.fromBits(
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24),
    )
}
