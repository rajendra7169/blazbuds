package com.blazify.buds.core.samsung

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.model.TransparencyStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Samsung Galaxy Buds, from the 2019 Buds to the Buds4 Pro: battery for each earbud and the case (and which are
 * charging, where the model says), the noise control each model has with its strengths where there are any, voice
 * detect on the Pro models, the five sound presets and, from the Buds3 on, a nine-band curve of the person's own, what
 * touch and hold does on each ear, the touch lock, the firmware, and making the earbuds ring to be found.
 *
 * The earbuds tell everything on their own: a full status on connecting and after most changes, a short one when a
 * battery moves, and a note when a setting is changed on the earbuds. A change made here shows at once and is put
 * right by the earbuds' acknowledgement or next status.
 *
 * Adapted from GalaxyBudsClient by Tim Schneeberger (github.com/timschneeb/GalaxyBudsClient, GPL-3.0), with facts
 * checked against Gadgetbridge (codeberg.org/Freeyourgadget/Gadgetbridge). Not checked on real earbuds yet.
 */
class SamsungBuds(
    name: String,
    private val model: SamsungModel,
    private val scope: CoroutineScope,
    private val open: suspend () -> Connection,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, model.label, Link.Connecting, canRing = true))
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val sending = Mutex()

    // What the earbuds have said; null until they say it.
    private var status: SamsungStatus? = null
    private var revision = 0
    private var greeted = false
    private var ringing = false

    /** Whether noise cancelling, when picked, is the adaptive kind; the last one chosen. */
    private var adaptiveChosen = false

    /** The person's own curve in the earbuds' steps, as last sent or read back; null until known. */
    private var curve: List<Int>? = null

    /**
     * Whether the person's own curve is the sound they chose here. The earbuds forget the curve when they are switched
     * off (and the Buds4 Pro when the noise control changes), so then it is sent again, as GalaxyBudsClient does.
     */
    private var curveChosen = false

    fun start() {
        if (runner != null) return
        runner = scope.launch {
            var wait = 2_000L
            while (isActive) {
                flow.update { if (it.link == Link.Busy || it.link == Link.Lost) it else it.copy(link = Link.Connecting) }
                val busy = try {
                    session()
                    wait = 2_000L
                    false
                } catch (e: LinkException) {
                    log("link: ${e.message}")
                    e.busy
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log("link: $e")
                    false
                }
                connection?.close()
                connection = null
                ringing = false
                flow.update { it.copy(link = if (busy) Link.Busy else Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    private suspend fun session() {
        val link = withContext(Dispatchers.IO) { open() }
        connection = link
        greeted = false
        flow.update { it.copy(link = Link.Connected) }
        val reader = SamsungMessage.Reader(model.legacy)
        try {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                while (true) {
                    val count = link.read(buffer)
                    if (count < 0) break
                    for (message in reader.feed(buffer, count)) handle(message)
                }
            }
        } finally {
            link.close()
        }
    }

    private suspend fun send(id: Int, vararg payload: Int) {
        val link = connection ?: return
        val message = SamsungMessage(id, ByteArray(payload.size) { payload[it].toByte() })
        log("-> $message")
        sending.withLock {
            runCatching { withContext(Dispatchers.IO) { link.write(SamsungMessage.encode(message, model.legacy)) } }
                .onFailure { log("send: ${it.message}") }
        }
    }

    private fun handle(message: SamsungMessage) {
        log("<- $message")
        val p = message.payload
        val first = p.firstOrNull()?.toInt()?.and(0xFF)
        when (message.id) {
            SamsungStatus.EXTENDED_STATUS -> {
                val read = SamsungStatus.extended(model, p) ?: return
                revision = read.revision
                status = read
                val fresh = !greeted
                // A preset picked elsewhere, on the phone or by another app, ends the person's own curve; the first
                // status after connecting doesn't count, as the earbuds may have just forgotten it.
                if (!fresh && read.preset != null && read.preset != CUSTOM_PRESET) curveChosen = false
                publish()
                if (fresh) {
                    greeted = true
                    // As Samsung's own app does: say who we are, then ask for the firmware.
                    scope.launch {
                        send(MANAGER_INFO, 1, 1, 34)
                        send(DEBUG_GET_ALL_DATA)
                        if (model.customEq) {
                            if (curveChosen) sendCurve() else send(CUSTOM_EQUALIZE_RECV)
                        }
                    }
                }
            }
            CUSTOM_EQUALIZE_RECV -> if (model.customEq && !curveChosen) customBands(p)?.let {
                curve = it
                publish()
            }
            SamsungStatus.STATUS -> {
                val read = SamsungStatus.basic(model, p, revision) ?: return
                status = (status ?: read).copy(left = read.left, right = read.right, case = read.case ?: status?.case, coupled = read.coupled, charging = read.charging ?: status?.charging)
                publish()
            }
            NOISE_CONTROLS_UPDATE -> if (model.noise == SamsungModel.Noise.Controls && first != null) {
                setNoise(first)
                curveAgain()
            }
            AMBIENT_MODE_UPDATED -> if (model.noise == SamsungModel.Noise.Ambient && first != null) setNoise(if (first == 1) 2 else 0)
            NOISE_REDUCTION_MODE_UPDATE -> if (model.noise == SamsungModel.Noise.Cancelling && first != null) setNoise(if (first == 1) 1 else 0)
            AMBIENT_VOICE_FOCUS -> if (model == SamsungModel.Buds && first != null) {
                status = status?.copy(voiceFocus = first == 1)
                publish()
            }
            TOUCH_UPDATED -> if (model.lock(revision) == SamsungModel.Lock.Simple && first != null) {
                status = status?.copy(touchLocked = first == 1)
                publish()
            }
            ACKNOWLEDGEMENT -> acknowledged(p)
            DEBUG_GET_ALL_DATA -> firmware(model, p)?.let { fw -> flow.update { it.copy(firmware = fw) } }
            FIND_MY_EARBUDS_STOP -> ringing = false
        }
    }

    /** The earbuds' answer to a change: the setting's id and the value it now has. */
    private fun acknowledged(p: ByteArray) {
        if (p.size < 2) return
        val value = p[1].toInt() and 0xFF
        when (p[0].toInt() and 0xFF) {
            NOISE_CONTROLS -> if (model.noise == SamsungModel.Noise.Controls) setNoise(value)
            SET_NOISE_REDUCTION -> if (model.noise == SamsungModel.Noise.Cancelling) setNoise(if (value == 1) 1 else 0)
            SET_DETECT_CONVERSATIONS -> if (model.voiceDetect) {
                status = status?.copy(voiceDetect = value == 1)
                publish()
            }
            NOISE_REDUCTION_LEVEL -> if (model.cancelLevels > 0 && value < model.cancelLevels) {
                status = status?.copy(cancelLevel = value)
                publish()
            }
            EQUALIZER -> if (model != SamsungModel.Buds && (value <= 5 || (model.customEq && value == CUSTOM_PRESET))) {
                if (value != CUSTOM_PRESET) curveChosen = false
                status = status?.copy(preset = value)
                publish()
            }
            SET_TOUCHPAD_OPTION -> if (p.size >= 3) {
                status = status?.copy(holdLeft = value, holdRight = p[2].toInt() and 0xFF)
                publish()
            }
        }
    }

    private fun setNoise(mode: Int) {
        status = status?.copy(noise = mode)
        publish()
    }

    /** Sends the person's own curve, then picks it, as GalaxyBudsClient does. */
    private suspend fun sendCurve() {
        val steps = curve ?: return
        status = status?.copy(preset = CUSTOM_PRESET)
        publish()
        send(CUSTOM_EQUALIZE_SEND, steps.size, *steps.toIntArray())
        send(EQUALIZER, CUSTOM_PRESET)
    }

    /** The Buds4 Pro drop the curve when the noise control changes, so it goes again; GalaxyBudsClient saw it on no other. */
    private fun curveAgain() {
        if (model == SamsungModel.Buds4Pro && curveChosen) scope.launch { sendCurve() }
    }

    /** Puts what the earbuds have said into the state, as the screens know it. */
    private fun publish() {
        val s = status ?: return
        fun battery(percent: Int?, charging: Boolean?) = percent?.let { Battery(it, charging = charging == true) }
        val sound = s.noise?.let { noise ->
            val modes = when (model.noise) {
                SamsungModel.Noise.Ambient -> listOf(ListeningMode.Transparency, ListeningMode.Normal)
                SamsungModel.Noise.Cancelling -> listOf(ListeningMode.NoiseCancelling, ListeningMode.Normal)
                SamsungModel.Noise.Controls -> ListeningMode.entries
            }
            val levels = model.cancelLevels
            Sound(
                mode = when (noise) {
                    1, 3 -> ListeningMode.NoiseCancelling
                    2 -> ListeningMode.Transparency
                    else -> ListeningMode.Normal
                },
                cancelStyle = if (noise == 3) CancelStyle.Adaptive else CancelStyle.Manual,
                // The screens count strengths from 1; the earbuds from 0.
                manualLevel = ((s.cancelLevel ?: (levels - 1)) + 1).coerceIn(1, levels.coerceAtLeast(1)),
                transparencyStyle = if (s.voiceFocus == true) TransparencyStyle.Voice else TransparencyStyle.Full,
                options = SoundOptions(
                    cancelStyles = when {
                        model.adaptive -> listOf(CancelStyle.Adaptive, CancelStyle.Manual)
                        // Manual alone, so its strengths show.
                        levels > 0 -> listOf(CancelStyle.Manual)
                        else -> emptyList()
                    },
                    transparencyStyles = model == SamsungModel.Buds && s.voiceFocus != null,
                    wind = false,
                    environment = false,
                    cycle = false,
                    modes = modes,
                    levels = levels > 0 && s.cancelLevel != null,
                    manualLevels = if (levels > 0) levels else 5,
                ),
            )
        }
        val equalizer = s.preset?.let { preset ->
            val presets = PRESETS.map { (id, label) -> EqPreset(id, label, emptyList()) }
            if (model.customEq) {
                // The earbuds' steps shown as decibels, within what the screens draw.
                val gains = (curve ?: List(BANDS.size) { 0 }).map { it.toDouble().coerceIn(Equalizer.MIN_DB, Equalizer.MAX_DB) }
                Equalizer(if (preset == CUSTOM_PRESET) Equalizer.CUSTOM else preset, presets, BANDS, gains, custom = true)
            } else {
                Equalizer(preset, presets, emptyList(), emptyList(), custom = false)
            }
        }
        val codes = model.holdCodes
        fun action(ear: Ear, code: Int?): TouchAction? {
            code ?: return null
            return when (codes.entries.firstOrNull { it.value == code }?.key) {
                SamsungModel.Hold.Voice -> TouchAction.VoiceAssistant
                SamsungModel.Hold.Noise -> TouchAction.ListeningMode
                // One choice for volume: the left earbud turns it down, the right one up.
                SamsungModel.Hold.Volume -> if (ear == Ear.Left) TouchAction.VolumeDown else TouchAction.VolumeUp
                // Spotify, or an action of another app's: nothing these screens can name.
                null -> TouchAction.None
            }
        }
        val left = action(Ear.Left, s.holdLeft)
        val right = action(Ear.Right, s.holdRight)
        val controls = if (left == null || right == null) null else {
            val choices = buildList {
                add(TouchAction.ListeningMode)
                add(TouchAction.VoiceAssistant)
                if (SamsungModel.Hold.Volume in codes) {
                    add(TouchAction.VolumeDown)
                    add(TouchAction.VolumeUp)
                }
            }
            TouchControls(
                actions = mapOf((Ear.Left to Press.Long) to left, (Ear.Right to Press.Long) to right),
                available = choices,
                availableFor = mapOf(Press.Long to choices),
            )
        }
        val toggles = buildMap {
            if (model.lock(revision) != SamsungModel.Lock.None) s.touchLocked?.let { put(Toggle.TouchLock, it) }
            // Samsung's voice detect: the world comes in and the music goes down while you talk.
            s.voiceDetect?.let { put(Toggle.SpeakToChat, it) }
        }
        flow.update {
            it.copy(
                left = battery(s.left, s.charging?.first),
                right = battery(s.right, s.charging?.second),
                case = battery(s.case, s.charging?.third),
                bothConnected = s.coupled,
                sound = sound,
                equalizer = equalizer,
                controls = controls,
                toggles = toggles,
            )
        }
    }

    override suspend fun refresh() {
        if (connection == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
        }
        // Connected earbuds tell every change themselves; there is nothing to ask.
    }

    override suspend fun apply(change: Change) {
        connection ?: return
        val s = status ?: return
        when (change) {
            is Change.Mode -> {
                val mode = when (change.mode) {
                    ListeningMode.NoiseCancelling -> if (model.adaptive && adaptiveChosen) 3 else 1
                    ListeningMode.Transparency -> 2
                    ListeningMode.Normal -> 0
                }
                setNoise(mode)
                when (model.noise) {
                    SamsungModel.Noise.Controls -> send(NOISE_CONTROLS, mode)
                    SamsungModel.Noise.Ambient -> send(SET_AMBIENT_MODE, if (mode == 2) 1 else 0)
                    SamsungModel.Noise.Cancelling -> send(SET_NOISE_REDUCTION, if (mode == 1) 1 else 0)
                }
                curveAgain()
            }
            is Change.Cancelling -> {
                if (model.adaptive) {
                    adaptiveChosen = change.style == CancelStyle.Adaptive
                    val mode = if (adaptiveChosen) 3 else 1
                    // A strength picked while Manual is on needs no switch first.
                    if (s.noise != mode || change.level == null) {
                        setNoise(mode)
                        send(NOISE_CONTROLS, mode)
                        curveAgain()
                    }
                }
                val level = change.level
                if (change.style == CancelStyle.Manual && level != null && model.cancelLevels > 0) {
                    val raw = (level - 1).coerceIn(0, model.cancelLevels - 1)
                    status = (status ?: s).copy(cancelLevel = raw)
                    publish()
                    send(NOISE_REDUCTION_LEVEL, raw)
                }
            }
            is Change.TransparencyKind -> if (model == SamsungModel.Buds) {
                val on = change.style == TransparencyStyle.Voice
                status = s.copy(voiceFocus = on)
                publish()
                send(AMBIENT_VOICE_FOCUS, if (on) 1 else 0)
            }
            is Change.Preset -> if (change.id in 0..5) {
                curveChosen = false
                status = s.copy(preset = change.id)
                publish()
                // The 2019 Buds take "on" and the preset counted from 0, plus 5 for the version without Dolby Atmos;
                // "off" still carries a preset, Dynamic, as GalaxyBudsClient sends.
                if (model == SamsungModel.Buds) send(EQUALIZER, if (change.id == 0) 0 else 1, if (change.id == 0) 7 else change.id - 1 + 5)
                else send(EQUALIZER, change.id)
            }
            is Change.Bands -> if (model.customEq && change.left.isNotEmpty()) {
                curve = steps(change.left)
                curveChosen = true
                sendCurve()
            }
            is Change.Touch -> if (change.press == Press.Long) {
                val hold = when (change.action) {
                    TouchAction.VoiceAssistant -> SamsungModel.Hold.Voice
                    TouchAction.ListeningMode -> SamsungModel.Hold.Noise
                    TouchAction.VolumeUp, TouchAction.VolumeDown -> SamsungModel.Hold.Volume
                    else -> return
                }
                val code = model.holdCodes[hold] ?: return
                val left = if (change.ear == Ear.Left) code else s.holdLeft ?: return
                val right = if (change.ear == Ear.Right) code else s.holdRight ?: return
                status = s.copy(holdLeft = left, holdRight = right)
                publish()
                send(SET_TOUCHPAD_OPTION, left, right)
            }
            is Change.Switch -> when (change.toggle) {
                Toggle.TouchLock -> lockTouch(s, change.on)
                Toggle.SpeakToChat -> if (model.voiceDetect && s.voiceDetect != null) {
                    status = s.copy(voiceDetect = change.on)
                    publish()
                    send(SET_DETECT_CONVERSATIONS, if (change.on) 1 else 0)
                }
                else -> {}
            }
            else -> {}
        }
    }

    private suspend fun lockTouch(s: SamsungStatus, on: Boolean) {
        when (model.lock(revision)) {
            SamsungModel.Lock.Simple -> {
                status = s.copy(touchLocked = on)
                publish()
                send(LOCK_TOUCHPAD, if (on) 1 else 0)
            }
            SamsungModel.Lock.Gestures -> {
                // The whole switch first, then each gesture as it is now: tap, double, triple, hold, and for calls.
                val g = s.gestures ?: return
                fun bit(n: Int) = if (g and (1 shl n) != 0) 1 else 0
                val payload = mutableListOf(if (on) 0 else 1, bit(3), bit(2), bit(1), bit(0))
                if (model.lockForCalls(revision)) payload += listOf(bit(4), bit(5))
                status = s.copy(touchLocked = on, gestures = if (on) g and 0x7F else g or 0x80)
                publish()
                send(LOCK_TOUCHPAD, *payload.toIntArray())
            }
            SamsungModel.Lock.None -> {}
        }
    }

    /** Rings the chosen earbuds; the other one is muted while they ring. */
    override suspend fun ring(left: Boolean, right: Boolean) {
        connection ?: return
        if (!left && !right) {
            if (ringing) send(FIND_MY_EARBUDS_STOP)
            ringing = false
            return
        }
        if (!ringing) send(FIND_MY_EARBUDS_START)
        ringing = true
        send(MUTE_EARBUD, if (left) 0 else 1, if (right) 0 else 1)
    }

    override fun watch(on: Boolean) {
        if (on && connection == null) wake.trySend(Unit)
    }

    override fun close() {
        val job = runner
        runner = null
        connection?.close()
        scope.launch { job?.cancelAndJoin() }
    }

    companion object {
        const val DEBUG_GET_ALL_DATA = 0x26
        const val ACKNOWLEDGEMENT = 0x42
        const val NOISE_CONTROLS_UPDATE = 0x77
        const val NOISE_CONTROLS = 0x78
        const val SET_DETECT_CONVERSATIONS = 0x7A
        const val SET_AMBIENT_MODE = 0x80
        const val AMBIENT_MODE_UPDATED = 0x81

        /** The strength of manual noise cancelling, from 0; on older models the same number is something else. */
        const val NOISE_REDUCTION_LEVEL = 0x83

        /** The 2019 Buds' voice focus; the same number means something else on later models. */
        const val AMBIENT_VOICE_FOCUS = 0x85
        const val EQUALIZER = 0x86
        const val MANAGER_INFO = 0x88
        const val LOCK_TOUCHPAD = 0x90
        const val TOUCH_UPDATED = 0x91
        const val SET_TOUCHPAD_OPTION = 0x92
        const val SET_NOISE_REDUCTION = 0x98
        const val NOISE_REDUCTION_MODE_UPDATE = 0x9B
        const val FIND_MY_EARBUDS_START = 0xA0
        const val FIND_MY_EARBUDS_STOP = 0xA1
        const val MUTE_EARBUD = 0xA2

        /** Samsung's presets, numbered as the earbuds count them from the Buds+ on. */
        val PRESETS = listOf(0 to "Normal", 1 to "Bass boost", 2 to "Soft", 3 to "Dynamic", 4 to "Clear", 5 to "Treble boost")

        // The person's own curve, Buds3 and later. Adapted from GalaxyBudsClient's SetCustomEqualizerEncoder and
        // CustomEqualizerDataDecoder (Tim Schneeberger, GPL-3.0).

        /** Asks for the curve the earbuds hold; the answer comes back under the same number. */
        const val CUSTOM_EQUALIZE_RECV = 0x69

        /** Sets the curve: the number of bands, then each band's step. On the 2019 Buds this number is something else. */
        const val CUSTOM_EQUALIZE_SEND = 0x89

        /** The equalizer setting that plays the person's own curve, after the five presets and "off". */
        const val CUSTOM_PRESET = 7

        /** The centre of each band, in hertz. */
        val BANDS = listOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /**
         * The curve in the earbuds' answer: how many presets, how many bands, a table of every preset but the flat
         * first one, and then the person's own bands, each a signed byte. Null when it isn't the nine bands known here.
         */
        fun customBands(p: ByteArray): List<Int>? {
            if (p.size < 2) return null
            val presets = p[0].toInt() and 0xFF
            val bands = p[1].toInt() and 0xFF
            if (presets < 1 || bands != BANDS.size) return null
            val from = 2 + (presets - 1) * bands
            if (from + bands > p.size) return null
            return List(bands) { p[from + it].toInt().coerceIn(-10, 10) }
        }

        /**
         * A curve as the earbuds' nine steps, each taken as a decibel, from −10 to +10. Nine bands go as they are; the
         * app's usual eight (100 Hz to 12.8 kHz, an octave apart) are read at each of Samsung's frequencies along the
         * octaves; any other count is spread over the nine.
         */
        fun steps(gains: List<Double>): List<Int> {
            val n = BANDS.size
            fun at(i: Int): Double = when (gains.size) {
                n -> gains[i]
                8 -> {
                    val x = log2(BANDS[i] / 100.0).coerceIn(0.0, 7.0)
                    val low = x.toInt().coerceAtMost(6)
                    val t = x - low
                    gains[low] * (1 - t) + gains[low + 1] * t
                }
                else -> gains[(i * (gains.size - 1) + (n - 1) / 2) / (n - 1)]
            }
            return List(n) { at(it).roundToInt().coerceIn(-10, 10) }
        }

        private val MONTHS = "ABCDEFGHIJKL"
        private val YEARS = "OPQRSTUVWXYZ"
        private val RELEASES = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        /**
         * The firmware name from the earbuds' debug data, as Samsung writes it (R175XXU0AUA1): the model number, "XX",
         * E or U, "0A", then a letter each for the year, month and release. The Buds3 and later send the name whole.
         */
        fun firmware(model: SamsungModel, p: ByteArray): String? {
            fun at(i: Int): Int? = p.getOrNull(i)?.toInt()?.and(0xFF)
            if (model == SamsungModel.Buds3 || model == SamsungModel.Buds3Pro || model == SamsungModel.Buds3Fe ||
                model == SamsungModel.Buds4 || model == SamsungModel.Buds4Pro
            ) {
                if (p.size < 3) return null
                return p.copyOfRange(2, minOf(p.size, 22)).filter { it != 0.toByte() }.toByteArray().decodeToString().trim().ifEmpty { null }
            }
            val start = if (model == SamsungModel.Buds) 1 else 2
            val variant = at(start) ?: return null
            val date = at(start + 1) ?: return null
            val release = at(start + 2) ?: return null
            val year = YEARS.getOrNull(date shr 4) ?: return null
            val month = MONTHS.getOrNull(date and 0x0F) ?: return null
            val kind = if (model == SamsungModel.Buds) "EU".getOrNull(variant) ?: return null else if (variant and 1 == 0) 'E' else 'U'
            val rel = if (model == SamsungModel.Buds && release <= 15) release.toString(16).uppercase() else RELEASES.getOrNull(if (model == SamsungModel.Buds) release - 16 else release)?.toString() ?: return null
            return "${model.build}XX${kind}0A$year$month$rel"
        }
    }
}
