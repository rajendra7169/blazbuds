package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.AutoPowerOff
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Multipoint
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.VolumeLimit
import com.blazify.buds.core.soundcore.Packet

/** One message to send, and what to note in the parsed state once the earbuds confirm it. */
class Step(val packet: Packet, val apply: (Parsed) -> Unit = {})

/**
 * How a model counts its battery: [max] steps for full, and an [offset] added first (some count 0 to 9 for 10% to
 * 100%). 255 means that side isn't there.
 */
data class Scale(val max: Int, val offset: Int = 0) {
    fun percent(raw: Int?): Int? {
        if (raw == null || raw == 255) return null
        return ((raw + offset) * 100 / max).coerceIn(0, 100)
    }
}

/** How a model keeps the modes a touch cycles through. */
enum class CycleKind {
    None,

    /** One byte: 1 cancelling, 2 transparency, 4 normal. */
    Plain,

    /** The same in each half: the low one with both earbuds out, the high one with one alone. */
    Tws,
}

/** What only one model has: its own fields in the state, its own messages for some changes, its own pushed updates. */
interface ModelExtras {
    /** Adds what only this model has to the state. */
    fun read(p: Parsed, state: BudsState): BudsState = state

    /** Messages for a change this model makes its own way; null to leave it to the shared code. */
    fun steps(p: Parsed, change: Change, wanted: BudsState): List<Step>? = null

    /** An update only this model pushes; true when it was taken in. */
    fun pushed(p: Parsed, packet: Packet): Boolean = false
}

/**
 * A Soundcore model, as data: its names, how to reach it, the order of its "everything" answer and which settings it
 * has. Everything the app shows and changes for it comes from this.
 */
class SoundcoreModel(
    /** Soundcore's model number, such as "A3951". */
    val code: String,
    /** The name it is sold under, without "soundcore". */
    val name: String,
    /** Other names the same model goes by, or that share its protocol. */
    val aliases: List<String> = emptyList(),
    /**
     * The control channel's service, exactly as the model announces it: most newer models have their own Soundcore
     * one (0cf12d31-…), older ones the plain serial port. The last digits don't always follow the model number, so
     * each model spells its own out.
     */
    val service: String = SERIAL_PORT,
    val layout: List<Piece>,
    val battery: Scale = Scale(5),
    val case: Scale? = null,
    val sound: SoundScheme? = null,
    val equalizer: EqSetup? = null,
    /** One layout for each touch group in the state, in order; the app shows the first. */
    val buttons: List<ButtonLayout> = emptyList(),
    /** The model can put its touches back as they came. */
    val resetButtons: Boolean = false,
    val cycle: CycleKind = CycleKind.None,
    /** Each switch the app can change, with the command that sets it. */
    val switches: Map<Toggle, Int> = emptyMap(),
    /** The minutes for each auto power-off choice, by the index the earbuds use. */
    val powerOff: List<Int>? = null,
    val volumeLimit: Boolean = false,
    val twoDevices: Boolean = false,
    /** The firmware and serial number come in their own answer, not in the state. */
    val askFirmware: Boolean = false,
    /** LDAC's switch comes in its own answer. */
    val askLdac: Boolean = false,
    val extras: ModelExtras? = null,
) {
    /** Reads the "everything" answer; throws when it doesn't fit this model. */
    fun parse(body: ByteArray): Parsed = parse(layout, body)

    /** The state in the app's terms. */
    fun state(p: Parsed, name: String, hosts: List<Host> = emptyList()): BudsState {
        val both = !p.hasTws || p.both
        val single = p.level != null && p.leftLevel == null
        val left = if (single) null else battery.percent(p.leftLevel)?.let { Battery(it, p.leftCharging) }
        val right = if (single) null else battery.percent(p.rightLevel)?.let { Battery(it, p.rightCharging) }
        val firmware = when {
            p.firmwareLeft == null -> p.firmwareRight
            p.firmwareRight == null || p.firmwareRight == p.firmwareLeft -> p.firmwareLeft
            else -> "${p.firmwareLeft} / ${p.firmwareRight}"
        }
        val cycleModes = p.cycle?.let { byte ->
            val bits = if (cycle == CycleKind.Tws && !both) byte shr 4 else byte and 0x0F
            buildSet {
                if (bits and 1 != 0) add(ListeningMode.NoiseCancelling)
                if (bits and 2 != 0) add(ListeningMode.Transparency)
                if (bits and 4 != 0) add(ListeningMode.Normal)
            }
        }
        val sound = sound?.let { scheme ->
            val bytes = p.sound ?: return@let null
            val read = scheme.read(bytes)
            read.copy(cycle = cycleModes ?: read.cycle, options = scheme.options(cycle != CycleKind.None && cycleModes != null))
        }
        val state = BudsState(
            name = name,
            model = code,
            link = Link.Connected,
            left = left,
            right = right,
            case = case?.percent(p.caseLevel)?.takeIf { it > 0 }?.let { Battery(it) },
            overall = if (single) battery.percent(p.level)?.let { Battery(it, p.charging) } else null,
            sound = sound,
            equalizer = equalizer?.let { setup -> p.eq?.let { setup.read(it) } },
            controls = buttons.firstOrNull()?.let { layout -> p.buttons.firstOrNull()?.let { layout.controls(it, both) } },
            toggles = p.switches.filterKeys { it in switches },
            multipoint = if (twoDevices) p.twoDevices?.let { Multipoint(it, hosts) } else null,
            autoPowerOff = powerOff?.let { minutes -> p.powerOff?.let { AutoPowerOff(it[0] == 1, minutes.getOrElse(it[1]) { minutes.first() }, minutes) } },
            volumeLimit = if (volumeLimit) p.limit?.let { VolumeLimit(it[0] == 1, it[1].takeIf { db -> db in 75..100 } ?: 85) } else null,
            firmware = firmware,
            serial = p.serial,
            bothConnected = both,
        )
        return extras?.read(p, state) ?: state
    }

    /**
     * The messages that make [change], given what the earbuds last said ([p]) and the state the screen now shows
     * ([wanted], with the change already in it). Null when this model can't make that change.
     */
    fun steps(p: Parsed, change: Change, wanted: BudsState): List<Step>? {
        extras?.steps(p, change, wanted)?.let { return it }
        val both = !p.hasTws || p.both
        return when (change) {
            is Change.Mode, is Change.Cancelling, is Change.TransparencyKind, is Change.Wind, is Change.Environment -> {
                val scheme = sound ?: return null
                val now = p.sound ?: return null
                val target = scheme.target(now, wanted.sound ?: return null)
                scheme.steps(now, target).map { bytes -> Step(Packet(scheme.command, bytesOf(bytes.toList()))) { it.sound = bytes.copyOf() } }
            }
            is Change.ModeCycle -> {
                val old = p.cycle ?: return null
                var bits = 0
                if (ListeningMode.NoiseCancelling in change.modes) bits = bits or 1
                if (ListeningMode.Transparency in change.modes) bits = bits or 2
                if (ListeningMode.Normal in change.modes) bits = bits or 4
                val byte = when (cycle) {
                    CycleKind.None -> return null
                    CycleKind.Plain -> bits
                    CycleKind.Tws -> if (both) (old and 0xF0) or bits else (old and 0x0F) or (bits shl 4)
                }
                listOf(Step(Packet(CYCLE_SET, byteArrayOf(byte.toByte()))) { it.cycle = byte })
            }
            is Change.Preset, is Change.Bands -> {
                val setup = equalizer ?: return null
                val eq = p.eq ?: return null
                if (setup.bothNeeded && !both) return null
                val id = if (change is Change.Preset) change.id else com.blazify.buds.core.model.Equalizer.CUSTOM
                val bandCount = eq.channels.firstNotNullOfOrNull { it?.size } ?: setup.frequencies.size
                val tenths = setup.gains(id, (change as? Change.Bands)?.left, bandCount) ?: return null
                setup.steps(p, id, tenths)
            }
            is Change.Switch -> {
                val command = switches[change.toggle] ?: return null
                val value = if (change.on) 1 else 0
                listOf(Step(Packet(command, byteArrayOf(value.toByte()))) { it.switches[change.toggle] = change.on })
            }
            is Change.Touch -> {
                val layout = buttons.firstOrNull() ?: return null
                val statuses = p.buttons.firstOrNull() ?: return null
                layout.touchSteps(0, statuses, both, change.ear, change.press, change.action)
            }
            Change.ResetControls -> if (resetButtons) listOf(Step(Packet(ButtonLayout.RESET))) else null
            is Change.PowerOff -> {
                val minutes = powerOff ?: return null
                val index = minutes.indexOf(change.minutes).takeIf { it >= 0 } ?: p.powerOff?.get(1) ?: 0
                val body = intArrayOf(if (change.enabled) 1 else 0, index)
                listOf(Step(Packet(POWER_OFF_SET, bytesOf(body.toList()))) { it.powerOff = body })
            }
            is Change.SafeVolume -> {
                if (!volumeLimit) return null
                val db = (change.decibels / 5 * 5).coerceIn(75, 100)
                val enabled = if (change.enabled) 1 else 0
                listOf(Step(Packet(LIMIT_SET, bytesOf(listOf(enabled, db)))) { it.limit = intArrayOf(enabled, db, it.limit?.getOrNull(2) ?: 0) })
            }
            is Change.TwoDevices -> {
                if (!twoDevices) return null
                listOf(Step(Packet(TWO_DEVICES_SET, byteArrayOf(if (change.on) 1 else 0))) { it.twoDevices = change.on })
            }
            else -> null
        }
    }

    override fun toString() = "$code $name"

    companion object {
        const val SERIAL_PORT = "00001101-0000-1000-8000-00805f9b34fb"

        val STATE = Packet.command(0x01, 0x01)
        val TWS = Packet.command(0x01, 0x02)
        val BATTERY = Packet.command(0x01, 0x03)
        val CHARGING = Packet.command(0x01, 0x04)
        val FIRMWARE = Packet.command(0x01, 0x05)
        val VOICE_PROMPT_UPDATE = Packet.command(0x01, 0x10)
        val GAMING_UPDATE = Packet.command(0x01, 0x11)
        val LDAC = Packet.command(0x01, 0x7F)
        val CYCLE_SET = Packet.command(0x06, 0x82)
        val POWER_OFF_SET = Packet.command(0x01, 0x86)
        val LIMIT_SET = Packet.command(0x20, 0x82)
        val TWO_DEVICES_SET = Packet.command(0x0B, 0x84)
        val DEVICES = Packet.command(0x0B, 0x01)

        // The commands that set each switch, shared by most models.
        val TOUCH_TONE = Packet.command(0x01, 0x83)
        val WEARING_DETECTION = Packet.command(0x01, 0x81)
        val AUTO_PLAY_PAUSE = Packet.command(0x01, 0x81)
        val GAMING_MODE = Packet.command(0x01, 0x87)
        val SOUND_LEAK = Packet.command(0x10, 0x86)
        val SURROUND = Packet.command(0x02, 0x86)
        val WEARING_TONE = Packet.command(0x01, 0x8C)
        val TOUCH_LOCK = Packet.command(0x04, 0x86)
        val LOW_BATTERY = Packet.command(0x10, 0x82)
        val VOICE_PROMPT = Packet.command(0x01, 0x90)
        val LDAC_SET = Packet.command(0x01, 0xFF)

        /** Turns every touch off: the earbuds call it "disable all buttons", which is what Touch lock means here. */
        val DISABLE_BUTTONS = Packet.command(0x10, 0x94)

        val HALF_HOURS = listOf(30, 60, 90, 120)
        val TEN_TO_SIXTY = listOf(10, 20, 30, 60)
        val LONG_WAITS = listOf(30, 60, 180, 300)
    }
}

/** Takes in an update the earbuds push by themselves; true when [p] changed. */
fun SoundcoreModel.pushed(p: Parsed, packet: Packet): Boolean {
    if (extras?.pushed(p, packet) == true) return true
    val body = packet.body.map { it.toInt() and 0xFF }
    when (packet.command) {
        SoundcoreModel.TWS -> {
            if (body.size < 2 || body[0] > 1) return false
            p.host = body[0]
            p.both = body[1] == 1
        }
        SoundcoreModel.BATTERY -> when {
            p.leftLevel != null && body.size >= 2 -> {
                p.leftLevel = body[0]
                p.rightLevel = body[1]
            }
            p.level != null && body.isNotEmpty() -> p.level = body[0]
            else -> return false
        }
        SoundcoreModel.CHARGING -> when {
            p.leftLevel != null && body.size >= 2 -> {
                p.leftCharging = body[0] == 1
                p.rightCharging = body[1] == 1
            }
            p.level != null && body.isNotEmpty() -> p.charging = body[0] == 1
            else -> return false
        }
        SoundScheme.SOUND_UPDATE -> {
            val scheme = sound ?: return false
            if (body.size < scheme.size) return false
            p.sound = body.take(scheme.size).toIntArray()
        }
        SoundcoreModel.LDAC -> if (Toggle.Ldac in switches && body.isNotEmpty()) p.switches[Toggle.Ldac] = body[0] == 1 else return false
        SoundcoreModel.GAMING_UPDATE -> if (Toggle.GamingMode in switches && body.isNotEmpty()) p.switches[Toggle.GamingMode] = body[0] == 1 else return false
        SoundcoreModel.VOICE_PROMPT_UPDATE -> if (Toggle.VoicePrompt in switches && body.isNotEmpty()) p.switches[Toggle.VoicePrompt] = body[0] == 1 else return false
        SoundcoreModel.FIRMWARE -> readFirmware(p, packet.body)
        else -> return false
    }
    return true
}

/** The firmware answer (01 05): each earbud's version, then the serial number. */
fun readFirmware(p: Parsed, body: ByteArray) {
    val r = ByteReader(body)
    runCatching {
        Piece.DualFirmware.read(r, p)
        Piece.Serial.read(r, p)
    }
}
