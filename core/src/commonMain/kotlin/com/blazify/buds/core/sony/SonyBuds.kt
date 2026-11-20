package com.blazify.buds.core.sony

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.AutoPowerOff
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.model.TransparencyStyle
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
 * Sony earbuds and headphones (WH-1000XM3 to XM6, WF-1000XM3 to XM6, the LinkBuds, WF-C500 to C710N, WH-CH720N and
 * others): battery for headphones, or each earbud and the case, with charging; noise cancelling, ambient sound and
 * normal, with focus on voice and the ambient sound level; the equalizer's presets and a curve of the person's own; the touch keys; DSEE upscaling,
 * speak-to-chat, pause when taken off and automatic power off; and the model and firmware. Each pair lists the
 * functions it has on connecting, and only those are asked for and shown.
 *
 * They talk on one of two serial-port services, set two's first, then set one's; each way in [opens] is tried until
 * one answers.
 */
class SonyBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val opens: List<suspend () -> Connection>,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val incoming = Channel<SonyFrame>(Channel.UNLIMITED)
    @kotlin.concurrent.Volatile private var watching = true

    // One message in flight at a time, each waiting for its acknowledgement.
    private val sending = Mutex()
    private val writing = Mutex()
    private var seq = 0
    @kotlin.concurrent.Volatile private var acked: CompletableDeferred<Int>? = null
    private var lastIn: SonyFrame? = null

    // What the earbuds have said; null until (unless) they answer.
    private var set: SonySet? = null
    private var features: SonyFeatures? = null
    private var noise: SonyNoise? = null
    /** Set one's ambient sound steps from its capability answer; null until (unless) it answers. */
    private var ambientSteps: Int? = null
    /** The last ambient sound level heard, kept because set one reports 0 while cancelling. */
    private var ambientLevel: Int? = null
    private var eq: SonyEq? = null
    private var upscaling: Boolean? = null
    private var wearPause: Boolean? = null
    private var speakToChat: Boolean? = null
    private var preview = 1
    private var offChoices: List<Int>? = null
    private var offSetting: Pair<Int, Int>? = null
    private var keys: List<Int>? = null
    private val earbuds = Regex("^(WF-|LinkBuds)", RegexOption.IGNORE_CASE).containsMatchIn(name)

    fun start() {
        if (runner != null) return
        runner = scope.launch {
            var wait = 2_000L
            while (isActive) {
                flow.update { if (it.link == Link.Lost) it else it.copy(link = Link.Connecting) }
                try {
                    session()
                    wait = 2_000L
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log("link: ${e.message}")
                }
                connection?.close()
                connection = null
                flow.update { it.copy(link = Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    /** Opens the first way in that answers, learns what the pair has, then keeps the state fresh until the link drops. */
    private suspend fun session() {
        var link: Connection? = null
        var reading: Job? = null
        var first: ByteArray? = null
        for ((index, open) in opens.withIndex()) {
            val tried = runCatching { withContext(Dispatchers.IO) { open() } }.getOrElse {
                log("way ${index + 1}: ${it.message}")
                null
            } ?: continue
            while (incoming.tryReceive().isSuccess) Unit
            seq = 0
            lastIn = null
            val job = scope.launch(Dispatchers.IO) { readLoop(tried) }
            connection = tried
            runCatching { send(Sony.protocolInfo()) }
            val reply = withTimeoutOrNull(3_000) {
                var got: ByteArray? = null
                while (got == null) {
                    val frame = incoming.receive()
                    if (frame.payload.firstOrNull()?.toInt() == 0x01) got = frame.payload
                }
                got
            }
            if (reply != null) {
                link = tried
                reading = job
                first = reply
                log("way ${index + 1} answers")
                break
            }
            log("way ${index + 1}: no answer")
            connection = null
            tried.close()
            job.cancel()
        }
        if (link == null || reading == null || first == null) throw LinkException("These earbuds don't answer")
        forget()
        // An answer of any other length is taken as the newer set.
        val speaks = Sony.setOf(first) ?: SonySet.Two
        set = speaks
        log("command set $speaks")
        flow.update { it.copy(link = Link.Connected) }
        val handling = scope.launch {
            for (frame in incoming) runCatching { handle(frame.payload) }.onFailure { log("handle: ${it.message}") }
        }
        send(Sony.deviceInfo(Sony.MODEL))
        send(Sony.deviceInfo(Sony.FIRMWARE))
        send(Sony.supportFunctions())
        val polling = scope.launch {
            while (true) {
                delay(30_000)
                if (watching) features?.let { f -> f.batteries.forEach { send(Sony.battery(f.set, it)) } }
            }
        }
        try {
            reading.join()
        } finally {
            polling.cancel()
            handling.cancel()
            link.close()
        }
    }

    /** Drops what an earlier connection learned, so a pair is asked afresh each time it comes back. */
    private fun forget() {
        features = null
        noise = null
        ambientSteps = null
        ambientLevel = null
        eq = null
        upscaling = null
        wearPause = null
        speakToChat = null
        offChoices = null
        offSetting = null
        keys = null
    }

    /** Asks for everything the pair listed. */
    private suspend fun readAll(f: SonyFeatures) {
        f.batteries.forEach { send(Sony.battery(f.set, it)) }
        f.noise?.let {
            // Set one tells how many steps its ambient sound level has; set two's run from 1 to 20.
            if (f.set == SonySet.One) send(Sony.noiseCapability(it))
            send(Sony.noise(it))
        }
        f.eq?.let { type ->
            // Sony's app asks set two's equalizer with error codes for no capability.
            if (!(f.set == SonySet.Two && type == 0x04)) send(Sony.eqCapability(type))
            send(Sony.eqBands(type))
            send(Sony.eqSetting(type))
        }
        if (f.upscaling) send(Sony.upscaling(f.set))
        if (f.wearPause) send(Sony.system(Sony.wearPauseKind(f.set)))
        f.speakToChat?.let { send(Sony.system(it)) }
        f.autoOff?.let { send(Sony.autoOffCapability(f.set, it)); send(Sony.autoOff(f.set, it)) }
        if (earbuds) f.keys?.let { send(Sony.system(it)) }
    }

    /** Sends one message and waits for its acknowledgement, sending it again if none comes. */
    private suspend fun send(payload: ByteArray) {
        val link = connection ?: return
        sending.withLock {
            repeat(5) { attempt ->
                val waiting = CompletableDeferred<Int>()
                acked = waiting
                if (attempt == 0) log("-> ${SonyFrame.hex(payload)}")
                write(link, SonyFrame.encode(SonyFrame.TABLE1, seq, payload))
                val next = withTimeoutOrNull(750) { waiting.await() }
                if (next != null) {
                    seq = next
                    return
                }
            }
            log("no acknowledgement for ${SonyFrame.hex(payload)}")
        }
    }

    private suspend fun write(link: Connection, bytes: ByteArray) {
        writing.withLock { withContext(Dispatchers.IO) { link.write(bytes) } }
    }

    private suspend fun readLoop(link: Connection) {
        val reader = SonyFrame.Reader()
        val buffer = ByteArray(2048)
        while (true) {
            val count = link.read(buffer)
            if (count < 0) return
            for (frame in reader.feed(buffer, count)) {
                if (frame.isAck) {
                    acked?.complete(frame.seq)
                    continue
                }
                write(link, SonyFrame.ack(frame.seq))
                // The same number twice in a row is a message sent again: answered, not handled twice.
                val before = lastIn
                lastIn = frame
                if (before != null && before.seq == frame.seq && before.payload.contentEquals(frame.payload)) continue
                if (frame.kind == SonyFrame.TABLE1) incoming.trySend(frame)
            }
        }
    }

    private suspend fun handle(payload: ByteArray) {
        if (payload.isEmpty()) return
        log("<- ${SonyFrame.hex(payload)}")
        val speaks = set ?: return
        val command = payload[0].toInt() and 0xFF
        when {
            command == 0x05 -> Sony.text(payload)?.let { (kind, text) ->
                if (text.isEmpty()) return
                when (kind) {
                    Sony.MODEL -> flow.update { it.copy(model = text) }
                    Sony.FIRMWARE -> flow.update { it.copy(firmware = text) }
                }
            }
            command == 0x07 && features == null -> {
                val functions = Sony.functions(speaks, payload)
                if (functions.isEmpty()) return
                val f = SonyFeatures.from(speaks, functions)
                features = f
                f.eq?.let { eq = SonyEq(speaks, it, f.eqCustom) }
                log("features $f")
                scope.launch { runCatching { readAll(f) } }
            }
            Sony.isBattery(speaks, command) -> Sony.batteries(payload)?.let { b ->
                flow.update {
                    when (payload[1].toInt() and 0xFF) {
                        0x00, 0x08 -> it.copy(overall = b.overall)
                        0x02, 0x0A -> it.copy(case = b.case)
                        else -> it.copy(left = b.left, right = b.right)
                    }
                }
            }
            command == 0x67 || command == 0x69 -> {
                val type = payload.getOrNull(1)?.toInt()?.and(0xFF)
                if (type != null && type == features?.noise) {
                    heard(SonyNoise(speaks, payload))
                    publish()
                }
            }
            command == 0x61 && speaks == SonySet.One -> {
                val type = payload.getOrNull(1)?.toInt()?.and(0xFF)
                if (type != null && type == features?.noise) Sony.ambientSteps(payload)?.let { ambientSteps = it; publish() }
            }
            command in listOf(0x51, 0x5B, 0x57, 0x59) -> if (eq?.read(payload) == true) publish()
            command == 0xE7 || command == 0xE9 -> Sony.upscalingOn(speaks, payload)?.let { upscaling = it; publish() }
            command == 0xF1 || command == 0x21 -> features?.autoOff?.let { kind ->
                Sony.autoOffChoices(speaks, kind, payload)?.let { offChoices = it; publish() }
            }
            command == 0x27 || command == 0x29 -> features?.autoOff?.let { kind ->
                Sony.autoOffSetting(speaks, kind, payload)?.let { offSetting = it; publish() }
            }
            command == 0xF7 || command == 0xF9 -> {
                val f = features ?: return
                val kind = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: return
                when (kind) {
                    Sony.wearPauseKind(speaks) -> if (f.wearPause) Sony.wearPauseOn(speaks, payload)?.let { wearPause = it }
                    f.speakToChat -> Sony.speakToChatOn(speaks, kind, payload)?.let {
                        speakToChat = it
                        if (speaks == SonySet.Two) preview = payload.getOrNull(3)?.toInt()?.and(0xFF) ?: preview
                    }
                    f.autoOff -> if (speaks == SonySet.One) Sony.autoOffSetting(speaks, kind, payload)?.let { offSetting = it }
                    f.keys -> Sony.keys(kind, payload)?.let { keys = it }
                }
                publish()
            }
        }
    }

    /** Takes in the noise control the earbuds report, remembering the ambient sound level for when it reads 0. */
    private fun heard(n: SonyNoise) {
        noise = n
        n.level?.takeIf { it > 0 && n.mode != ListeningMode.NoiseCancelling }?.let { ambientLevel = it }
    }

    /** How many steps the ambient sound level has: what set one says, else 20 where the layout carries one. */
    private fun levels(n: SonyNoise): Int = when {
        n.level == null -> 0
        else -> ambientSteps ?: if (n.hasLevel) SonyNoise.LEVELS else 0
    }

    /** The level to send when going into ambient sound from a layout that reads 0 (set one while cancelling). */
    private fun restoredLevel(n: SonyNoise, mode: ListeningMode): Int? {
        val steps = levels(n)
        if (steps == 0 || mode != ListeningMode.Transparency || (n.level ?: 0) > 0) return null
        return (ambientLevel ?: steps).coerceIn(1, steps)
    }

    /** Puts what the earbuds have answered into the state, as the screens know it. */
    private fun publish() {
        if (features == null) return
        val sound = noise?.let { n ->
            val steps = levels(n)
            Sound(
                mode = n.mode,
                transparencyStyle = if (n.voice == true) TransparencyStyle.Voice else TransparencyStyle.Full,
                ambientLevel = if (steps > 0) (n.level?.takeIf { it > 0 } ?: ambientLevel ?: steps).coerceIn(1, steps) else 0,
                options = SoundOptions(
                    cancelStyles = emptyList(),
                    transparencyStyles = n.voice != null,
                    wind = false,
                    environment = false,
                    cycle = false,
                    modes = n.modes,
                    ambientLevels = steps,
                ),
            )
        }
        val toggles = buildMap {
            upscaling?.let { put(Toggle.Upscaling, it) }
            wearPause?.let { put(Toggle.WearDetection, it) }
            speakToChat?.let { put(Toggle.SpeakToChat, it) }
        }
        val autoPowerOff = offChoices?.let { ids ->
            val (active, _) = offSetting ?: return@let null
            val times = ids.mapNotNull { Sony.minutes(it) }
            // Earbuds that only turn off when taken out get just the switch.
            if (times.isEmpty() && Sony.OFF_WHEN_TAKEN_OFF !in ids) return@let null
            AutoPowerOff(enabled = active != Sony.OFF_NEVER, minutes = Sony.minutes(active) ?: -1, choices = times)
        }
        val controls = keys?.takeIf { it.size == 2 }?.let { list ->
            val actions = buildMap {
                listOf(Ear.Left, Ear.Right).forEachIndexed { i, ear -> action(list[i])?.let { put(ear to Press.Single, it) } }
            }
            if (actions.isEmpty()) null else TouchControls(actions, KEY_ACTIONS)
        }
        flow.update {
            it.copy(
                sound = sound,
                equalizer = eq?.equalizer(),
                controls = controls,
                toggles = toggles,
                autoPowerOff = autoPowerOff,
            )
        }
    }

    private fun action(preset: Int): TouchAction? = when (preset) {
        Sony.KEY_NOISE -> TouchAction.ListeningMode
        Sony.KEY_PLAYBACK -> TouchAction.PlayPause
        Sony.KEY_NONE -> TouchAction.None
        else -> null
    }

    private fun preset(action: TouchAction): Int? = when (action) {
        TouchAction.ListeningMode -> Sony.KEY_NOISE
        TouchAction.PlayPause -> Sony.KEY_PLAYBACK
        TouchAction.None -> Sony.KEY_NONE
        else -> null
    }

    override suspend fun refresh() {
        if (connection == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        val f = features ?: return
        runCatching { f.batteries.forEach { send(Sony.battery(f.set, it)) } }
    }

    override suspend fun apply(change: Change) {
        val f = features ?: return
        val speaks = f.set
        runCatching {
            when (change) {
                is Change.Mode -> {
                    val now = noise ?: return
                    val message = now.set(change.mode, level = restoredLevel(now, change.mode)) ?: return
                    heard(SonyNoise(speaks, message))
                    publish()
                    send(message)
                    send(Sony.noise(now.type))
                }
                is Change.TransparencyKind -> {
                    val now = noise ?: return
                    val message = now.set(voice = change.style == TransparencyStyle.Voice, level = restoredLevel(now, now.mode)) ?: return
                    heard(SonyNoise(speaks, message))
                    publish()
                    send(message)
                    send(Sony.noise(now.type))
                }
                is Change.AmbientLevel -> {
                    val now = noise ?: return
                    val steps = levels(now)
                    if (steps == 0) return
                    val mode = if (ListeningMode.Transparency in now.modes) ListeningMode.Transparency else now.mode
                    val message = now.set(mode, level = change.level.coerceIn(1, steps)) ?: return
                    heard(SonyNoise(speaks, message))
                    publish()
                    send(message)
                    send(Sony.noise(now.type))
                }
                is Change.Preset -> {
                    val e = eq ?: return
                    e.preset = change.id
                    publish()
                    send(e.choose(change.id))
                    send(Sony.eqSetting(e.type))
                }
                is Change.Bands -> {
                    val e = eq ?: return
                    val message = e.curve(change.left) ?: return
                    e.read(byteArrayOf(0x57) + message.copyOfRange(1, message.size))
                    publish()
                    send(message)
                    send(Sony.eqSetting(e.type))
                }
                is Change.Switch -> when (change.toggle) {
                    Toggle.Upscaling -> if (f.upscaling) {
                        upscaling = change.on
                        publish()
                        send(Sony.setUpscaling(speaks, change.on))
                        send(Sony.upscaling(speaks))
                    }
                    Toggle.WearDetection -> if (f.wearPause) {
                        wearPause = change.on
                        publish()
                        send(Sony.setWearPause(speaks, change.on))
                        send(Sony.system(Sony.wearPauseKind(speaks)))
                    }
                    Toggle.SpeakToChat -> f.speakToChat?.let { kind ->
                        speakToChat = change.on
                        publish()
                        send(Sony.setSpeakToChat(speaks, kind, change.on, preview))
                        send(Sony.system(kind))
                    }
                    else -> {}
                }
                is Change.PowerOff -> {
                    val kind = f.autoOff ?: return
                    val ids = offChoices ?: return
                    val (_, last) = offSetting ?: return
                    val active = when {
                        !change.enabled -> Sony.OFF_NEVER
                        else -> Sony.idFor(change.minutes)?.takeIf { it in ids }
                            ?: Sony.OFF_WHEN_TAKEN_OFF.takeIf { it in ids }
                            ?: ids.firstOrNull { Sony.minutes(it) != null }
                            ?: return
                    }
                    offSetting = active to (if (Sony.minutes(active) != null) active else last)
                    publish()
                    send(Sony.setAutoOff(speaks, kind, active, last))
                    send(Sony.autoOff(speaks, kind))
                }
                is Change.Touch -> {
                    val kind = f.keys ?: return
                    val now = keys?.takeIf { it.size == 2 } ?: return
                    if (change.press != Press.Single) return
                    val value = preset(change.action) ?: return
                    val next = now.toMutableList().also { it[if (change.ear == Ear.Left) 0 else 1] = value }
                    keys = next
                    publish()
                    send(Sony.setKeys(kind, next))
                    send(Sony.system(kind))
                }
                else -> {}
            }
        }.onFailure { log("change: ${it.message}") }
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
        /** What a touch key can be set to here: the noise control switch, play and pause, or nothing. */
        private val KEY_ACTIONS = listOf(TouchAction.ListeningMode, TouchAction.PlayPause, TouchAction.None)
    }
}
