package com.blazify.buds.core.soundcore

import com.blazify.buds.core.model.AutoPowerOff
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Multipoint
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.model.Travel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * The Liberty 4 NC (A3947): where each setting sits in the 159 bytes the earbuds send for "everything", and the
 * messages that change each one. Pure functions over bytes, so it is all tested without earbuds.
 */
object A3947 {
    const val SERVICE = "0cf12d31-fac3-4553-bd80-d6832e7b3947"
    const val STATE_LENGTH = 158

    // Where things sit in the state.
    const val HOST = 0
    const val BOTH = 1
    const val LEFT_LEVEL = 2
    const val RIGHT_LEVEL = 3
    const val LEFT_CHARGING = 4
    const val RIGHT_CHARGING = 5
    const val FIRMWARE_LEFT = 6
    const val FIRMWARE_RIGHT = 11
    const val SERIAL = 16
    const val PRESET = 37
    const val EQ_LEFT = 39
    const val EQ_RIGHT = 49
    const val HEAR_ID = 60
    const val HEAR_ID_GENRE = 106
    const val BUTTONS = 109
    const val CYCLE = 125
    const val SOUND = 126
    const val CASE_LEVEL = 139
    const val LEAK = 141
    const val GAMING = 143
    const val TOUCH_TONE = 144
    const val TWO_DEVICES = 147
    const val SURROUND = 148
    const val WEAR_DETECTION = 152
    const val WEARING_TONE = 153
    const val LIMIT = 149
    const val LIMIT_DB = 150
    const val POWER_OFF = 154
    const val POWER_OFF_TIME = 155
    const val TOUCH_LOCK = 156
    const val LOW_BATTERY = 157

    /** Each switch: its place in the state and the command that sets it. */
    val TOGGLES: Map<Toggle, Pair<Int, Int>> = mapOf(
        Toggle.WearDetection to (WEAR_DETECTION to Packet.command(0x01, 0x81)),
        Toggle.TouchTone to (TOUCH_TONE to Packet.command(0x01, 0x83)),
        Toggle.WearingTone to (WEARING_TONE to Packet.command(0x01, 0x8C)),
        Toggle.Surround to (SURROUND to Packet.command(0x02, 0x86)),
        Toggle.TouchLock to (TOUCH_LOCK to Packet.command(0x04, 0x86)),
        Toggle.LowBatteryPrompt to (LOW_BATTERY to Packet.command(0x10, 0x82)),
        Toggle.GamingMode to (GAMING to Packet.command(0x10, 0x85)),
        Toggle.SoundLeakCompensation to (LEAK to Packet.command(0x10, 0x86)),
    )

    val STATE = Packet.command(0x01, 0x01)
    val TWS = Packet.command(0x01, 0x02)
    val BATTERY = Packet.command(0x01, 0x03)
    val CHARGING = Packet.command(0x01, 0x04)
    val FIRMWARE = Packet.command(0x01, 0x05)
    val SOUND_UPDATE = Packet.command(0x06, 0x01)
    val SOUND_SET = Packet.command(0x06, 0x81)
    val CYCLE_SET = Packet.command(0x06, 0x82)
    val LDAC = Packet.command(0x01, 0x7F)
    val LDAC_SET = Packet.command(0x01, 0xFF)
    val EQ_SET = Packet.command(0x03, 0x87)
    val BUTTON_SET = Packet.command(0x04, 0x81)
    val BUTTON_RESET = Packet.command(0x04, 0x82)
    val BUTTON_ENABLE = Packet.command(0x04, 0x83)
    val POWER_OFF_SET = Packet.command(0x01, 0x86)
    val DEVICES = Packet.command(0x0B, 0x01)
    val DISCONNECT = Packet.command(0x0B, 0x81)
    val CONNECT = Packet.command(0x0B, 0x82)
    val FORGET = Packet.command(0x0B, 0x83)
    val TWO_DEVICES_SET = Packet.command(0x0B, 0x84)
    val LIMIT_SET = Packet.command(0x20, 0x82)
    /** Not yet confirmed on this model: from the Soundcore family's shared protocol. */
    val RING = Packet.command(0x10, 0x89)
    val FIT_TEST = Packet.command(0x09, 0x01)
    val HEARING_TEST = Packet.command(0x03, 0x83)

    val FREQUENCIES = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)
    val POWER_OFF_MINUTES = listOf(10, 20, 30, 60)

    /** Soundcore's own presets, in tenths of a decibel over all ten bands. */
    val PRESETS: List<Pair<String, IntArray>> = listOf(
        "Signature" to intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120),
        "Acoustic" to intArrayOf(40, 10, 20, 20, 40, 40, 40, 20, 0, -120),
        "Bass booster" to intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120),
        "Bass reducer" to intArrayOf(-40, -30, -10, 0, 0, 0, 0, 0, 0, -120),
        "Classical" to intArrayOf(30, 30, -20, -20, 0, 20, 30, 40, 0, -120),
        "Podcast" to intArrayOf(-30, 20, 40, 40, 30, 20, 0, -20, 0, -120),
        "Dance" to intArrayOf(20, -30, -10, 10, 20, 20, 10, -30, 0, -120),
        "Deep" to intArrayOf(20, 10, 30, 30, 20, -20, -40, -50, 0, -120),
        "Electronic" to intArrayOf(30, 20, -20, 20, 10, 20, 30, 30, 0, -120),
        "Flat" to intArrayOf(-20, -20, -10, 0, 0, 0, -20, -20, 0, -120),
        "Hip-hop" to intArrayOf(20, 30, -10, -10, 20, -10, 20, 30, 0, -120),
        "Jazz" to intArrayOf(20, 20, -20, -20, 0, 20, 30, 40, 0, -120),
        "Latin" to intArrayOf(0, 0, -20, -20, -20, 0, 30, 50, 0, -120),
        "Lounge" to intArrayOf(-10, 20, 40, 30, 0, -20, 20, 10, 0, -120),
        "Piano" to intArrayOf(0, 30, 30, 20, 40, 50, 30, 40, 0, -120),
        "Pop" to intArrayOf(-10, 10, 30, 30, 10, -10, -20, -30, 0, -120),
        "R&B" to intArrayOf(60, 20, -20, -20, 20, 30, 30, 40, 0, -120),
        "Rock" to intArrayOf(30, 20, -10, -10, 10, 30, 40, 50, 0, -120),
        "Small speakers" to intArrayOf(40, 30, 10, 0, -20, -30, -40, -40, 0, -120),
        "Spoken word" to intArrayOf(-30, -20, 10, 20, 20, 10, 0, -30, 0, -120),
        "Treble booster" to intArrayOf(-20, -20, -20, -10, 10, 20, 20, 40, 0, -120),
        "Treble reducer" to intArrayOf(0, 0, 0, -20, -30, -40, -40, -60, 0, -120),
    )

    private const val CUSTOM = 0xFEFE

    /** The buttons in the order the state lists them, with the id the set command uses. */
    private val BUTTON_ORDER: List<Triple<Ear, Press, Int>> = listOf(
        Triple(Ear.Left, Press.Single, 2), Triple(Ear.Right, Press.Single, 2),
        Triple(Ear.Left, Press.Double, 0), Triple(Ear.Right, Press.Double, 0),
        Triple(Ear.Left, Press.Long, 1), Triple(Ear.Right, Press.Long, 1),
        Triple(Ear.Left, Press.Triple, 5), Triple(Ear.Right, Press.Triple, 5),
    )

    private val ACTIONS: List<Pair<Int, TouchAction>> = listOf(
        0 to TouchAction.VolumeUp,
        1 to TouchAction.VolumeDown,
        2 to TouchAction.PreviousSong,
        3 to TouchAction.NextSong,
        4 to TouchAction.ListeningMode,
        5 to TouchAction.VoiceAssistant,
        6 to TouchAction.PlayPause,
        0xF to TouchAction.None,
    )

    fun isState(body: ByteArray): Boolean = body.size >= STATE_LENGTH && (body[HOST].u() <= 1)

    /** What the earbuds report, in the app's own terms. */
    fun read(raw: ByteArray, ldac: Boolean?, hosts: List<Host>, name: String): BudsState {
        val both = raw[BOTH].toInt() == 1
        val left = bud(raw[FIRMWARE_LEFT], raw[LEFT_LEVEL], raw[LEFT_CHARGING])
        val right = bud(raw[FIRMWARE_RIGHT], raw[RIGHT_LEVEL], raw[RIGHT_CHARGING])
        val fwLeft = text(raw, FIRMWARE_LEFT, 5)
        val fwRight = text(raw, FIRMWARE_RIGHT, 5)
        val firmware = when {
            fwLeft == null -> fwRight
            fwRight == null || fwRight == fwLeft -> fwLeft
            else -> "$fwLeft / $fwRight"
        }
        val toggles = TOGGLES.mapValues { (_, at) -> raw[at.first].toInt() == 1 }.toMutableMap()
        if (ldac != null) toggles[Toggle.Ldac] = ldac
        return BudsState(
            name = name,
            model = "A3947",
            link = Link.Connected,
            left = left,
            right = right,
            // The earbuds only hear from the case while one of them sits in it; until then they say 0, which means
            // "don't know", not empty.
            case = level(raw[CASE_LEVEL]).takeIf { it > 0 }?.let { Battery(it) },
            sound = sound(raw, SOUND, raw[CYCLE].u()),
            equalizer = equalizer(raw),
            controls = controls(raw, both),
            toggles = toggles,
            multipoint = Multipoint(raw[TWO_DEVICES].toInt() == 1, hosts),
            volumeLimit = com.blazify.buds.core.model.VolumeLimit(raw[LIMIT].toInt() == 1, raw[LIMIT_DB].u().takeIf { it in 75..100 } ?: 85),
            autoPowerOff = AutoPowerOff(raw[POWER_OFF].toInt() == 1, POWER_OFF_MINUTES.getOrElse(raw[POWER_OFF_TIME].u()) { 30 }, POWER_OFF_MINUTES),
            firmware = firmware,
            serial = text(raw, SERIAL, 16),
            bothConnected = both,
            // The family's find command plays nothing on this model; hidden until its own is known.
            canRing = false,
            canFitTest = true,
            earPrint = earPrint(raw),
        )
    }

    private fun bud(firmware: Byte, level: Byte, charging: Byte): Battery? {
        // A bud that isn't there reports its firmware as zeros or 0xFF.
        if (firmware.toInt() == 0 || firmware.u() == 0xFF) return null
        return Battery(level(level), charging.toInt() == 1)
    }

    private fun level(byte: Byte) = byte.u().coerceIn(0, 5) * 20

    private fun text(raw: ByteArray, at: Int, length: Int): String? {
        val bytes = raw.copyOfRange(at, at + length)
        if (bytes.all { it.toInt() == 0 || it.u() == 0xFF }) return null
        return bytes.decodeToString()
    }

    fun sound(bytes: ByteArray, at: Int, cycle: Int): Sound = Sound(
        mode = when (bytes[at].u()) {
            1 -> ListeningMode.Transparency
            2 -> ListeningMode.Normal
            else -> ListeningMode.NoiseCancelling
        },
        cancelStyle = when (bytes[at + 3].u()) {
            1 -> CancelStyle.Adaptive
            2 -> CancelStyle.Travel
            else -> CancelStyle.Manual
        },
        manualLevel = ((bytes[at + 1].u() shr 4) and 0x0F).coerceIn(1, 5),
        transparencyStyle = if (bytes[at + 2].u() == 1) TransparencyStyle.Voice else TransparencyStyle.Full,
        windReduction = bytes[at + 4].u() and 1 == 1,
        environmentDetection = bytes[at + 5].toInt() == 1,
        travel = Travel.entries.getOrElse(bytes[at + 6].u()) { Travel.Plane },
        cycle = buildSet {
            if (cycle and 1 != 0) add(ListeningMode.NoiseCancelling)
            if (cycle and 2 != 0) add(ListeningMode.Transparency)
            if (cycle and 4 != 0) add(ListeningMode.Normal)
        },
    )

    private fun equalizer(raw: ByteArray): Equalizer {
        val id = raw[PRESET].u() or (raw[PRESET + 1].u() shl 8)
        // The second channel reads as absent on this model; both ears share the first.
        val channel = if (raw[EQ_LEFT].u() == 0xFF) EQ_RIGHT else EQ_LEFT
        val gains = (0 until 8).map { (raw[channel + it].u() - 120) / 10.0 }
        return Equalizer(
            presetId = if (id == CUSTOM) Equalizer.CUSTOM else id,
            presets = PRESETS.mapIndexed { i, (name, tenths) -> EqPreset(i, name, tenths.take(8).map { it / 10.0 }) },
            frequencies = FREQUENCIES,
            left = gains,
        )
    }

    private fun controls(raw: ByteArray, both: Boolean): TouchControls {
        val actions = BUTTON_ORDER.mapIndexed { i, (ear, press, _) ->
            val byte = raw[BUTTONS + i * 2 + 1].u()
            val id = if (both) byte and 0x0F else byte shr 4
            (ear to press) to (ACTIONS.firstOrNull { it.first == id }?.second ?: TouchAction.None)
        }.toMap()
        return TouchControls(actions, ACTIONS.map { it.second })
    }

    /** The hearing profile the earbuds hold (Soundcore calls it HearID): on or off, a curve for each ear, when made. */
    fun earPrint(raw: ByteArray): com.blazify.buds.core.model.EarPrintState {
        fun curve(at: Int): List<Double>? =
            if (raw[at].u() == 0xFF) null else (0 until 8).map { (raw[at + it].u() - 120) / 10.0 }
        val left = curve(HEAR_ID + 1) ?: List(8) { 0.0 }
        val right = curve(HEAR_ID + 11) ?: left
        val time = (raw[81].u().toLong() shl 24) or (raw[82].u().toLong() shl 16) or (raw[83].u().toLong() shl 8) or raw[84].u().toLong()
        return com.blazify.buds.core.model.EarPrintState(raw[HEAR_ID].toInt() == 1, left, right, time)
    }

    /**
     * The message that plays an EarPrint, laid out as Soundcore's own app sends it (read from a recording of it): the
     * current sound kept, the hearing profile switched on with a curve for each ear, and the limiter's curve for it.
     * [trying] marks a sound being compared rather than kept.
     */
    fun earPrintBody(raw: ByteArray, left: List<Double>, right: List<Double>, trying: Boolean, madeAt: Long): ByteArray {
        fun tenths(curve: List<Double>) = IntArray(10) { if (it < 8) kotlin.math.round(curve.getOrElse(it) { 0.0 } * 10).toInt().coerceIn(-120, 134) else -120 }
        val l = tenths(left)
        val r = tenths(right)
        val bands = if (raw[EQ_LEFT].u() == 0xFF) ByteArray(10) { 0x78 } else raw.copyOfRange(EQ_LEFT, EQ_LEFT + 10)
        fun curveBytes(t: IntArray) = ByteArray(10) { (t[it] + 120).toByte() }
        fun limiter(t: IntArray) = drc(t).let { values -> ByteArray(10) { (values[it] + 120).toByte() }.also { it[8] = 0x6C; it[9] = 0 } }
        val out = ArrayList<Byte>(93)
        out += raw[PRESET]; out += raw[PRESET + 1]
        out += 0; out += 0
        out.addAll(bands.toList()); out.addAll(bands.toList())
        if (trying) { out += 1; out += 0xFE.toByte() } else { out += 0; out += 1 }
        out += 1
        out.addAll(curveBytes(l).toList()); out.addAll(curveBytes(r).toList())
        out += (madeAt shr 24).toByte(); out += (madeAt shr 16).toByte(); out += (madeAt shr 8).toByte(); out += madeAt.toByte()
        out += 0
        out.addAll(curveBytes(l).toList()); out.addAll(curveBytes(r).toList())
        out.addAll(limiter(l).toList()); out.addAll(limiter(r).toList())
        out += if (trying) 0 else 0x15
        return out.toByteArray()
    }

    // ---- Messages that change things ----

    /**
     * The sound-mode messages to go from [now] to [target]: one field per message, a field's parent first, as the
     * earbuds ignore a change to a setting whose mode isn't on.
     */
    fun soundSteps(now: ByteArray, target: ByteArray): List<ByteArray> {
        val steps = mutableListOf<ByteArray>()
        var current = now.copyOf()
        fun step(index: Int, value: Int) {
            if (current[index].u() == value) return
            current = current.copyOf().also { it[index] = value.toByte() }
            steps += current
        }
        // Mode, then the cancelling kind, then what hangs off each.
        step(0, target[0].u())
        val ambient = target[0].u()
        if (ambient == 0) {
            step(3, target[3].u())
            when (target[3].u()) {
                0 -> step(1, (target[1].u() and 0xF0) or (current[1].u() and 0x0F))
                1 -> step(5, target[5].u())
                2 -> step(6, target[6].u())
            }
        }
        if (ambient == 1) step(2, target[2].u())
        if (ambient == 0 || ambient == 1) step(4, (current[4].u() and 0x02) or (target[4].u() and 0x01))
        return steps
    }

    fun soundTarget(now: ByteArray, sound: Sound): ByteArray = now.copyOf().also {
        it[0] = when (sound.mode) {
            ListeningMode.NoiseCancelling -> 0
            ListeningMode.Transparency -> 1
            ListeningMode.Normal -> 2
        }
        it[1] = ((sound.manualLevel.coerceIn(1, 5) shl 4) or (now[1].u() and 0x0F)).toByte()
        it[2] = if (sound.transparencyStyle == TransparencyStyle.Voice) 1 else 0
        it[3] = when (sound.cancelStyle) {
            CancelStyle.Manual -> 0
            CancelStyle.Adaptive -> 1
            CancelStyle.Travel -> 2
        }
        it[4] = ((now[4].u() and 0x02) or if (sound.windReduction) 1 else 0).toByte()
        it[5] = if (sound.environmentDetection) 1 else 0
        it[6] = sound.travel.ordinal.toByte()
    }

    /** The equalizer message: the bands for both channels, the hearing profile echoed back switched off, and the curve the earbuds' limiter needs. */
    fun equalizerBody(raw: ByteArray, presetId: Int, tenths: IntArray): ByteArray {
        require(tenths.size == 10)
        val bands = ByteArray(10) { (tenths[it].coerceIn(-120, 134) + 120).toByte() }
        val drc = drc(tenths).let { values -> ByteArray(10) { (values[it] + 120).toByte() }.also { it[9] = 0 } }
        val id = if (presetId == Equalizer.CUSTOM) CUSTOM else presetId
        val out = ArrayList<Byte>(93)
        out += id.toByte(); out += (id shr 8).toByte()
        out += raw[HEAR_ID_GENRE]; out += 0
        out.addAll(bands.toList()); out.addAll(bands.toList())
        out += 0; out += 0
        out += 0 // the hearing profile off, so these bands are what plays
        for (i in HEAR_ID + 1 until HEAR_ID + 1 + 20) out += raw[i]
        for (i in 81 until 85) out += raw[i]
        out += raw[85]
        for (i in 86 until 106) out += raw[i]
        out.addAll(drc.toList()); out.addAll(drc.toList())
        out += 0
        return out.toByteArray()
    }

    /** Ten bands in tenths: the eight shown, then the two hidden ones kept as the earbuds last said. */
    fun customTenths(raw: ByteArray, gains: List<Double>): IntArray {
        val channel = if (raw[EQ_LEFT].u() == 0xFF) EQ_RIGHT else EQ_LEFT
        return IntArray(10) { i ->
            if (i < 8) roundHalfAway(gains.getOrElse(i) { 0.0 } * 10).toInt() else raw[channel + i].u() - 120
        }
    }

    /** Soundcore's limiter curve for a set of bands, exactly as the official app works it out. */
    fun drc(tenths: IntArray): IntArray {
        val a = DoubleArray(8) { tenths[it] / 10.0 }
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
        val out = IntArray(10)
        for (b in 0 until 8) {
            var acc = 0.0
            for (i in 0 until 8) {
                acc += when {
                    (b == 1 || b == 3) && i == 2 -> -lows
                    (b == 4 || b == 6) && i == 5 -> -highs
                    else -> a[i] * c[b][i]
                }
            }
            out[b] = roundHalfAway((acc / 10.0) * 10.0).toInt().coerceIn(-120, 134)
        }
        out[8] = 0
        out[9] = -120
        return out
    }

    /** Rounds .5 away from zero, as the official app does; Kotlin's own rounding goes to even. */
    private fun roundHalfAway(x: Double): Double = sign(x) * floor(abs(x) + 0.5)

    /** The message to set one touch, and the one that turns that touch on, if needed. */
    fun touchBodies(raw: ByteArray, ear: Ear, press: Press, action: TouchAction): List<Packet> {
        val index = BUTTON_ORDER.indexOfFirst { it.first == ear && it.second == press }
        if (index < 0) return emptyList()
        val (_, _, id) = BUTTON_ORDER[index]
        val side = if (ear == Ear.Left) 0 else 1
        val both = raw[BOTH].toInt() == 1
        val code = ACTIONS.firstOrNull { it.second == action }?.first ?: 0xF
        val oldAction = raw[BUTTONS + index * 2 + 1].u()
        val newAction = if (both) (oldAction and 0xF0) or code else (oldAction and 0x0F) or (code shl 4)
        val oldEnabled = raw[BUTTONS + index * 2].u()
        val newEnabled = if (both) oldEnabled or 0x01 else oldEnabled or 0x10
        return buildList {
            if (newAction != oldAction) add(Packet(BUTTON_SET, byteArrayOf(side.toByte(), id.toByte(), newAction.toByte())))
            if (newEnabled != oldEnabled) add(Packet(BUTTON_ENABLE, byteArrayOf(side.toByte(), id.toByte(), newEnabled.toByte())))
        }
    }

    fun buttonOffset(ear: Ear, press: Press): Int =
        BUTTONS + 2 * BUTTON_ORDER.indexOfFirst { it.first == ear && it.second == press }

    fun cycleByte(old: Int, modes: Set<ListeningMode>): Int {
        var bits = 0
        if (ListeningMode.NoiseCancelling in modes) bits = bits or 1
        if (ListeningMode.Transparency in modes) bits = bits or 2
        if (ListeningMode.Normal in modes) bits = bits or 4
        // The high half is kept as read: it is most likely the cycle used with one earbud.
        return (old and 0xF0) or bits
    }

    /** The list of devices the earbuds remember, from one or more 0B 01 messages. */
    fun hosts(body: ByteArray): List<Host> {
        val hosts = mutableListOf<Host>()
        var at = 2
        while (at < body.size) {
            val length = body[at].u()
            if (length < 8 || at + length > body.size) break
            val connected = body[at + 1].toInt() == 1
            val mac = (0 until 6).map { body[at + 2 + it] }.reversed().joinToString(":") { "%02X".format(it) }
            val name = body.copyOfRange(at + 8, at + length).takeWhile { it.toInt() != 0 }.toByteArray().decodeToString()
            hosts += Host(name.ifBlank { mac }, mac, connected)
            at += length
        }
        return hosts
    }

    fun macBytes(address: String): ByteArray =
        address.split(":").map { it.toInt(16).toByte() }.reversed().toByteArray()

    private fun Byte.u(): Int = toInt() and 0xFF
}
