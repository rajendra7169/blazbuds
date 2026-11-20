/**
 * Every Soundcore model besides the Liberty 4 NC, described as data.
 *
 * Soundcore earbuds and headphones all speak the same framing and share most of their settings, but each model lays
 * out its "everything" answer in its own order and supports its own mix of settings. What each model holds, in what
 * order, and which messages change it was worked out by the OpenSCQ30 project (github.com/Oppzippy/OpenSCQ30, GPL-3.0),
 * whose per-model knowledge this package carries over to Kotlin. The Liberty 4 NC keeps its own driver in
 * [com.blazify.buds.core.soundcore.SoundcoreBuds].
 */
package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.Toggle

/** Reads a message from the earbuds one field at a time, from the front. */
class ByteReader(private val data: ByteArray, var at: Int = 0) {
    val left: Int get() = data.size - at

    fun u8(): Int {
        if (at >= data.size) throw ShortMessage()
        return data[at++].toInt() and 0xFF
    }

    fun bool(): Boolean = u8() == 1

    fun ints(count: Int): IntArray = IntArray(count) { u8() }

    fun u16(): Int = u8() or (u8() shl 8)

    fun u32BigEndian(): Long = (u8().toLong() shl 24) or (u8().toLong() shl 16) or (u8().toLong() shl 8) or u8().toLong()
}

/** The earbuds' message ended before everything the model should send was read. */
class ShortMessage : Exception("The message from the earbuds ended early")

/** A message that doesn't fit the model it was read as. */
class WrongShape(what: String) : Exception(what)

/**
 * Ten (or eight) band gains in tenths of a decibel, for one or two channels, plus the preset they came from. A channel
 * the earbuds don't have reads as null.
 */
class EqState(var presetId: Int, var channels: List<IntArray?>) {
    val first: IntArray? get() = channels.firstOrNull { it != null }
}

/** The hearing profile some models keep next to their equalizer; only echoed back when the equalizer changes. */
class HearIdState(
    var enabled: Boolean,
    val curves: List<IntArray?>,
    val time: Long,
    val type: Int = 0,
    val genre: Int = 0,
    val customCurves: List<IntArray?>,
)

/** One touch as the earbuds store it: an optional "on" byte and the action byte, both kept raw to send back as they were. */
class ButtonStatus(var enabled: Int?, var action: Int) {
    fun copy() = ButtonStatus(enabled, action)
}

/** Everything one "everything" answer held, still close to the wire so writes can echo back what they must. */
class Parsed {
    /** Which earbud talks to the phone: 0 left, 1 right. */
    var host = 0
    var both = true
    var hasTws = false
    var leftLevel: Int? = null
    var rightLevel: Int? = null
    var leftCharging = false
    var rightCharging = false
    var level: Int? = null
    var charging = false
    var caseLevel: Int? = null
    var firmwareLeft: String? = null
    var firmwareRight: String? = null
    var serial: String? = null
    var eq: EqState? = null
    var gender = 0
    var age = 0xFF
    var hearId: HearIdState? = null
    /** Touch settings, one list per group the model keeps (most have one). */
    val buttons = mutableListOf<MutableList<ButtonStatus>>()
    var sound: IntArray? = null
    var cycle: Int? = null
    val switches = linkedMapOf<Toggle, Boolean>()
    var powerOff: IntArray? = null
    var limit: IntArray? = null
    var twoDevices: Boolean? = null
    /** Anything a single model keeps of its own, by name. */
    val extra = mutableMapOf<String, IntArray>()
}

/** One field in a model's "everything" answer. A model's layout is a list of these, in the order the earbuds send them. */
sealed interface Piece {
    fun read(r: ByteReader, p: Parsed)

    /** Which earbud is the main one, and whether both are out and linked. */
    data object Tws : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            val host = r.u8()
            if (host > 1) throw WrongShape("host earbud $host")
            p.host = host
            p.both = r.bool()
            p.hasTws = true
        }
    }

    /** Both earbuds' levels, then whether each is charging. */
    data object DualBattery : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.leftLevel = r.u8()
            p.rightLevel = r.u8()
            p.leftCharging = r.u8() == 1
            p.rightCharging = r.u8() == 1
        }
    }

    /** Both earbuds' levels with no charging bytes. */
    data object DualLevel : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.leftLevel = r.u8()
            p.rightLevel = r.u8()
        }
    }

    /** Headphones and one-piece devices: one level, then whether it is charging. */
    data object SingleBattery : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.level = r.u8()
            p.charging = r.u8() == 1
        }
    }

    /** One level with no charging byte. */
    data object SingleLevel : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.level = r.u8()
        }
    }

    data object CaseLevel : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.caseLevel = r.u8()
        }
    }

    /** One firmware version, five characters such as "02.61". */
    data object Firmware : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.firmwareLeft = text(r.ints(5))
        }
    }

    /** A firmware version for each earbud; zeros or 0xFF for one that isn't there. */
    data object DualFirmware : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.firmwareLeft = text(r.ints(5))
            p.firmwareRight = text(r.ints(5))
        }
    }

    data object Serial : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.serial = text(r.ints(16))
        }
    }

    /** The preset id and each channel's bands; a channel whose first byte is 0xFF isn't there. */
    data class Eq(val channels: Int, val bands: Int) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            val id = r.u16()
            p.eq = EqState(id, List(channels) { bandsOrNull(r, bands) })
        }
    }

    data object Gender : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.gender = r.u8()
        }
    }

    /** The age range for the hearing profile; 0xFF on models without one. */
    data object Age : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.age = r.u8()
        }
    }

    /** A hearing profile without the custom part: on, two curves, when it was made. */
    data class BasicHearId(val bands: Int) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            val on = r.bool()
            val curves = List(2) { bandsOrNull(r, bands) }
            val time = r.u32BigEndian()
            p.hearId = HearIdState(on, curves, time, 0, 0, List(2) { null })
        }
    }

    /** The full hearing profile; [genreAtEnd] for the models that send the music genre last, in two bytes. */
    data class CustomHearId(val channels: Int, val bands: Int, val genreAtEnd: Boolean = false) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            val on = r.bool()
            val curves = List(channels) { bandsOrNull(r, bands) }
            val time = r.u32BigEndian()
            val type = r.u8()
            if (genreAtEnd) {
                val custom = List(channels) { bandsOrNull(r, bands) }
                val genre = r.u16()
                p.hearId = HearIdState(on, curves, time, type, genre, custom)
            } else {
                val genre = r.u8()
                val custom = List(channels) { bandsOrNull(r, bands) }
                p.hearId = HearIdState(on, curves, time, type, genre, custom)
            }
        }
    }

    /** One group of touch settings, laid out by [layout]. */
    data class Buttons(val layout: ButtonLayout) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.buttons += layout.order.mapIndexed { i, slot ->
                layout.gaps[i]?.let { r.ints(it) }
                val setup = layout.setup(slot.kind)
                val enabled = if (setup.enabled == EnabledKind.None) null else r.u8()
                ButtonStatus(enabled, r.u8())
            }.toMutableList()
        }
    }

    /** The listening mode and its settings, [size] bytes read by the model's [SoundScheme]. */
    data class Sound(val size: Int) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.sound = r.ints(size)
        }
    }

    /** Which modes a touch cycles through. */
    data object Cycle : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.cycle = r.u8()
        }
    }

    /** A switch that is simply on (1) or off. */
    data class Switch(val toggle: Toggle) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.switches[toggle] = r.bool()
        }
    }

    data object PowerOff : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.powerOff = r.ints(2)
        }
    }

    /** The volume cap: on, decibels, and how often the earbuds measure. */
    data object Limit : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.limit = r.ints(3)
        }
    }

    /** Whether the earbuds keep two devices connected. */
    data object TwoDevices : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.twoDevices = r.bool()
        }
    }

    /** Bytes the app doesn't use. */
    data class Skip(val count: Int) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            r.ints(count)
        }
    }

    /** Bytes one model keeps of its own, saved under [name] for its own code. */
    data class Own(val name: String, val count: Int) : Piece {
        override fun read(r: ByteReader, p: Parsed) {
            p.extra[name] = r.ints(count)
        }
    }

    /** Fields that only newer firmware sends: read when they are there, skipped as a whole when the message ends first. */
    data class Optional(val pieces: List<Piece>) : Piece {
        constructor(vararg pieces: Piece) : this(pieces.toList())

        override fun read(r: ByteReader, p: Parsed) {
            val start = r.at
            try {
                pieces.forEach { it.read(r, p) }
            } catch (_: ShortMessage) {
                r.at = start
            }
        }
    }

    companion object {
        /** Reads text the earbuds send padded with zeros or 0xFF; null when there is none. */
        fun text(bytes: IntArray): String? {
            if (bytes.all { it == 0 || it == 0xFF }) return null
            return bytes.takeWhile { it != 0 && it != 0xFF }.map { it.toChar() }.joinToString("")
        }

        fun bandsOrNull(r: ByteReader, bands: Int): IntArray? {
            val raw = r.ints(bands)
            // As OpenSCQ30 does, a gain above +13.4 dB is held at that.
            return if (raw[0] == 0xFF) null else IntArray(bands) { (raw[it] - 120).coerceAtMost(134) }
        }
    }
}

/** Reads a whole "everything" answer by [layout]; throws when it doesn't fit. */
fun parse(layout: List<Piece>, body: ByteArray): Parsed {
    val parsed = Parsed()
    val reader = ByteReader(body)
    layout.forEach { it.read(reader, parsed) }
    return parsed
}
