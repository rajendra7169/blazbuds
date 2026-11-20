package com.blazify.buds.core.huawei

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.model.Link
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Huawei and Honor earbuds (Honor Choice Earbuds X7e Active checked on the real thing): battery for each earbud and the
 * case in whole percent, which is charging, and the model, firmware and serial; then whatever else the pair answers
 * for: noise control, touch controls, sound presets, pause when taken out, and low-latency game mode. Each of those is
 * asked for on connecting and shown only if the earbuds give a real answer, so every model gets just what it has.
 * They talk on a plain serial-port channel; some have a second one that speaks something else, so each way in
 * [opens] is tried until one answers in this protocol.
 */
class HuaweiBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val opens: List<suspend () -> Connection>,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    private val honorChoice = Regex("^(Earbuds X\\d|HONOR)", RegexOption.IGNORE_CASE).containsMatchIn(name)
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val answers = Channel<HuaweiPacket>(Channel.UNLIMITED)

    // What the earbuds have said about each setting; null until (unless) they answer.
    private var noise: Int? = null
    private val taps = mutableMapOf<Pair<Ear, Press>, Int>()
    private val tapChoices = mutableMapOf<Press, List<Int>>()
    private var presets: List<Int> = emptyList()
    private var preset: Int? = null
    private var autoPause: Boolean? = null
    private var lowLatency: Boolean? = null
    @kotlin.concurrent.Volatile private var watching = true

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

    /** Opens the first way in that answers in this protocol, then keeps the state fresh until the link drops. */
    private suspend fun session() {
        var link: Connection? = null
        var reading: Job? = null
        for ((index, open) in opens.withIndex()) {
            val tried = runCatching { withContext(Dispatchers.IO) { open() } }.getOrElse {
                log("way ${index + 1}: ${it.message}")
                null
            } ?: continue
            while (answers.tryReceive().isSuccess) Unit
            val job = scope.launch(Dispatchers.IO) { readLoop(tried) }
            send(tried, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3))
            val first = withTimeoutOrNull(2_500) { answers.receive() }
            if (first != null) {
                link = tried
                reading = job
                handle(first)
                log("way ${index + 1} answers")
                break
            }
            log("way ${index + 1}: no answer in this protocol")
            tried.close()
            job.cancel()
        }
        if (link == null || reading == null) throw LinkException("These earbuds don't answer")
        connection = link
        flow.update { it.copy(link = Link.Connected) }
        send(link, HuaweiPacket.ask(INFO_SERVICE, INFO, 1, 3, 7, 9, 10, 12, 15, 17))
        // Everything else this family can have; only what's answered shows.
        READS.forEach { send(link, it) }
        val handling = scope.launch {
            for (packet in answers) handle(packet)
        }
        val polling = scope.launch {
            while (true) {
                delay(30_000)
                if (watching) send(link, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3))
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

    private suspend fun send(link: Connection, packet: HuaweiPacket) {
        log("-> $packet")
        withContext(Dispatchers.IO) { link.write(HuaweiPacket.encode(packet)) }
    }

    private fun readLoop(link: Connection) {
        val reader = HuaweiPacket.Reader()
        val buffer = ByteArray(2048)
        while (true) {
            val count = link.read(buffer)
            if (count < 0) return
            for (packet in reader.feed(buffer, count)) answers.trySend(packet)
        }
    }

    private fun handle(packet: HuaweiPacket) {
        if (packet.id != CHATTER) log("<- $packet")
        if (packet.failed) return
        when (packet.id) {
            (BATTERY_SERVICE shl 8) or BATTERY, (BATTERY_SERVICE shl 8) or BATTERY_PUSH -> {
                val levels = packet.field(2) ?: return
                val charging = packet.field(3)
                fun level(i: Int): Battery? {
                    val percent = levels.getOrNull(i)?.toInt()?.and(0xFF) ?: return null
                    // 0 means that one isn't heard from: an earbud away, or the case while both are out.
                    if (percent == 0 || percent > 100) return null
                    return Battery(percent, charging = charging?.getOrNull(i)?.toInt() == 1)
                }
                flow.update { it.copy(left = level(0), right = level(1), case = level(2), overall = packet.field(1)?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { p -> p in 1..100 }?.let { p -> Battery(p) }) }
            }
            NOISE_READ, NOISE_PUSH -> {
                val data = packet.field(1) ?: return
                // The read gives (strength, mode); a change made on the earbuds gives the mode alone.
                noise = (if (data.size >= 2) data[1] else data.firstOrNull() ?: return).toInt() and 0xFF
                if (packet.id == NOISE_PUSH) connection?.let { link -> scope.launch { runCatching { send(link, HuaweiPacket.ask(0x2B, 0x2A, 1, 2)) } } }
                publishSettings()
            }
            DOUBLE_READ, TRIPLE_READ, LONG_READ -> {
                val press = when (packet.id) { DOUBLE_READ -> Press.Double; TRIPLE_READ -> Press.Triple; else -> Press.Long }
                packet.field(1)?.firstOrNull()?.let { taps[Ear.Left to press] = it.toInt() }
                packet.field(2)?.firstOrNull()?.let { taps[Ear.Right to press] = it.toInt() }
                packet.field(3)?.takeIf { it.isNotEmpty() }?.let { list -> tapChoices[press] = list.map { it.toInt() } }
                publishSettings()
            }
            EQ_READ -> {
                packet.field(3)?.takeIf { it.isNotEmpty() }?.let { list -> presets = list.map { it.toInt() and 0xFF } }
                packet.field(2)?.firstOrNull()?.let { preset = it.toInt() and 0xFF }
                publishSettings()
            }
            PAUSE_READ -> packet.field(1)?.firstOrNull()?.let { autoPause = it.toInt() == 1; publishSettings() }
            LATENCY_READ -> (packet.field(2) ?: packet.field(1))?.firstOrNull()?.let { lowLatency = it.toInt() == 1; publishSettings() }
            (INFO_SERVICE shl 8) or INFO -> {
                fun text(type: Int) = packet.field(type)?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() }
                flow.update {
                    it.copy(
                        model = text(17) ?: text(10) ?: it.model,
                        firmware = text(7) ?: text(1),
                        serial = text(9),
                    )
                }
            }
        }
    }

    override suspend fun refresh() {
        val link = connection
        if (link == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        runCatching { send(link, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3)) }
    }

    /** Puts what the earbuds have answered into the state, as the screens know it. */
    private fun publishSettings() {
        val sound = noise?.let { mode ->
            Sound(
                mode = when (mode) { 1 -> ListeningMode.NoiseCancelling; 2 -> ListeningMode.Transparency; else -> ListeningMode.Normal },
                options = SoundOptions(cancelStyles = emptyList(), transparencyStyles = false, wind = false, environment = false, cycle = false),
            )
        }
        val equalizer = if (presets.isEmpty()) null else Equalizer(
            presetId = preset ?: presets.first(),
            presets = presets.map { EqPreset(it, presetName(it), emptyList()) },
            frequencies = emptyList(),
            left = emptyList(),
            custom = false,
        )
        val controls = if (taps.isEmpty()) null else TouchControls(
            actions = taps.mapValues { (key, code) -> action(key.second, code) },
            available = TAP_ACTIONS,
            availableFor = buildMap {
                put(Press.Long, listOf(TouchAction.ListeningMode, TouchAction.None))
                tapChoices.forEach { (press, codes) -> if (press != Press.Long) put(press, codes.map { action(press, it) }.distinct()) }
            },
        )
        val toggles = buildMap {
            autoPause?.let { put(Toggle.WearDetection, it) }
            lowLatency?.let { put(Toggle.GamingMode, it) }
        }
        flow.update { it.copy(sound = sound, equalizer = equalizer, controls = controls, toggles = toggles) }
    }

    private fun action(press: Press, code: Int): TouchAction = when {
        press == Press.Long -> if (code == LONG_NOISE) TouchAction.ListeningMode else TouchAction.None
        else -> when (code) {
            1 -> TouchAction.PlayPause
            2 -> TouchAction.NextSong
            7 -> TouchAction.PreviousSong
            0 -> TouchAction.VoiceAssistant
            else -> TouchAction.None
        }
    }

    private fun code(press: Press, action: TouchAction): Int? = if (press == Press.Long) {
        when (action) { TouchAction.ListeningMode -> LONG_NOISE; TouchAction.None -> -1; else -> null }
    } else when (action) {
        TouchAction.PlayPause -> 1
        TouchAction.NextSong -> 2
        TouchAction.PreviousSong -> 7
        TouchAction.VoiceAssistant -> 0
        TouchAction.None -> -1
        else -> null
    }

    override suspend fun apply(change: Change) {
        val link = connection ?: return
        when (change) {
            is Change.Mode -> {
                val mode = when (change.mode) { ListeningMode.NoiseCancelling -> 1; ListeningMode.Transparency -> 2; ListeningMode.Normal -> 0 }
                noise = mode
                publishSettings()
                // Honor Choice earbuds take the mode alone (checked on the X7e Active); Huawei's own FreeBuds take it
                // with a strength after it.
                val value = if (honorChoice) byteArrayOf(mode.toByte()) else byteArrayOf(mode.toByte(), 0xFF.toByte())
                set(link, HuaweiPacket(0x2B, 0x04, mapOf(1 to value)), HuaweiPacket.ask(0x2B, 0x2A, 1, 2))
            }
            is Change.Preset -> {
                preset = change.id
                publishSettings()
                set(link, HuaweiPacket(0x2B, 0x49, mapOf(1 to byteArrayOf(change.id.toByte()))), HuaweiPacket.ask(0x2B, 0x4A, 1, 2, 3, 4, 5, 6, 7, 8))
            }
            is Change.Touch -> {
                val value = code(change.press, change.action) ?: return
                val (write, read) = when (change.press) {
                    Press.Double -> (0x01 to 0x1F) to HuaweiPacket.ask(0x01, 0x20, 1, 2, 3)
                    Press.Triple -> (0x01 to 0x25) to HuaweiPacket.ask(0x01, 0x26, 1, 2, 3)
                    Press.Long -> (0x2B to 0x16) to HuaweiPacket.ask(0x2B, 0x17, 1, 2, 3)
                    Press.Single -> return
                }
                taps[change.ear to change.press] = value
                publishSettings()
                val field = if (change.ear == Ear.Left) 1 else 2
                set(link, HuaweiPacket(write.first, write.second, mapOf(field to byteArrayOf(value.toByte()))), read)
            }
            is Change.Switch -> when (change.toggle) {
                Toggle.WearDetection -> {
                    autoPause = change.on
                    publishSettings()
                    set(link, HuaweiPacket(0x2B, 0x10, mapOf(1 to byteArrayOf(if (change.on) 1 else 0))), HuaweiPacket.ask(0x2B, 0x11, 1))
                }
                Toggle.GamingMode -> {
                    lowLatency = change.on
                    publishSettings()
                    set(link, HuaweiPacket(0x2B, 0x6C, mapOf(1 to byteArrayOf(if (change.on) 1 else 0))), HuaweiPacket.ask(0x2B, 0x6C, 2))
                }
                else -> {}
            }
            else -> {}
        }
    }

    /** Sends a change, then asks again so the screen shows what the earbuds really did. */
    private suspend fun set(link: Connection, change: HuaweiPacket, readBack: HuaweiPacket) {
        runCatching {
            send(link, change)
            delay(400)
            send(link, readBack)
        }
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
        const val INFO_SERVICE = 0x01
        const val INFO = 0x07
        const val BATTERY_SERVICE = 0x01
        const val BATTERY = 0x08
        const val BATTERY_PUSH = 0x27

        /** Something these earbuds send over and over that isn't about settings. */
        private const val CHATTER = 0x0A0D
        private const val NOISE_READ = 0x2B2A
        private const val NOISE_PUSH = 0x2B03
        private const val DOUBLE_READ = 0x0120
        private const val TRIPLE_READ = 0x0126
        private const val LONG_READ = 0x2B17
        private const val EQ_READ = 0x2B4A
        private const val PAUSE_READ = 0x2B11
        private const val LATENCY_READ = 0x2B6C

        /** Press and hold set to switch the noise control. */
        private const val LONG_NOISE = 10

        private val TAP_ACTIONS = listOf(TouchAction.PlayPause, TouchAction.NextSong, TouchAction.PreviousSong, TouchAction.VoiceAssistant, TouchAction.None)

        private val READS = listOf(
            HuaweiPacket.ask(0x2B, 0x2A, 1, 2),
            HuaweiPacket.ask(0x01, 0x20, 1, 2, 3),
            HuaweiPacket.ask(0x01, 0x26, 1, 2, 3),
            HuaweiPacket.ask(0x2B, 0x17, 1, 2, 3),
            HuaweiPacket.ask(0x2B, 0x4A, 1, 2, 3, 4, 5, 6, 7, 8),
            HuaweiPacket.ask(0x2B, 0x11, 1),
            HuaweiPacket.ask(0x2B, 0x6C, 2),
        )

        fun presetName(id: Int) = when (id) {
            1 -> "Default"
            2 -> "Bass boost"
            3 -> "Treble boost"
            9 -> "Voices"
            else -> "Sound $id"
        }

        /** The plain serial-port service these earbuds talk on. */
        const val SERIAL = "00001101-0000-1000-8000-00805f9b34fb"
    }
}
