package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.soundcore.Packet
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/** A preset as the earbuds know it: its id, its name, and its gains in tenths of a decibel (eight or ten bands). */
class SoundcorePreset(val id: Int, val name: String, val tenths: IntArray)

/** How a model's equalizer is sent. */
enum class EqWrite {
    /** 02 81: the preset id and the bands. */
    Plain,

    /** 02 83: the same, then the limiter's curve for each channel. */
    Drc,

    /** 03 86 or 03 87: the bands with the hearing profile (switched off) and the limiter's curve. */
    HearId,

    /** The same, always as 03 87, for models that need the hearing profile sent whatever their age range says. */
    HearIdAlways,

    /** The same message, for models whose hearing profile has no custom part. */
    BasicHearId,
}

/**
 * A model's equalizer: its presets, the bands the app shows, whether bands can be set by hand, how changes are sent,
 * and whether both earbuds must be out for a change to take.
 */
class EqSetup(
    val presets: List<SoundcorePreset>,
    val write: EqWrite,
    val bothNeeded: Boolean = false,
    /** False on models with presets only. */
    val custom: Boolean = true,
    val frequencies: List<Int> = HZ,
    /** What the bands the app doesn't show are set to when the bands are set by hand. */
    val hidden: IntArray = intArrayOf(0, 0),
    /** The id the earbuds use for bands set by hand. */
    val customId: Int = CUSTOM_ID,
    /** Some models' own equalizer code builds the message itself. */
    val own: ((Parsed, Int, IntArray) -> List<Step>)? = null,
) {
    fun read(eq: EqState): Equalizer {
        val bands = frequencies.size
        val first = eq.first ?: IntArray(bands)
        val second = eq.channels.getOrNull(1)
        val left = (0 until bands).map { first.getOrElse(it) { 0 } / 10.0 }
        val right = second?.let { s -> (0 until bands).map { s.getOrElse(it) { 0 } / 10.0 } } ?: left
        return Equalizer(
            presetId = if (eq.presetId == customId) Equalizer.CUSTOM else eq.presetId,
            presets = presets.map { p -> EqPreset(p.id, p.name, (0 until bands).map { p.tenths.getOrElse(it) { 0 } / 10.0 }) },
            frequencies = frequencies,
            left = left,
            right = right,
            custom = custom,
        )
    }

    /** The gains to send for a preset, or for the bands set by hand, sized to the model's band count. */
    fun gains(presetId: Int, shown: List<Double>?, bandCount: Int): IntArray? {
        if (presetId == Equalizer.CUSTOM) {
            if (!custom || shown == null) return null
            return IntArray(bandCount) { i ->
                if (i < frequencies.size) roundHalfAway(shown.getOrElse(i) { 0.0 } * 10).toInt().coerceIn(-120, 134)
                else hidden.getOrElse(i - frequencies.size) { 0 }
            }
        }
        val preset = presets.firstOrNull { it.id == presetId } ?: return null
        return IntArray(bandCount) { preset.tenths.getOrElse(it) { 0 } }
    }

    /** The messages that set the equalizer to [presetId] with [tenths] on every channel. */
    fun steps(p: Parsed, presetId: Int, tenths: IntArray): List<Step> {
        own?.let { return it(p, presetId, tenths) }
        val eq = p.eq ?: return emptyList()
        val id = if (presetId == Equalizer.CUSTOM) customId else presetId
        val target = EqState(id, eq.channels.map { tenths.copyOf() })
        val keep: (Parsed) -> Unit = { it.eq = target; it.hearId?.enabled = false }
        val packet = when (write) {
            EqWrite.Plain -> Packet(SET_PLAIN, eqBytes(target))
            EqWrite.Drc -> Packet(SET_DRC, eqBytes(target) + target.channels.flatMap { drcBytes(it, tenths.size).toList() }.toByteArray())
            EqWrite.HearId, EqWrite.HearIdAlways, EqWrite.BasicHearId -> hearIdPacket(p, target, force = write == EqWrite.HearIdAlways, basic = write == EqWrite.BasicHearId)
        }
        return listOf(Step(packet, keep))
    }

    companion object {
        const val CUSTOM_ID = 0xFEFE
        val HZ = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)
        val SET_PLAIN = Packet.command(0x02, 0x81)
        val SET_DRC = Packet.command(0x02, 0x83)
        val SET_HEAR_ID = Packet.command(0x03, 0x87)
        val SET_NO_HEAR_ID = Packet.command(0x03, 0x86)

        private fun band(tenths: Int) = (tenths.coerceIn(-120, 134) + 120).toByte()

        /** Bands as bytes, 0xFF for a channel that isn't there. */
        fun bandBytes(values: IntArray?, count: Int): ByteArray =
            if (values == null) ByteArray(count) { 0xFF.toByte() } else ByteArray(count) { band(values.getOrElse(it) { 0 }) }

        fun eqBytes(eq: EqState): ByteArray {
            val bands = eq.channels.firstNotNullOfOrNull { it?.size } ?: 8
            return byteArrayOf(eq.presetId.toByte(), (eq.presetId shr 8).toByte()) + eq.channels.flatMap { bandBytes(it, bands).toList() }.toByteArray()
        }

        /** The limiter's curve for one channel, 0xFF for a channel that isn't there. */
        fun drcBytes(values: IntArray?, count: Int): ByteArray = bandBytes(values?.let { drc(it) }, count)

        /**
         * Soundcore's limiter curve for a set of bands, as OpenSCQ30 works it out from the official app: the first
         * eight bands mixed with fixed weights, then 0 and -12 dB for any bands after them.
         */
        fun drc(values: IntArray): IntArray {
            val a = DoubleArray(8) { values.getOrElse(it) { 0 } / 10.0 }
            val s = 0.85
            val l = 0.95
            val lows = a[2] * 0.81 * s
            val highs = a[5] * 0.81 * s
            val c = arrayOf(
                doubleArrayOf(1.26, -0.71 * s, 0.177, -0.0494, 0.0345, -0.0197, 0.0075, -0.00217),
                doubleArrayOf(-0.71 * s, 1.73 * l, 0.0, 0.204, -0.068, 0.045, -0.0235, 0.0075),
                doubleArrayOf(0.177, -0.81 * s, 1.73 * l, -0.81 * s, 0.208, -0.07, 0.045, -0.0197),
                doubleArrayOf(-0.0494, 0.204, 0.0, 1.73 * l, -0.82 * s, 0.208, -0.068, 0.0345),
                doubleArrayOf(0.0345, -0.068, 0.208, -0.82 * s, 1.73 * l, 0.0, 0.204, -0.0494),
                doubleArrayOf(-0.0197, 0.045, -0.07, 0.208, -0.81 * s, 1.73 * l, -0.81 * s, 0.177),
                doubleArrayOf(0.0075, -0.0235, 0.045, -0.068, 0.204, 0.0, 1.83 * l, -0.71 * s),
                doubleArrayOf(-0.00217, 0.0075, -0.0197, 0.0345, -0.0494, 0.177, -0.71 * s, 1.5),
            )
            val defaults = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120)
            return IntArray(values.size) { b ->
                if (b >= 8) return@IntArray defaults.getOrElse(b) { 0 }
                var acc = 0.0
                for (i in 0 until 8) {
                    acc += when {
                        (b == 1 || b == 3) && i == 2 -> -lows
                        (b == 4 || b == 6) && i == 5 -> -highs
                        else -> a[i] * c[b][i]
                    }
                }
                roundHalfAway(acc / 10.0 * 10.0).toInt().coerceIn(-120, 134)
            }
        }

        /**
         * The equalizer message that carries the hearing profile, laid out as OpenSCQ30 sends it. The profile goes
         * back switched off, so the bands sent are what plays. Earbuds whose age range is 0xFF have no hearing profile
         * and get 0xFF in its place, under 03 86.
         */
        fun hearIdPacket(p: Parsed, eq: EqState, force: Boolean, basic: Boolean): Packet {
            val supports = p.age != 0xFF || force
            val hear = p.hearId
            val bands = eq.channels.firstNotNullOfOrNull { it?.size } ?: 8
            val hearBands = hear?.curves?.firstNotNullOfOrNull { it?.size } ?: bands
            val hearChannels = hear?.curves?.size ?: eq.channels.size
            val wave = ByteArray(eq.channels.size * bands) { 0xFF.toByte() }
            val out = ArrayList<Byte>()
            out += eq.presetId.toByte(); out += (eq.presetId shr 8).toByte()
            val genre = if (basic) 0 else hear?.genre ?: 0
            if (supports) {
                out += genre.toByte(); out += (genre shr 8).toByte()
            }
            eq.channels.forEach { out.addAll(bandBytes(it, bands).toList()) }
            out += (if (supports) p.gender else 0xFF).toByte()
            out += (if (supports) p.age else 0xFF).toByte()
            out += 0
            if (supports) {
                for (i in 0 until hearChannels) out.addAll(bandBytes(hear?.curves?.getOrNull(i), hearBands).toList())
            } else out.addAll(wave.toList())
            val time = if (supports) hear?.time ?: 0L else 0L
            out += (time shr 24).toByte(); out += (time shr 16).toByte(); out += (time shr 8).toByte(); out += time.toByte()
            out += (if (basic) 0 else hear?.type ?: 0).toByte()
            if (supports) {
                for (i in 0 until hearChannels) out.addAll(bandBytes(if (basic) null else hear?.customCurves?.getOrNull(i), hearBands).toList())
            } else out.addAll(wave.toList())
            eq.channels.forEach { out.addAll(drcBytes(it, bands).toList()) }
            return Packet(if (supports) SET_HEAR_ID else SET_NO_HEAR_ID, out.toByteArray())
        }

        /** Soundcore's 22 presets, the same on most models. */
        val PRESETS: List<SoundcorePreset> = presets(rock = intArrayOf(30, 20, -10, -10, 10, 30, 30, 30, 0, -120))

        /** The newer list: the same presets, with a brighter Rock. */
        val PRESETS_TYPE_2: List<SoundcorePreset> = presets(rock = intArrayOf(30, 20, -10, -10, 10, 30, 40, 50, 0, -120))

        private fun presets(rock: IntArray) = listOf(
            SoundcorePreset(0, "Signature", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120)),
            SoundcorePreset(1, "Acoustic", intArrayOf(40, 10, 20, 20, 40, 40, 40, 20, 0, -120)),
            SoundcorePreset(2, "Bass booster", intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120)),
            SoundcorePreset(3, "Bass reducer", intArrayOf(-40, -30, -10, 0, 0, 0, 0, 0, 0, -120)),
            SoundcorePreset(4, "Classical", intArrayOf(30, 30, -20, -20, 0, 20, 30, 40, 0, -120)),
            SoundcorePreset(5, "Podcast", intArrayOf(-30, 20, 40, 40, 30, 20, 0, -20, 0, -120)),
            SoundcorePreset(6, "Dance", intArrayOf(20, -30, -10, 10, 20, 20, 10, -30, 0, -120)),
            SoundcorePreset(7, "Deep", intArrayOf(20, 10, 30, 30, 20, -20, -40, -50, 0, -120)),
            SoundcorePreset(8, "Electronic", intArrayOf(30, 20, -20, 20, 10, 20, 30, 30, 0, -120)),
            SoundcorePreset(9, "Flat", intArrayOf(-20, -20, -10, 0, 0, 0, -20, -20, 0, -120)),
            SoundcorePreset(10, "Hip-hop", intArrayOf(20, 30, -10, -10, 20, -10, 20, 30, 0, -120)),
            SoundcorePreset(11, "Jazz", intArrayOf(20, 20, -20, -20, 0, 20, 30, 40, 0, -120)),
            SoundcorePreset(12, "Latin", intArrayOf(0, 0, -20, -20, -20, 0, 30, 50, 0, -120)),
            SoundcorePreset(13, "Lounge", intArrayOf(-10, 20, 40, 30, 0, -20, 20, 10, 0, -120)),
            SoundcorePreset(14, "Piano", intArrayOf(0, 30, 30, 20, 40, 50, 30, 40, 0, -120)),
            SoundcorePreset(15, "Pop", intArrayOf(-10, 10, 30, 30, 10, -10, -20, -30, 0, -120)),
            SoundcorePreset(16, "R&B", intArrayOf(60, 20, -20, -20, 20, 30, 30, 40, 0, -120)),
            SoundcorePreset(17, "Rock", rock),
            SoundcorePreset(18, "Small speakers", intArrayOf(40, 30, 10, 0, -20, -30, -40, -40, 0, -120)),
            SoundcorePreset(19, "Spoken word", intArrayOf(-30, -20, 10, 20, 20, 10, 0, -30, 0, -120)),
            SoundcorePreset(20, "Treble booster", intArrayOf(-20, -20, -20, -10, 10, 20, 20, 40, 0, -120)),
            SoundcorePreset(21, "Treble reducer", intArrayOf(0, 0, 0, -20, -30, -40, -40, -60, 0, -120)),
        )
    }
}

/** Rounds .5 away from zero, as the official app and OpenSCQ30 do; Kotlin's own rounding goes to even. */
internal fun roundHalfAway(x: Double): Double = sign(x) * floor(abs(x) + 0.5)
