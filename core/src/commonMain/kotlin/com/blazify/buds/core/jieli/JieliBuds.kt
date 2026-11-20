package com.blazify.buds.core.jieli

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
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
 * Earbuds on a JieLi chip that speak its RCSP protocol, checked on the Ultima Boom Sleek: battery for each earbud, the
 * ten-band equalizer with the earbuds' own presets (their curves read from the earbuds) and a custom curve, the maker's
 * extra sounds, single and double tap for each ear, and game mode. They want the JieLi login first.
 */
class JieliBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val open: suspend () -> Connection,
    private val log: (String) -> Unit = {},
    /** Told once whether these earbuds turned out to speak JieLi; set when trying earbuds the app doesn't know. */
    private val verdict: ((Boolean) -> Unit)? = null,
) : Buds {
    private var decided = false
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val sending = Mutex()
    private var number = 0
    private val waiting = mutableMapOf<Int, CompletableDeferred<Rcsp>>()

    // What the earbuds have said.
    private var eqMode: Int? = null
    private var eqGains: List<Double> = emptyList()
    private var frequencies: List<Int> = emptyList()
    private val presetCurves = mutableMapOf<Int, List<Double>>()
    private var extraSound: Int? = null
    private val keys = mutableMapOf<Pair<Ear, Press>, Int>()
    private var game: Boolean? = null
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
                } catch (e: NotJieli) {
                    // Earbuds that don't log in this way aren't JieLi: say so once and stop trying.
                    log("not JieLi: ${e.message}")
                    connection?.close()
                    if (!decided) { decided = true; verdict?.invoke(false) }
                    return@launch
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log("link: ${e.message}")
                }
                connection?.close()
                connection = null
                waiting.values.forEach { it.cancel() }
                waiting.clear()
                flow.update { it.copy(link = Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    private suspend fun session() {
        val link = withContext(Dispatchers.IO) { open() }
        connection = link
        val raw = Channel<ByteArray>(Channel.UNLIMITED)
        val reader = Rcsp.Reader()
        var loggedIn = false
        val reading = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            while (true) {
                val count = link.read(buffer)
                if (count < 0) return@launch
                if (!loggedIn) raw.trySend(buffer.copyOf(count)) else for (packet in reader.feed(buffer, count)) handle(packet)
            }
        }
        try {
            login(link, raw)
            loggedIn = true
            if (!decided) { decided = true; verdict?.invoke(true) }
            flow.update { it.copy(link = Link.Connected) }
            readAll()
            val polling = scope.launch {
                while (true) {
                    delay(30_000)
                    if (watching) runCatching { ask(0xC1, MASK_BATTERY) }
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

    /** JieLi's login, both ways: we check them, then they check us. */
    private suspend fun login(link: Connection, raw: Channel<ByteArray>) {
        var pending = ByteArray(0)
        suspend fun next(size: Int): ByteArray {
            while (pending.size < size) {
                val more = withTimeoutOrNull(4_000) { raw.receive() } ?: throw if (verdict != null && !decided) NotJieli("no answer to the login") else LinkException("These earbuds didn't answer the login")
                pending += more
            }
            return pending.copyOfRange(0, size).also { pending = pending.copyOfRange(size, pending.size) }
        }
        suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) { link.write(bytes) }
        val ours = JieliAuth.challenge()
        write(ours)
        val answer = next(17)
        if (!answer.contentEquals(JieliAuth.respond(ours.copyOfRange(1, 17)))) throw if (verdict != null && !decided) NotJieli("a different answer to the login") else LinkException("These earbuds answered the login wrongly")
        write(JieliAuth.PASS)
        val theirs = next(17)
        if (theirs[0].toInt() != 0) throw LinkException("Unexpected login from the earbuds")
        write(JieliAuth.respond(theirs.copyOfRange(1, 17)))
        val pass = next(5)
        if (!pass.contentEquals(JieliAuth.PASS)) throw LinkException("The earbuds didn't accept the login")
        log("logged in")
    }

    /** Sends a command and waits for its answer. */
    private suspend fun command(opcode: Int, data: ByteArray): Rcsp? = sending.withLock {
        val link = connection ?: return null
        val sn = number++ and 0xFF
        val answer = CompletableDeferred<Rcsp>()
        waiting[sn] = answer
        val packet = Rcsp(Rcsp.COMMAND, opcode, byteArrayOf(sn.toByte()) + data)
        log("-> $packet")
        withContext(Dispatchers.IO) { link.write(Rcsp.encode(packet)) }
        withTimeoutOrNull(2_500) { answer.await() }.also { waiting.remove(sn) }
    }

    /** Asks for the settings in [mask]; the answer comes back as setting blocks. */
    private suspend fun ask(opcode: Int, mask: Int) {
        val answer = command(opcode, if (opcode == 0x07) byteArrayOf(0xFF.toByte()) + int4(mask) else int4(mask)) ?: return
        val body = answer.body
        if (body.size < 2 || body[0].toInt() != 0) return
        if (opcode == 0x07) readSystem(body.copyOfRange(3, body.size)) else readCustom(body.copyOfRange(2, body.size))
        publish()
    }

    private suspend fun readAll() {
        ask(0x07, MASK_EQ)
        ask(0xC1, -1)
        ask(0xC1, MASK_GAME)
    }

    private fun handle(packet: Rcsp) {
        // Twice a second the earbuds say how they are: [number, maker, product, version, address, ?, left, right, ?, ?].
        if (packet.isNews && packet.opcode == 0xC2) {
            val b = packet.body
            if (b.size >= 6) batteries(b[b.size - 4], b[b.size - 3])
            return
        }
        log("<- $packet")
        if (packet.isAnswer && packet.body.size >= 2) {
            waiting[packet.body[1].toInt() and 0xFF]?.complete(packet)
            return
        }
        if (packet.isNews && packet.opcode == 0x09 && packet.body.size > 2) {
            readSystem(packet.body.copyOfRange(2, packet.body.size))
            publish()
        }
    }

    /** Blocks of [length, kind, data] from the earbuds' system settings: kind 4 the equalizer, 12 its presets. */
    private fun readSystem(blocks: ByteArray) {
        var at = 0
        while (at + 2 <= blocks.size) {
            val size = blocks[at].toInt() and 0xFF
            if (size == 0 || at + 1 + size > blocks.size) break
            val kind = blocks[at + 1].toInt() and 0xFF
            val data = blocks.copyOfRange(at + 2, at + 1 + size)
            when (kind) {
                4 -> if (data.size >= 11) {
                    eqMode = data[0].toInt() and 0xFF
                    eqGains = (1..10).map { data[it] / 2.0 }
                }
                12 -> if (data.isNotEmpty()) {
                    val bands = data[0].toInt() and 0xFF
                    frequencies = (0 until bands).map { ((data[1 + it * 2].toInt() and 0xFF) shl 8) or (data[2 + it * 2].toInt() and 0xFF) }
                    var p = 1 + bands * 2
                    while (p + 1 + bands <= data.size) {
                        presetCurves[data[p].toInt() and 0xFF] = (1..bands).map { data[p + it] / 2.0 }
                        p += 1 + bands
                    }
                }
            }
            at += 1 + size
        }
    }

    /** Blocks of [length, kind, data] from the maker's settings: 0 battery, 2 the taps, 5 game mode. */
    private fun readCustom(blocks: ByteArray) {
        var at = 0
        while (at + 2 <= blocks.size) {
            val size = blocks[at].toInt() and 0xFF
            if (size == 0 || at + 1 + size > blocks.size) break
            val kind = blocks[at + 1].toInt() and 0xFF
            val data = blocks.copyOfRange(at + 2, at + 1 + size)
            when (kind) {
                0 -> if (data.size >= 2) batteries(data[0], data[1])
                2 -> {
                    var k = 0
                    while (k + 3 <= data.size) {
                        val ear = if (data[k].toInt() == 1) Ear.Left else Ear.Right
                        val press = if (data[k + 1].toInt() == 1) Press.Single else Press.Double
                        keys[ear to press] = data[k + 2].toInt() and 0xFF
                        k += 3
                    }
                }
                5 -> data.firstOrNull()?.let { game = it.toInt() == 2 }
            }
            at += 1 + size
        }
    }

    /**
     * Each earbud's level: the low seven bits are the percent, the top bit says it's charging in the case (seen on
     * the Ultima: 0xE4 in the case, 0x64 in the ear, both 100 %). The case's own level isn't sent.
     */
    private fun batteries(left: Byte, right: Byte) {
        fun level(b: Byte): Battery? {
            val v = b.toInt() and 0xFF
            val percent = v and 0x7F
            return if (percent in 1..100) Battery(percent, charging = v and 0x80 != 0) else null
        }
        flow.update { it.copy(left = level(left), right = level(right)) }
    }

    private fun publish() {
        val equalizer = eqMode?.let { mode ->
            Equalizer(
                presetId = extraSound?.let { EXTRA + it } ?: if (mode == CUSTOM_MODE) Equalizer.CUSTOM else mode,
                presets = PRESET_NAMES.mapNotNull { (id, label) -> presetCurves[id]?.let { EqPreset(id, label, it) } } +
                    EXTRA_NAMES.mapIndexed { i, label -> EqPreset(EXTRA + i, label, emptyList()) },
                frequencies = frequencies.ifEmpty { DEFAULT_FREQUENCIES },
                left = eqGains,
            )
        }
        val controls = if (keys.isEmpty()) null else TouchControls(
            actions = keys.mapValues { (_, code) -> ACTIONS.entries.firstOrNull { it.value == code }?.key ?: TouchAction.None },
            available = DOUBLE,
            availableFor = mapOf(Press.Single to SINGLE, Press.Double to DOUBLE),
        )
        val toggles = buildMap { game?.let { put(Toggle.GamingMode, it) } }
        flow.update { it.copy(equalizer = equalizer, controls = controls, toggles = toggles) }
    }

    override suspend fun refresh() {
        if (connection == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        ask(0xC1, MASK_BATTERY)
    }

    override suspend fun apply(change: Change) {
        connection ?: return
        when (change) {
            is Change.Preset -> if (change.id >= EXTRA) {
                // The maker's own sounds, set by a command of their own.
                extraSound = change.id - EXTRA
                publish()
                command(0xFF, byteArrayOf(0x10, (change.id - EXTRA).toByte()))
            } else {
                extraSound = null
                setEq(change.id, presetCurves[change.id]?.let { List(10) { 0.0 } } ?: List(10) { 0.0 })
            }
            is Change.Bands -> {
                extraSound = null
                setEq(CUSTOM_MODE, change.left)
            }
            is Change.Touch -> {
                val code = ACTIONS[change.action] ?: return
                val ear = if (change.ear == Ear.Left) 1 else 2
                val press = if (change.press == Press.Single) 1 else 2
                keys[change.ear to change.press] = code
                publish()
                command(0xC0, byteArrayOf(4, 2, ear.toByte(), press.toByte(), code.toByte()))
                ask(0xC1, MASK_KEYS)
            }
            is Change.Switch -> if (change.toggle == Toggle.GamingMode) {
                game = change.on
                publish()
                command(0xC0, byteArrayOf(2, 5, if (change.on) 2 else 1))
                ask(0xC1, MASK_GAME)
            }
            else -> {}
        }
    }

    /** Sets the equalizer: a preset by number with its curve left to the earbuds, or the custom curve. */
    private suspend fun setEq(mode: Int, gains: List<Double>) {
        eqMode = mode
        if (mode == CUSTOM_MODE) eqGains = gains
        publish()
        val bands = ByteArray(10) { kotlin.math.round((gains.getOrElse(it) { 0.0 }.coerceIn(-6.0, 6.0)) * 2).toInt().toByte() }
        command(0x08, byteArrayOf(0xFF.toByte(), 0x0C, 0x04, mode.toByte()) + bands)
        ask(0x07, MASK_EQ)
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

    private fun int4(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    companion object {
        private const val MASK_EQ = 0x1010
        private const val MASK_BATTERY = 0x01
        private const val MASK_KEYS = 0x04
        private const val MASK_GAME = 0x20
        private const val CUSTOM_MODE = 6

        /** Where the maker's own sounds start among the preset numbers. */
        private const val EXTRA = 100
        private val PRESET_NAMES = listOf(0 to "Default", 1 to "Rock", 2 to "Pop", 3 to "Classical", 4 to "Jazz", 5 to "Country")
        private val EXTRA_NAMES = listOf("Signature Sound", "Balance Sound")
        private val DEFAULT_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        private val ACTIONS = linkedMapOf(
            TouchAction.PlayPause to 5,
            TouchAction.NextSong to 4,
            TouchAction.PreviousSong to 3,
            TouchAction.VolumeUp to 9,
            TouchAction.VolumeDown to 10,
            TouchAction.None to 0,
        )
        private val SINGLE = listOf(TouchAction.VolumeUp, TouchAction.VolumeDown)
        private val DOUBLE = ACTIONS.keys.toList()
    }
}

/** The earbuds didn't answer JieLi's login: they speak something else. */
class NotJieli(message: String) : Exception(message)
