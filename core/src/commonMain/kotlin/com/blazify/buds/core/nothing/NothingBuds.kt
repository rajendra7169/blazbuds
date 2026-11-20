package com.blazify.buds.core.nothing

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
import com.blazify.buds.core.nothing.NothingCommands as C
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Nothing and CMF earbuds and headphones: Ear (1), Ear (2), Ear (stick), Ear (a), Ear, Ear (3), Ear (3a), Ear (open),
 * Headphone (1), CMF Buds, Buds Pro, Buds Pro 2, Buds 2, Buds 2 Plus, Buds 2a, Neckband Pro and Headphone Pro.
 *
 * They say which model they are when asked, and the app then asks for what that model has: battery for each earbud
 * and the case, noise control with its strengths, the equalizer presets and the person's own three-band sound,
 * double, triple and long press on each earbud, pause when taken out, low lag, bass boost and spatial audio, and the
 * firmware. Each shows only once the earbuds answer for it. A model the app doesn't know by number still gets
 * battery, noise control, presets and the switches it answers for. They can also beep to be found.
 *
 * Written from nada by chukfinley and nadamas by ezvk (itself a fork of SoaOaoS's something-x), all MIT licensed;
 * protocol facts from Gadgetbridge, ear (web) and PairFree.
 */
class NothingBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val open: suspend () -> Connection,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val sending = Mutex()
    private var number = 0
    private val waiting = mutableMapOf<Int, CompletableDeferred<NothingFrame>>()
    private var activated = false
    @kotlin.concurrent.Volatile private var watching = true

    // What the earbuds have said; null until (unless) they answer.
    private var model: NothingModel? = null
    private var noiseMode: Int? = null
    private var strength: Int? = null
    private var eqMode: Int? = null
    private var bands: List<Double>? = null
    private var inEar: Boolean? = null
    private var lowLag: Boolean? = null
    private var bass: Pair<Boolean, Int>? = null
    private var spatial: ByteArray? = null
    private val keys = mutableMapOf<Pair<Ear, Press>, Int>()

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
                waiting.values.forEach { it.cancel() }
                waiting.clear()
                flow.update { it.copy(link = if (busy) Link.Busy else Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    /** One connection: the model first, which also shows these speak this protocol, then everything it has. */
    private suspend fun session() {
        val link = withContext(Dispatchers.IO) { open() }
        connection = link
        activated = false
        val reading = scope.launch(Dispatchers.IO) {
            val reader = NothingFrame.Reader()
            val buffer = ByteArray(2048)
            while (true) {
                val count = link.read(buffer)
                if (count < 0) return@launch
                for (frame in reader.feed(buffer, count)) handle(frame)
            }
        }
        try {
            command(C.READ_VERSION, timeout = 1_000)
            val answer = command(C.READ_MODEL, timeout = 3_000) ?: command(C.READ_MODEL, timeout = 3_000)
                ?: throw LinkException("These earbuds didn't answer")
            val id = C.productId(answer.payload)
            model = id?.let { NothingModel.byId(it) }
            log("product $id: ${model?.name ?: "not one the app knows"}")
            flow.update {
                it.copy(
                    link = Link.Connected,
                    model = model?.name ?: (if (id != null) "Nothing $id" else "Nothing"),
                    canRing = model != null,
                )
            }
            readAll()
            val polling = scope.launch {
                while (true) {
                    delay(30_000)
                    if (watching) runCatching { command(C.READ_BATTERY) }
                }
            }
            try {
                reading.join()
            } finally {
                polling.cancel()
            }
        } finally {
            link.close()
            reading.cancel()
        }
    }

    private suspend fun readAll() {
        val known = model
        command(C.READ_FIRMWARE)
        command(C.READ_BATTERY)
        // The Ear (stick) has noise cancelling only from its x.x.2 firmware on.
        val stickWithout = known?.id == "B157" && flow.value.firmware?.split('.')?.getOrNull(2) != "2"
        if ((known == null || known.noise.isNotEmpty()) && !stickWithout) command(C.READ_NOISE, C.NOISE_GROUPS)
        command(if (known?.eq == NothingEq.Dirac) C.READ_DIRAC else C.READ_EQ)
        if (known?.curve != null) command(C.READ_CURVE)
        if (known == null || known.inEar) command(C.READ_EXTRA)
        command(C.READ_LOW_LAG)
        if (known == null || known.bass) command(C.READ_BASS)
        if (known == null || known.spatial) command(C.READ_SPATIAL)
        if (known?.taps?.isNotEmpty() == true) command(C.READ_KEYS)
    }

    /**
     * Sends one command and waits for its answer, one at a time as Nothing's app does. Earbuds that need waking first
     * answer "not activated"; they are woken once and asked again.
     */
    private suspend fun command(command: Int, payload: ByteArray = ByteArray(0), timeout: Long = 1_500): NothingFrame? {
        val answer = sending.withLock {
            val link = connection ?: return null
            val fsn = number
            number = (number + 1) % 254
            val reply = CompletableDeferred<NothingFrame>()
            waiting[command or 0x8000] = reply
            log("-> " + NothingFrame.hex4(command) + " " + NothingFrame.hex(payload))
            withContext(Dispatchers.IO) { link.write(NothingFrame.encode(command, payload, fsn)) }
            withTimeoutOrNull(timeout) { reply.await() }.also { waiting.remove(command or 0x8000) }
        } ?: return null
        if (answer.result == NothingFrame.NOT_ACTIVATED && !activated) {
            activated = true
            log("activating")
            command(C.ACTIVATE)
            return command(command, payload, timeout)
        }
        return answer
    }

    private fun handle(frame: NothingFrame) {
        log("<- $frame")
        // What it says is taken in before whoever asked carries on.
        read(frame)
        if (frame.isAnswer) waiting[frame.id]?.complete(frame)
    }

    private fun read(frame: NothingFrame) {
        if (!frame.ok || frame.payload.isEmpty()) return
        val p = frame.payload
        when (frame.id) {
            C.READ_BATTERY, C.BATTERY_NEWS -> {
                val levels = C.batteries(p)
                flow.update { it.copy(left = levels[C.LEFT], right = levels[C.RIGHT], case = levels[C.CASE], overall = levels[C.WHOLE]) }
                return
            }
            C.READ_FIRMWARE -> {
                val text = p.decodeToString().trim { it <= ' ' }
                if (text.isNotEmpty()) flow.update { it.copy(firmware = text) }
                return
            }
            C.READ_NOISE, C.NOISE_NEWS -> {
                val (mode, level) = C.noise(p)
                if (mode != null) noiseMode = mode
                if (level != null && level in 1..4) strength = level
                if (mode != null && mode in 1..4) strength = mode
            }
            C.READ_EQ, C.READ_DIRAC -> eqMode = p[0].toInt() and 0xFF
            C.READ_CURVE -> bands = C.curve(p) ?: bands ?: listOf(0.0, 0.0, 0.0)
            C.READ_EXTRA -> C.extras(p)[C.IN_EAR]?.let { inEar = it == 1 }
            C.READ_LOW_LAG -> lowLag = p[0].toInt() == 1
            C.READ_BASS -> bass = (p[0].toInt() == 1) to (p.getOrNull(1)?.toInt()?.and(0xFF) ?: 0)
            C.READ_SPATIAL -> spatial = p
            C.READ_KEYS -> C.keys(p).forEach { key ->
                val ear = when (key.part) { C.LEFT -> Ear.Left; C.RIGHT -> Ear.Right; else -> return@forEach }
                val press = PRESSES[key.gesture] ?: return@forEach
                if (key.button == C.TOUCH) keys[ear to press] = key.action
            }
            else -> return
        }
        publish()
    }

    /** Puts what the earbuds have answered into the state, as the screens know it. */
    private fun publish() {
        val known = model
        val sound = noiseMode?.let { mode -> sound(mode, known) }
        val equalizer = eqMode?.let { mode -> equalizer(mode, known) }
        val taps = known?.taps.orEmpty()
        val actions = keys.filterKeys { it.second in taps }
            .mapValues { (_, code) -> action(code) ?: if (code in NothingAction.NOISE_CYCLES) TouchAction.ListeningMode else TouchAction.None }
        val controls = if (actions.isEmpty()) null else TouchControls(
            actions = actions,
            available = taps.values.flatten().mapNotNull { action(it) }.distinct(),
            availableFor = taps.mapValues { (_, codes) -> codes.mapNotNull { action(it) }.distinct() },
        )
        val toggles = buildMap {
            inEar?.let { put(Toggle.WearDetection, it) }
            lowLag?.let { put(Toggle.GamingMode, it) }
            bass?.let { put(Toggle.BassBoost, it.first) }
            spatial?.let { put(Toggle.Surround, it[0].toInt() != 0) }
        }
        flow.update { it.copy(sound = sound, equalizer = equalizer, controls = controls, toggles = toggles) }
    }

    private fun sound(mode: Int, known: NothingModel?): Sound {
        val modes = known?.noise ?: NothingModel.ALL_NOISE
        val strengths = known?.strengths ?: listOf(NothingNoise.LOW, NothingNoise.MID, NothingNoise.HIGH)
        val adaptive = NothingNoise.ADAPTIVE in modes
        val styles = when {
            adaptive && strengths.isNotEmpty() -> listOf(CancelStyle.Adaptive, CancelStyle.Manual)
            !adaptive && strengths.size > 1 -> listOf(CancelStyle.Manual)
            else -> emptyList()
        }
        val cancelling = if (mode == NothingNoise.OFF || mode == NothingNoise.TRANSPARENCY) strength else mode
        return Sound(
            mode = when (mode) {
                NothingNoise.OFF -> ListeningMode.Normal
                NothingNoise.TRANSPARENCY -> ListeningMode.Transparency
                else -> ListeningMode.NoiseCancelling
            },
            cancelStyle = if (cancelling == NothingNoise.ADAPTIVE && adaptive || strengths.isEmpty()) CancelStyle.Adaptive else CancelStyle.Manual,
            manualLevel = STEPS[cancelling] ?: STEPS.getValue(strengths.lastOrNull() ?: NothingNoise.HIGH),
            options = SoundOptions(
                cancelStyles = styles,
                transparencyStyles = false,
                wind = false,
                environment = false,
                cycle = false,
                modes = buildList {
                    if (adaptive || strengths.isNotEmpty()) add(ListeningMode.NoiseCancelling)
                    if (NothingNoise.TRANSPARENCY in modes) add(ListeningMode.Transparency)
                    if (NothingNoise.OFF in modes) add(ListeningMode.Normal)
                },
            ),
        )
    }

    private fun equalizer(mode: Int, known: NothingModel?): Equalizer {
        val kind = known?.eq ?: NothingEq.Nothing
        val names = if (kind == NothingEq.Dirac) DIRAC_PRESETS else NOTHING_PRESETS
        val custom = known?.curve != null
        return Equalizer(
            presetId = if (custom && mode == C.customPreset(kind)) Equalizer.CUSTOM else mode,
            presets = names.map { (id, label) -> EqPreset(id, label, emptyList()) },
            frequencies = known?.curve?.let { listOf(it.lowHz.toInt(), it.midHz.toInt(), it.highHz.toInt()) } ?: emptyList(),
            left = if (custom) bands ?: listOf(0.0, 0.0, 0.0) else emptyList(),
            custom = custom,
        )
    }

    override suspend fun refresh() {
        if (connection == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        command(C.READ_BATTERY)
    }

    override suspend fun apply(change: Change) {
        connection ?: return
        val known = model
        when (change) {
            is Change.Mode -> {
                val modes = known?.noise ?: NothingModel.ALL_NOISE
                val wanted = when (change.mode) {
                    ListeningMode.Normal -> NothingNoise.OFF
                    ListeningMode.Transparency -> NothingNoise.TRANSPARENCY
                    ListeningMode.NoiseCancelling -> strength?.takeIf { it in modes } ?: known?.strengths?.lastOrNull() ?: NothingNoise.HIGH
                }
                if (wanted in modes) setNoise(wanted)
            }
            is Change.Cancelling -> {
                val modes = known?.noise ?: NothingModel.ALL_NOISE
                val wanted = when (change.style) {
                    CancelStyle.Adaptive -> NothingNoise.ADAPTIVE
                    else -> change.level?.let { nearest(it, known?.strengths ?: listOf(NothingNoise.LOW, NothingNoise.MID, NothingNoise.HIGH)) }
                        ?: strength?.takeIf { it != NothingNoise.ADAPTIVE }
                        ?: NothingNoise.HIGH
                }
                if (wanted in modes) setNoise(wanted)
            }
            is Change.Preset -> {
                val kind = known?.eq ?: NothingEq.Nothing
                eqMode = change.id
                publish()
                setEq(kind, change.id)
                command(if (kind == NothingEq.Dirac) C.READ_DIRAC else C.READ_EQ)
            }
            is Change.Bands -> {
                val shape = known?.curve ?: return
                val gains = change.left.map { kotlin.math.round(it.coerceIn(Equalizer.MIN_DB, Equalizer.MAX_DB)) }
                if (gains.size < 3) return
                val custom = C.customPreset(known.eq)
                bands = gains.take(3)
                val switching = eqMode != custom
                eqMode = custom
                publish()
                if (switching) setEq(known.eq, custom)
                command(C.SET_CURVE, C.curve(gains[0], gains[1], gains[2], shape))
                delay(300)
                command(C.READ_CURVE)
                command(if (known.eq == NothingEq.Dirac) C.READ_DIRAC else C.READ_EQ)
            }
            is Change.Touch -> {
                val taps = known?.taps?.get(change.press) ?: return
                val code = when (change.action) {
                    TouchAction.ListeningMode -> NothingAction.NOISE
                    else -> ACTIONS.entries.firstOrNull { it.value == change.action }?.key
                }?.takeIf { it in taps } ?: return
                val gesture = PRESSES.entries.first { it.value == change.press }.key
                keys[change.ear to change.press] = code
                publish()
                command(C.SET_KEYS, C.setKey(C.Key(if (change.ear == Ear.Left) C.LEFT else C.RIGHT, C.TOUCH, gesture, code)))
                command(C.READ_KEYS)
            }
            is Change.Switch -> when (change.toggle) {
                Toggle.WearDetection -> if (inEar != null) {
                    inEar = change.on
                    publish()
                    command(C.SET_EXTRA, byteArrayOf(1, C.IN_EAR.toByte(), if (change.on) 1 else 0))
                    command(C.READ_EXTRA)
                }
                Toggle.GamingMode -> if (lowLag != null) {
                    lowLag = change.on
                    publish()
                    command(C.SET_LOW_LAG, byteArrayOf(if (change.on) 1 else 2))
                    command(C.READ_LOW_LAG)
                }
                Toggle.BassBoost -> bass?.let { (_, level) ->
                    bass = change.on to level
                    publish()
                    // The strength stays as the earbuds had it; it is kept while the boost is off.
                    command(C.SET_BASS, byteArrayOf(if (change.on) 1 else 0, (level.takeIf { it > 0 } ?: BASS_LEVEL).toByte()))
                    command(C.READ_BASS)
                }
                Toggle.Surround -> spatial?.let { was ->
                    val on: Byte = if (change.on) 1 else 0
                    // Headphones also send whether the sound follows the head; that is kept as it was.
                    val payload = if (was.size >= 2) byteArrayOf(on, if (change.on) was[1] else 0) else byteArrayOf(on)
                    spatial = payload
                    publish()
                    command(C.SET_SPATIAL, payload)
                    command(C.READ_SPATIAL)
                }
                else -> {}
            }
            else -> {}
        }
    }

    private suspend fun setNoise(mode: Int) {
        noiseMode = mode
        if (mode in 1..4) strength = mode
        publish()
        command(C.SET_NOISE, byteArrayOf(1, mode.toByte(), 0))
        delay(300)
        command(C.READ_NOISE, C.NOISE_GROUPS)
    }

    private suspend fun setEq(kind: NothingEq, id: Int) {
        command(if (kind == NothingEq.Dirac) C.SET_DIRAC else C.SET_EQ, byteArrayOf(id.toByte(), 0))
    }

    /**
     * Rings the chosen earbuds, or stops them. Headphones, the neckband and the Ear (1) ring as one piece and take
     * only on or off.
     */
    override suspend fun ring(left: Boolean, right: Boolean) {
        val known = model ?: return
        if (known.form != NothingForm.Earbuds || known.id == "B181") {
            command(C.RING, byteArrayOf(if (left || right) 1 else 0))
            return
        }
        command(C.RING, byteArrayOf(C.LEFT.toByte(), if (left) 1 else 0))
        command(C.RING, byteArrayOf(C.RIGHT.toByte(), if (right) 1 else 0))
    }

    override fun watch(on: Boolean) {
        watching = on
        if (on) wake.trySend(Unit)
    }

    override fun close() {
        val job = runner
        runner = null
        connection?.close()
        scope.launch { job?.cancelAndJoin() }
    }

    companion object {
        /** The control service these earbuds and headphones talk on. */
        const val SERVICE = "aeac4a03-dff5-498f-843a-34487cf133eb"

        /** What the bass boost goes to when the earbuds have never said a strength. */
        private const val BASS_LEVEL = 6

        private val PRESSES = mapOf(C.DOUBLE to Press.Double, C.TRIPLE to Press.Triple, C.HOLD to Press.Long)

        private val ACTIONS = mapOf(
            NothingAction.PLAY_PAUSE to TouchAction.PlayPause,
            NothingAction.PREVIOUS to TouchAction.PreviousSong,
            NothingAction.NEXT to TouchAction.NextSong,
            NothingAction.VOICE to TouchAction.VoiceAssistant,
            NothingAction.VOLUME_UP to TouchAction.VolumeUp,
            NothingAction.VOLUME_DOWN to TouchAction.VolumeDown,
            NothingAction.NOISE to TouchAction.ListeningMode,
            NothingAction.NONE to TouchAction.None,
        )

        private fun action(code: Int): TouchAction? = ACTIONS[code]

        /** The cancelling strengths on the app's 1 to 5 steps: low, mid and high. */
        private val STEPS = mapOf(NothingNoise.LOW to 1, NothingNoise.MID to 3, NothingNoise.HIGH to 5)

        /** The strength nearest a step from 1 to 5, the stronger one when two are as near. */
        internal fun nearest(step: Int, strengths: List<Int>): Int? =
            strengths.minWithOrNull(compareBy<Int> { kotlin.math.abs(STEPS.getValue(it) - step) }.thenByDescending { STEPS.getValue(it) })

        private val NOTHING_PRESETS = listOf(0 to "Balanced", 3 to "More bass", 2 to "More treble", 1 to "Voice")
        private val DIRAC_PRESETS = listOf(0 to "Dirac Opteo", 3 to "Pop", 1 to "Rock", 5 to "Classical", 2 to "Electronic", 4 to "Enhance vocals")
    }
}
