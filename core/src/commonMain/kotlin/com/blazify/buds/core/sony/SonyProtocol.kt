package com.blazify.buds.core.sony

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import kotlin.math.roundToInt

/**
 * The two command sets Sony's earbuds and headphones speak. Older models (WH-1000XM3, WF-1000XM3, WF-SP800N and
 * others) use the first, newer ones (WH-1000XM5, WF-1000XM4 and later, LinkBuds) the second. Both share connecting,
 * noise control and the equalizer's numbering; they differ for the battery, power and some switches. A pair says
 * which it speaks in its first answer, and lists the functions it has, so only those are asked for.
 */
enum class SonySet { One, Two }

/** What a pair can do, from the function numbers it lists in answer to `06 00`. */
data class SonyFeatures(
    val set: SonySet,
    /** The battery kinds to ask for: one for headphones, left and right, and the case. */
    val batteries: List<Int>,
    /** The noise control layout to ask with, or null for none we can show. */
    val noise: Int?,
    /** The equalizer kind to ask with, and whether it takes a curve of the person's own. */
    val eq: Int?,
    val eqCustom: Boolean,
    val upscaling: Boolean,
    val wearPause: Boolean,
    /** The kind speak-to-chat is asked with. */
    val speakToChat: Int?,
    /** The kind automatic power off is asked with. */
    val autoOff: Int?,
    /** The kind the touch sensors' functions are asked with. */
    val keys: Int?,
) {
    companion object {
        fun from(set: SonySet, functions: Set<Int>): SonyFeatures = when (set) {
            SonySet.One -> SonyFeatures(
                set,
                batteries = listOfNotNull(0.takeIf { 0x11 in functions }, 1.takeIf { 0x15 in functions }, 2.takeIf { 0x18 in functions }),
                noise = when {
                    0x62 in functions -> 0x02
                    0x61 in functions -> 0x01
                    0x63 in functions -> 0x03
                    else -> null
                },
                eq = when {
                    0x51 in functions -> 0x01
                    0x53 in functions -> 0x03
                    else -> null
                },
                eqCustom = 0x51 in functions,
                upscaling = 0xE2 in functions,
                wearPause = 0xF3 in functions,
                speakToChat = 0x05.takeIf { 0xF5 in functions },
                autoOff = 0x04.takeIf { 0xF4 in functions },
                keys = 0x06.takeIf { 0xF6 in functions },
            )
            SonySet.Two -> SonyFeatures(
                set,
                batteries = listOfNotNull(
                    when { 0x28 in functions -> 0x08; 0x20 in functions -> 0x00; else -> null },
                    when { 0x29 in functions -> 0x09; 0x21 in functions -> 0x01; else -> null },
                    when { 0x2A in functions -> 0x0A; 0x22 in functions -> 0x02; else -> null },
                ),
                noise = when {
                    0x6D in functions -> 0x19
                    0x6B in functions -> 0x17
                    0x6C in functions -> 0x18
                    0x6A in functions -> 0x16
                    0x68 in functions -> 0x15
                    0x67 in functions -> 0x22
                    else -> null
                },
                eq = when {
                    0x50 in functions -> 0x00
                    0x57 in functions -> 0x04
                    0x52 in functions -> 0x02
                    else -> null
                },
                eqCustom = 0x50 in functions || 0x57 in functions,
                upscaling = 0xE2 in functions,
                wearPause = 0xF1 in functions,
                speakToChat = when { 0xFC in functions -> 0x0C; 0xF2 in functions -> 0x02; else -> null },
                autoOff = when { 0x25 in functions -> 0x05; 0x24 in functions -> 0x04; else -> null },
                keys = 0x03.takeIf { 0xF3 in functions },
            )
        }
    }
}

/** The batteries one answer tells about. */
data class SonyBatteries(val overall: Battery? = null, val left: Battery? = null, val right: Battery? = null, val case: Battery? = null)

/**
 * Noise cancelling and ambient sound as the earbuds last reported them, kept whole so a change is written in the very
 * layout they use, with only the changed bytes different.
 */
class SonyNoise(val set: SonySet, val raw: ByteArray) {
    val type: Int get() = raw[1].toInt() and 0xFF

    private fun at(i: Int): Int = raw.getOrNull(i)?.toInt()?.and(0xFF) ?: 0

    /** Where the "focus on voice" byte is, if this layout has one. */
    private val voiceAt: Int? = when (set) {
        SonySet.One -> when (type) { 0x02 -> 6; 0x03 -> 4; else -> null }
        SonySet.Two -> when (type) {
            0x15, 0x16 -> 6
            0x17, 0x18 -> if (raw.size >= 8) 6 else 5
            0x19 -> 5
            0x22 -> 4
            else -> null
        }
    }

    /** Where the noise cancelling value sits in set two's longer layouts. */
    private val ncValueAt: Int? = if (set == SonySet.Two && raw.size >= 8 && type in listOf(0x15, 0x16, 0x17, 0x18)) 5 else null

    val modes: List<ListeningMode> = when {
        set == SonySet.One && type == 0x01 -> listOf(ListeningMode.NoiseCancelling, ListeningMode.Normal)
        (set == SonySet.One && type == 0x03) || (set == SonySet.Two && type == 0x22) -> listOf(ListeningMode.Transparency, ListeningMode.Normal)
        else -> ListeningMode.entries
    }

    val voice: Boolean? get() = voiceAt?.let { at(it) == 1 }

    val mode: ListeningMode
        get() = when (set) {
            SonySet.One -> when (type) {
                0x01 -> if (at(3) == 1) ListeningMode.NoiseCancelling else ListeningMode.Normal
                0x02 -> when {
                    at(2) == 0 -> ListeningMode.Normal
                    at(4) > 0 -> ListeningMode.NoiseCancelling
                    else -> ListeningMode.Transparency
                }
                else -> if (at(2) == 0) ListeningMode.Normal else ListeningMode.Transparency
            }
            SonySet.Two -> when {
                at(3) == 0 -> ListeningMode.Normal
                type == 0x22 -> ListeningMode.Transparency
                at(4) == 1 -> ListeningMode.Transparency
                else -> ListeningMode.NoiseCancelling
            }
        }

    /** The message that sets [mode] (and [voice] where given), or null if this layout hasn't that mode. */
    fun set(mode: ListeningMode = this.mode, voice: Boolean? = null): ByteArray? {
        if (mode !in modes) return null
        val out = raw.copyOf()
        out[0] = SET_NOISE.toByte()
        when (set) {
            SonySet.One -> when (type) {
                0x01 -> out[3] = (if (mode == ListeningMode.NoiseCancelling) 1 else 0).toByte()
                0x02 -> {
                    // On is written as "adjustment complete", as Sony's app does.
                    out[2] = (if (mode == ListeningMode.Normal) 0x00 else 0x11).toByte()
                    out[4] = when (mode) {
                        ListeningMode.NoiseCancelling -> if (at(4) > 0) at(4) else if (at(3) == 2) 2 else 1
                        else -> 0
                    }.toByte()
                }
                else -> out[2] = (if (mode == ListeningMode.Normal) 0x00 else 0x11).toByte()
            }
            SonySet.Two -> {
                out[2] = 1
                out[3] = (if (mode == ListeningMode.Normal) 0 else 1).toByte()
                if (type != 0x22 && mode != ListeningMode.Normal) out[4] = (if (mode == ListeningMode.Transparency) 1 else 0).toByte()
                ncValueAt?.let { if (mode == ListeningMode.NoiseCancelling && at(it) == 0) out[it] = 2 }
            }
        }
        if (voice != null) voiceAt?.let { out[it] = (if (voice) 1 else 0).toByte() }
        return out
    }

    companion object {
        const val SET_NOISE = 0x68
    }
}

/**
 * The equalizer as the earbuds describe it over three answers: what it can do (`51`), what each value is (`5B`) and
 * its setting (`57`, or `59` when it changes). Values run from 0 to steps − 1, flat in the middle; one step is shown
 * as one decibel, as Gadgetbridge does.
 */
class SonyEq(val set: SonySet, val type: Int, val customizable: Boolean) {
    var steps: Int? = null
    var presets: List<Int> = emptyList()
    var names: Map<Int, String> = emptyMap()

    /** Each value's kind and number: 1 hertz, 2 kilohertz, 0x10 something special (1 is Clear Bass). */
    var bands: List<Pair<Int, Int>>? = null
    var preset: Int? = null
    var values: List<Int> = emptyList()

    /** Reads a `51`, `5B`, `57` or `59` answer about this equalizer; false if it was about something else. */
    fun read(payload: ByteArray): Boolean {
        if (payload.size < 2 || (payload[1].toInt() and 0xFF) != type) return false
        val p = payload.map { it.toInt() and 0xFF }
        when (p[0]) {
            0x51 -> {
                if (p.size < 5) return false
                steps = p[3]
                val count = p[4]
                val ids = mutableListOf<Int>()
                val named = mutableMapOf<Int, String>()
                var at = 5
                repeat(count) {
                    if (at + 2 > p.size) return@repeat
                    val id = p[at]
                    val length = p[at + 1]
                    val end = minOf(p.size, at + 2 + length)
                    val name = payload.copyOfRange(at + 2, end).decodeToString().trim()
                    ids += id
                    if (name.isNotEmpty()) named[id] = name
                    at = end
                }
                presets = ids
                names = named
            }
            0x5B -> {
                val count = p.getOrNull(2) ?: return false
                bands = (0 until count).mapNotNull { i ->
                    val at = 3 + i * 3
                    if (at + 2 >= p.size) null else p[at] to ((p[at + 1] shl 8) or p[at + 2])
                }
            }
            0x57, 0x59 -> {
                if (p.size < 4) return false
                preset = p[2]
                val count = p[3]
                values = p.drop(4).take(count)
            }
            else -> return false
        }
        return true
    }

    private val clearBassAt: Int?
        get() = bands?.indexOfFirst { it.first == 0x10 && it.second == 1 }?.takeIf { it >= 0 }
            ?: (0.takeIf { bands == null && values.size == 6 })

    private val center: Int?
        get() = steps?.let { (it - 1) / 2 } ?: when (values.size) { 6 -> 10; 10 -> 6; else -> null }

    /** The hertz of each band the person can move, Clear Bass left out; null when the earbuds don't make it clear. */
    private val frequencies: List<Int>?
        get() {
            val info = bands
            if (info != null) {
                val real = info.filterIndexed { i, _ -> i != clearBassAt }
                if (real.size != values.size - (if (clearBassAt != null) 1 else 0) || real.any { it.first != 0x01 }) return null
                return real.map { it.second }
            }
            return when (values.size) {
                6 -> listOf(400, 1000, 2500, 6300, 16000)
                10 -> listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
                else -> null
            }
        }

    /** The presets to offer: the earbuds' own list, without Custom and the user settings, which stand for a curve. */
    private val offered: List<Int>
        get() = presets.ifEmpty { if (type == 0x04 && set == SonySet.Two) emptyList() else DEFAULT_PRESETS }.filter { it !in CURVES }

    fun equalizer(): Equalizer? {
        val current = preset ?: return null
        val freqs = frequencies
        val mid = center
        val custom = customizable && freqs != null && mid != null && values.isNotEmpty()
        val list = offered
        if (list.isEmpty() && !custom) return null
        val gains = if (custom) values.filterIndexed { i, _ -> i != clearBassAt }.map { (it - mid!!).toDouble().coerceIn(Equalizer.MIN_DB, Equalizer.MAX_DB) } else emptyList()
        return Equalizer(
            presetId = if (current in CURVES) Equalizer.CUSTOM else current,
            presets = list.map { EqPreset(it, names[it] ?: presetName(it), emptyList()) },
            frequencies = if (custom) freqs!! else emptyList(),
            left = gains,
            custom = custom,
        )
    }

    /** The message that picks a preset. */
    fun choose(id: Int): ByteArray = bytes(SET_EQ, type, id, 0)

    /** The message that sets the person's own curve, Clear Bass kept as it is. */
    fun curve(gains: List<Double>): ByteArray? {
        val mid = center ?: return null
        val top = (steps ?: (mid * 2 + 1)) - 1
        val bass = clearBassAt
        val out = values.toMutableList()
        var g = 0
        for (i in out.indices) {
            if (i == bass) continue
            val gain = gains.getOrNull(g++) ?: continue
            out[i] = (gain.roundToInt() + mid).coerceIn(0, top)
        }
        val current = preset ?: 0
        val id = when {
            set == SonySet.One -> 0xFF
            type == 0x04 -> if (current in 0xA1..0xA5) current else 0xA1
            else -> if (current in 0xA0..0xA5) current else 0xA0
        }
        return bytes(SET_EQ, type, id, out.size, *out.toIntArray())
    }

    companion object {
        const val SET_EQ = 0x58

        /** Custom, the five user settings, and "none named", which all mean the person's own curve. */
        private val CURVES = (0xA0..0xA5).toSet() + 0xFF

        /** The presets Gadgetbridge offers when a pair doesn't list its own. */
        private val DEFAULT_PRESETS = listOf(0x00) + (0x10..0x17)

        fun presetName(id: Int): String = when (id) {
            0x00 -> "Off"
            0x01 -> "Rock"
            0x02 -> "Pop"
            0x03 -> "Jazz"
            0x04 -> "Dance"
            0x05 -> "EDM"
            0x06 -> "R&B and hip-hop"
            0x07 -> "Acoustic"
            0x10 -> "Bright"
            0x11 -> "Excited"
            0x12 -> "Mellow"
            0x13 -> "Relaxed"
            0x14 -> "Vocal"
            0x15 -> "Treble boost"
            0x16 -> "Bass boost"
            0x17 -> "Speech"
            else -> "Sound $id"
        }
    }
}

/** Builders and readers for the messages the driver sends and gets. */
object Sony {
    /** Sony's control service: set two first, then set one. */
    const val SERVICE_V2 = "956C7B26-D49A-4BA8-B03F-B17D393CB6E2"
    const val SERVICE_V1 = "96CC203E-5068-46AD-B32D-E316F5E069BA"

    const val MODEL = 0x01
    const val FIRMWARE = 0x02

    fun protocolInfo(): ByteArray = bytes(0x00, 0x00)

    fun deviceInfo(kind: Int): ByteArray = bytes(0x04, kind)

    fun supportFunctions(): ByteArray = bytes(0x06, 0x00)

    /** The set a pair speaks, from its answer to [protocolInfo]: four bytes for set one, eight for set two. */
    fun setOf(reply: ByteArray): SonySet? = when {
        reply.isEmpty() || reply[0].toInt() != 0x01 -> null
        reply.size == 4 -> SonySet.One
        reply.size >= 8 -> SonySet.Two
        else -> null
    }

    /** The function numbers in an answer to [supportFunctions]: one byte each in set one, a number and a version in two. */
    fun functions(set: SonySet, reply: ByteArray): Set<Int> {
        if (reply.size < 3 || (reply[0].toInt() and 0xFF) != 0x07) return emptySet()
        val count = reply[2].toInt() and 0xFF
        val width = if (set == SonySet.One) 1 else 2
        return (0 until count).mapNotNull { i -> reply.getOrNull(3 + i * width)?.toInt()?.and(0xFF) }.toSet()
    }

    /** The model name or firmware in a `05` answer: the kind, a length, then that much text. */
    fun text(reply: ByteArray): Pair<Int, String>? {
        if (reply.size < 3 || (reply[0].toInt() and 0xFF) != 0x05) return null
        val length = reply[2].toInt() and 0xFF
        return (reply[1].toInt() and 0xFF) to reply.copyOfRange(3, minOf(reply.size, 3 + length)).decodeToString().trim()
    }

    fun battery(set: SonySet, kind: Int): ByteArray = bytes(if (set == SonySet.One) 0x10 else 0x22, kind)

    fun isBattery(set: SonySet, command: Int): Boolean =
        if (set == SonySet.One) command == 0x11 || command == 0x13 else command == 0x23 || command == 0x25

    /** A battery answer: a level from 0 to 100 and 1 while charging, for one, left and right, or the case. */
    fun batteries(reply: ByteArray): SonyBatteries? {
        if (reply.size < 4) return null
        val p = reply.map { it.toInt() and 0xFF }
        fun level(at: Int, missingAtZero: Boolean): Battery? {
            val percent = p.getOrNull(at) ?: return null
            if (percent > 100 || (missingAtZero && percent == 0)) return null
            return Battery(percent, charging = p.getOrNull(at + 1) == 1)
        }
        return when (p[1]) {
            0x00, 0x08 -> SonyBatteries(overall = level(2, false))
            // A level of 0 for an earbud means it isn't heard from, as Gadgetbridge reads it.
            0x01, 0x09 -> SonyBatteries(left = level(2, true), right = level(4, true))
            0x02, 0x0A -> SonyBatteries(case = level(2, true))
            else -> null
        }
    }

    fun noise(type: Int): ByteArray = bytes(0x66, type)

    fun eqCapability(type: Int): ByteArray = bytes(0x50, type)

    fun eqBands(type: Int): ByteArray = bytes(0x5A, type)

    fun eqSetting(type: Int): ByteArray = bytes(0x56, type)

    fun upscaling(set: SonySet): ByteArray = bytes(0xE6, if (set == SonySet.One) 0x02 else 0x01)

    /** DSEE upscaling from an `E7`/`E9` answer: 1 is automatic, 0 off, in both sets. */
    fun upscalingOn(set: SonySet, reply: ByteArray): Boolean? {
        val p = reply.map { it.toInt() and 0xFF }
        return when {
            set == SonySet.One && p.size >= 4 && p[1] == 0x02 -> p[3] == 1
            set == SonySet.Two && p.size >= 3 && p[1] == 0x01 -> p[2] == 1
            else -> null
        }
    }

    fun setUpscaling(set: SonySet, on: Boolean): ByteArray =
        if (set == SonySet.One) bytes(0xE8, 0x02, 0x00, if (on) 1 else 0) else bytes(0xE8, 0x01, if (on) 1 else 0)

    /** A `F6` request; the system settings (pause when taken off, speak-to-chat, power off, touch keys). */
    fun system(kind: Int): ByteArray = bytes(0xF6, kind)

    /** Pause when taken off, from a `F7`/`F9` answer. In set two 0 is on and 1 off. */
    fun wearPauseOn(set: SonySet, reply: ByteArray): Boolean? {
        val p = reply.map { it.toInt() and 0xFF }
        return when {
            set == SonySet.One && p.size >= 4 && p[1] == 0x03 -> p[3] == 1
            set == SonySet.Two && p.size >= 3 && p[1] == 0x01 -> p[2] == 0
            else -> null
        }
    }

    fun wearPauseKind(set: SonySet): Int = if (set == SonySet.One) 0x03 else 0x01

    fun setWearPause(set: SonySet, on: Boolean): ByteArray =
        if (set == SonySet.One) bytes(0xF8, 0x03, 0x00, if (on) 1 else 0) else bytes(0xF8, 0x01, if (on) 0 else 1)

    /** Speak-to-chat from a `F7`/`F9` answer of [kind]. In set two 0 is on and 1 off, then a preview byte. */
    fun speakToChatOn(set: SonySet, kind: Int, reply: ByteArray): Boolean? {
        val p = reply.map { it.toInt() and 0xFF }
        if (p.size < 4 || p[1] != kind) return null
        return if (set == SonySet.One) p[3] == 1 else p[2] == 0
    }

    /** Sets speak-to-chat; set two keeps the preview byte the earbuds last gave. */
    fun setSpeakToChat(set: SonySet, kind: Int, on: Boolean, preview: Int = 1): ByteArray =
        if (set == SonySet.One) bytes(0xF8, kind, 0x01, if (on) 1 else 0) else bytes(0xF8, kind, if (on) 0 else 1, preview)

    /** The minutes behind each automatic power off number; 0x10 is "when taken off" and 0x11 never. */
    private val MINUTES = mapOf(0x00 to 5, 0x04 to 15, 0x01 to 30, 0x02 to 60, 0x03 to 180)
    const val OFF_WHEN_TAKEN_OFF = 0x10
    const val OFF_NEVER = 0x11

    fun minutes(id: Int): Int? = MINUTES[id]

    fun idFor(minutes: Int): Int? = MINUTES.entries.firstOrNull { it.value == minutes }?.key

    fun autoOffCapability(set: SonySet, kind: Int): ByteArray = bytes(if (set == SonySet.One) 0xF0 else 0x20, kind)

    fun autoOff(set: SonySet, kind: Int): ByteArray = if (set == SonySet.One) system(kind) else bytes(0x26, kind)

    /** The choices in an automatic power off capability answer. */
    fun autoOffChoices(set: SonySet, kind: Int, reply: ByteArray): List<Int>? {
        val p = reply.map { it.toInt() and 0xFF }
        if (p.size < 3 || p[0] != (if (set == SonySet.One) 0xF1 else 0x21) || p[1] != kind) return null
        return p.drop(3).take(p[2])
    }

    /** The active choice and the last time chosen, from an automatic power off answer. */
    fun autoOffSetting(set: SonySet, kind: Int, reply: ByteArray): Pair<Int, Int>? {
        val p = reply.map { it.toInt() and 0xFF }
        return when (set) {
            SonySet.One -> if (p.size >= 5 && (p[0] == 0xF7 || p[0] == 0xF9) && p[1] == kind) p[3] to p[4] else null
            SonySet.Two -> if (p.size >= 4 && (p[0] == 0x27 || p[0] == 0x29) && p[1] == kind) p[2] to p[3] else null
        }
    }

    /** Sets automatic power off; a time is sent as both numbers, the other choices keep the last time. */
    fun setAutoOff(set: SonySet, kind: Int, active: Int, lastTime: Int): ByteArray {
        val time = if (minutes(active) != null) active else lastTime
        return if (set == SonySet.One) bytes(0xF8, kind, 0x01, active, time) else bytes(0x28, kind, active, time)
    }

    /** The function of each touch key, in the earbuds' order (left, then right), from a `F7`/`F9` answer. */
    fun keys(kind: Int, reply: ByteArray): List<Int>? {
        val p = reply.map { it.toInt() and 0xFF }
        if (p.size < 3 || p[1] != kind) return null
        return p.drop(3).take(p[2]).takeIf { it.size == p[2] }
    }

    fun setKeys(kind: Int, presets: List<Int>): ByteArray = bytes(0xF8, kind, presets.size, *presets.toIntArray())

    /** Touch key functions: the noise control switch, play and pause, and nothing. */
    const val KEY_NOISE = 0x00
    const val KEY_PLAYBACK = 0x20
    const val KEY_NONE = 0xFF
}

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
