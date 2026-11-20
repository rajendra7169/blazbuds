package com.blazify.buds.core.soundcore

import com.blazify.buds.core.demo.with
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.FitResult
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.Toggle
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A Liberty 4 NC, live. It opens the control channel through [open], asks for everything, then keeps the state in
 * step with what the earbuds push (a tap that changes the mode, a bud put in the case, the battery). A change shows
 * on screen at once and is sent; if the earbuds don't confirm it after three tries, the screen goes back to what they
 * really have. A dropped link is tried again by itself.
 */
class SoundcoreBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val open: suspend () -> Connection,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "A3947", Link.Connecting))
    override val state: StateFlow<BudsState> = flow

    private var connection: Connection? = null
    private var raw: ByteArray? = null
    private var ldac: Boolean? = null
    private var hosts: List<Host> = emptyList()
    private val pending = mutableMapOf<Int, ArrayDeque<CompletableDeferred<Packet>>>()

    /**
     * Answers still on their way for messages sent more than once. A slow answer and the answer to the repeat both
     * arrive; the late one says how things were before, so it must not count as the answer to anything newer.
     */
    private val late = mutableMapOf<Int, Int>()

    /**
     * The earbuds take a moment to switch modes and, until they have, report the old one in their "everything"
     * answer. For a few seconds after they confirm a mode change, that change wins over such a report.
     */
    private var soundSet: Pair<ByteArray, TimeMark>? = null
    private val pendingLock = Mutex()
    private val sending = Mutex()
    private var deviceList: Channel<Packet>? = null
    private var fitAnswers: Channel<Packet>? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * When a switch that restarts the earbuds (best quality, two devices) was made. They drop the link a moment later
     * and come back about seven seconds after; reconnecting the control channel while their music link is still
     * settling made the first seconds of music cut out, so for a drop soon after a switch the app waits it out.
     */
    private var restartedAt: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null

    fun start() {
        if (runner != null) return
        runner = scope.launch {
            var wait = 2_000L
            while (isActive) {
                // Retrying by itself, it keeps saying what is wrong rather than flashing back to "connecting" each time.
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
                failAll()
                val restart = restartedAt?.takeIf { it.elapsedNow().inWholeMilliseconds < RESTART_WINDOW }
                if (restart != null) {
                    // Restarting on purpose: say "connecting", not "lost", and come back once the music has settled.
                    restartedAt = null
                    flow.update { it.copy(link = Link.Connecting) }
                    delay(RESTART_HOLD)
                    wait = 2_000L
                    continue
                }
                flow.update { it.copy(link = if (busy) Link.Busy else Link.Lost) }
                // Try again soon, a little less often each time, or at once when asked to.
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    /** One connection, from opening to losing it. */
    private suspend fun session() {
        val link = withContext(Dispatchers.IO) { open() }
        connection = link
        val reading = scope.launch(Dispatchers.IO) { readLoop(link) }
        try {
            val state = request(Packet(A3947.STATE)) ?: throw LinkException("The earbuds didn't answer")
            if (!A3947.isState(state.body)) throw LinkException("These don't look like Liberty 4 NC earbuds")
            raw = state.body
            ldac = request(Packet(A3947.LDAC))?.body?.firstOrNull()?.let { it.toInt() == 1 }
            publish()
            if (raw!![A3947.TWO_DEVICES].toInt() == 1) loadHosts()
            val polling = scope.launch {
                while (true) {
                    delay(POLL)
                    // Other apps' changes don't always reach this link; asking is the sure way to see them.
                    if (watching && !sending.isLocked) request(Packet(A3947.STATE))?.body?.takeIf(A3947::isState)?.let {
                        raw = keepSound(it)
                        publish()
                    }
                }
            }
            try {
                reading.join()
            } finally {
                polling.cancel()
            }
        } finally {
            // A read waiting on the radio doesn't hear a cancel; closing the link wakes it.
            link.close()
            reading.cancel()
        }
    }

    private suspend fun readLoop(link: Connection) {
        val reader = PacketCodec.Reader()
        val buffer = ByteArray(2048)
        while (true) {
            val count = link.read(buffer)
            if (count < 0) return
            for (packet in reader.feed(buffer, count)) handle(packet)
        }
    }

    private suspend fun handle(packet: Packet) {
        log("<- $packet".take(90))
        if (packet.command == A3947.DEVICES) deviceList?.let { it.trySend(packet); return }
        if (packet.command == A3947.FIT_TEST) fitAnswers?.let { it.trySend(packet); return }
        val waiter = pendingLock.withLock {
            val owed = late[packet.command] ?: 0
            if (owed > 0) {
                late[packet.command] = owed - 1
                return@withLock DROP
            }
            pending[packet.command]?.removeFirstOrNull()
        }
        if (waiter === DROP) return
        if (waiter != null) {
            waiter.complete(packet)
            return
        }
        val now = raw ?: return
        val body = packet.body
        when (packet.command) {
            A3947.STATE -> if (A3947.isState(body)) raw = keepSound(body)
            A3947.TWS -> {
                if (body.size >= 2) body.copyInto(now, A3947.HOST, 0, 2)
                log("in case $packet")
            }
            A3947.BATTERY -> {
                if (body.size >= 2) body.copyInto(now, A3947.LEFT_LEVEL, 0, 2)
                // A third number, when the earbuds send one, is the case (0 while they can't hear it).
                if (body.size >= 3 && body[2].toInt() in 1..5) now[A3947.CASE_LEVEL] = body[2]
                log("battery $packet")
            }
            A3947.CHARGING -> if (body.size >= 2) body.copyInto(now, A3947.LEFT_CHARGING, 0, 2)
            A3947.FIRMWARE -> if (body.size >= 26) body.copyInto(now, A3947.FIRMWARE_LEFT, 0, 26)
            A3947.SOUND_UPDATE -> if (body.size >= 7) body.copyInto(now, A3947.SOUND, 0, 7)
            A3947.LDAC -> ldac = body.firstOrNull()?.toInt() == 1
            else -> {
                log("unhandled $packet")
                return
            }
        }
        publish()
    }

    private var lastCase = -1

    private fun publish() {
        val now = raw ?: return
        val case = now[A3947.CASE_LEVEL].toInt() and 0xFF
        if (case != lastCase) {
            lastCase = case
            log("case level $case (next byte ${now[A3947.CASE_LEVEL + 1].toInt() and 0xFF})")
        }
        flow.value = A3947.read(now, ldac, hosts, name)
    }

    /** Sends a message and waits for the earbuds' answer with the same command: three tries, a little longer each. */
    private suspend fun request(packet: Packet): Packet? = sending.withLock {
        val link = connection ?: return null
        val bytes = PacketCodec.encode(packet)
        // Everything takes the earbuds longer to put together than a short answer.
        val first = if (packet.command == A3947.STATE) 1_500L else 500L
        for (attempt in 1..3) {
            val waiter = CompletableDeferred<Packet>()
            pendingLock.withLock { pending.getOrPut(packet.command) { ArrayDeque() }.addLast(waiter) }
            log("-> $packet try $attempt")
            withContext(Dispatchers.IO) { link.write(bytes) }
            val answer = withTimeoutOrNull(first * attempt) { waiter.await() }
            if (answer != null) {
                if (attempt > 1) pendingLock.withLock { late[packet.command] = (late[packet.command] ?: 0) + attempt - 1 }
                return answer
            }
            pendingLock.withLock { pending[packet.command]?.remove(waiter) }
        }
        log("no answer to $packet")
        null
    }

    private suspend fun send(packet: Packet) = sending.withLock {
        val link = connection ?: return
        withContext(Dispatchers.IO) { link.write(PacketCodec.encode(packet)) }
    }

    private suspend fun failAll() = pendingLock.withLock {
        pending.values.forEach { queue -> queue.forEach { it.cancel() } }
        pending.clear()
        late.clear()
    }

    private companion object {
        val DROP = CompletableDeferred<Packet>()
        val SOUND_SETTLE = 3.seconds
        const val POLL = 3_000L

        /** A drop this soon after a restarting switch is that restart. */
        private const val RESTART_WINDOW = 20_000L

        /** How long to leave the earbuds alone after such a drop: back in about 7 s, music settled a few after. */
        private const val RESTART_HOLD = 12_000L
    }

    /** The devices the earbuds remember, which can come over several messages. */
    private suspend fun loadHosts() {
        val list = Channel<Packet>(Channel.UNLIMITED)
        deviceList = list
        try {
            send(Packet(A3947.DEVICES))
            val found = mutableListOf<Host>()
            repeat(20) {
                val part = withTimeoutOrNull(2_000L) { list.receive() } ?: return@repeat
                val body = part.body
                if (body.size < 2) return@repeat
                if (body[1].toInt() == 1) found.clear()
                found += A3947.hosts(body)
                if (body[0] == body[1]) {
                    hosts = found.toList()
                    publish()
                    return
                }
            }
            hosts = found.toList()
            publish()
        } finally {
            deviceList = null
        }
    }

    override suspend fun refresh() {
        if (connection == null) {
            // Asked to try again: show that it is trying.
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        request(Packet(A3947.STATE))?.body?.takeIf(A3947::isState)?.let { raw = keepSound(it) }
        publish()
    }

    private fun keepSound(state: ByteArray): ByteArray {
        val (sound, at) = soundSet ?: return state
        if (at.elapsedNow() > SOUND_SETTLE) {
            soundSet = null
            return state
        }
        sound.copyInto(state, A3947.SOUND)
        return state
    }

    override suspend fun apply(change: Change) {
        val now = raw ?: return
        // Show it at once; the earbuds confirm or it goes back.
        flow.value = flow.value.with(change)
        val ok = runCatching { send(change, now) }.getOrDefault(false)
        if (!ok) refresh() else publish()
    }

    private suspend fun ack(command: Int, body: ByteArray): Boolean = request(Packet(command, body)) != null

    private suspend fun send(change: Change, now: ByteArray): Boolean = when (change) {
        is Change.Mode, is Change.Cancelling, is Change.TransparencyKind, is Change.Wind, is Change.Environment -> {
            val wanted = flow.value.sound ?: return false
            val current = now.copyOfRange(A3947.SOUND, A3947.SOUND + 7)
            val steps = A3947.soundSteps(current, A3947.soundTarget(current, wanted))
            steps.all { step ->
                ack(A3947.SOUND_SET, step).also {
                    if (it) {
                        step.copyInto(now, A3947.SOUND)
                        soundSet = step to TimeSource.Monotonic.markNow()
                    }
                }
            }
        }
        is Change.ModeCycle -> {
            val byte = A3947.cycleByte(now[A3947.CYCLE].toInt() and 0xFF, change.modes)
            ack(A3947.CYCLE_SET, byteArrayOf(byte.toByte())).also { if (it) now[A3947.CYCLE] = byte.toByte() }
        }
        is Change.Preset -> {
            val tenths = A3947.PRESETS.getOrNull(change.id)?.second ?: return false
            equalizer(now, change.id, tenths)
        }
        is Change.Bands -> equalizer(now, com.blazify.buds.core.model.Equalizer.CUSTOM, A3947.customTenths(now, change.left))
        is Change.Switch -> if (change.toggle == Toggle.Ldac) {
            ack(A3947.LDAC_SET, byteArrayOf(if (change.on) 1 else 0)).also {
                if (it) {
                    ldac = change.on
                    restartedAt = kotlin.time.TimeSource.Monotonic.markNow()
                    // Turning it on also turns two devices off in the earbuds; read back what they now have.
                    delay(1_500)
                    refresh()
                }
            }
        } else {
            val (at, command) = A3947.TOGGLES[change.toggle] ?: return false
            val value: Byte = if (change.on) 1 else 0
            ack(command, byteArrayOf(value)).also { if (it) now[at] = value }
        }
        is Change.Touch -> A3947.touchBodies(now, change.ear, change.press, change.action).all { packet ->
            ack(packet.command, packet.body).also {
                if (it) {
                    val at = A3947.buttonOffset(change.ear, change.press)
                    if (packet.command == A3947.BUTTON_SET) now[at + 1] = packet.body[2] else now[at] = packet.body[2]
                }
            }
        }
        is Change.PowerOff -> {
            val index = A3947.POWER_OFF_MINUTES.indexOf(change.minutes).coerceAtLeast(0)
            val body = byteArrayOf(if (change.enabled) 1 else 0, index.toByte())
            ack(A3947.POWER_OFF_SET, body).also { if (it) body.copyInto(now, A3947.POWER_OFF) }
        }
        is Change.TwoDevices -> ack(A3947.TWO_DEVICES_SET, byteArrayOf(if (change.on) 1 else 0)).also {
            if (it) {
                now[A3947.TWO_DEVICES] = if (change.on) 1 else 0
                restartedAt = kotlin.time.TimeSource.Monotonic.markNow()
                if (change.on) {
                    ldac = false
                    loadHosts()
                }
            }
        }
        is Change.HostConnection -> {
            send(Packet(if (change.connect) A3947.CONNECT else A3947.DISCONNECT, A3947.macBytes(change.address)))
            delay(2_500)
            loadHosts()
            true
        }
        is Change.Forget -> ack(A3947.FORGET, A3947.macBytes(change.address)).also { if (it) loadHosts() }
        is Change.SafeVolume -> {
            // The earbuds take the cap in steps of 5 dB, from 75 to 100.
            val db = (change.decibels / 5 * 5).coerceIn(75, 100)
            val body = byteArrayOf(if (change.enabled) 1 else 0, db.toByte())
            ack(A3947.LIMIT_SET, body).also { if (it) body.copyInto(now, A3947.LIMIT) }
        }
        is Change.EarPrintTry -> ack(A3947.EQ_SET, A3947.earPrintBody(now, change.left, change.right, trying = true, madeAt = 0))
        is Change.EarPrintSave -> ack(A3947.EQ_SET, A3947.earPrintBody(now, change.left, change.right, trying = false, madeAt = change.madeAt)).also {
            if (it) keepEarPrint(now, change.left, change.right, change.madeAt, on = true)
        }
        is Change.EarPrintOn -> {
            val saved = A3947.earPrint(now)
            if (change.on) {
                ack(A3947.EQ_SET, A3947.earPrintBody(now, saved.left, saved.right, trying = false, madeAt = saved.madeAt)).also { if (it) now[A3947.HEAR_ID] = 1 }
            } else {
                // The usual sound message switches the profile off and keeps it on the earbuds for later.
                val tenths = IntArray(10) { if (now[A3947.EQ_LEFT + it].toInt() and 0xFF == 0xFF) 0 else (now[A3947.EQ_LEFT + it].toInt() and 0xFF) - 120 }
                val id = (now[A3947.PRESET].toInt() and 0xFF) or ((now[A3947.PRESET + 1].toInt() and 0xFF) shl 8)
                ack(A3947.EQ_SET, A3947.equalizerBody(now, if (id == 0xFEFE) com.blazify.buds.core.model.Equalizer.CUSTOM else id, tenths)).also { if (it) now[A3947.HEAR_ID] = 0 }
            }
        }
        is Change.HearingTestMode -> ack(A3947.HEARING_TEST, byteArrayOf(if (change.on) 1 else 0))
        // The Liberty 4 NC's transparency has no level.
        is Change.AmbientLevel -> false
        Change.ResetControls -> ack(A3947.BUTTON_RESET, ByteArray(0)).also { if (it) refresh() }
    }

    private fun keepEarPrint(now: ByteArray, left: List<Double>, right: List<Double>, madeAt: Long, on: Boolean) {
        now[A3947.HEAR_ID] = if (on) 1 else 0
        for (i in 0 until 10) {
            now[A3947.HEAR_ID + 1 + i] = (if (i < 8) kotlin.math.round(left[i] * 10).toInt() + 120 else 0).toByte()
            now[A3947.HEAR_ID + 11 + i] = (if (i < 8) kotlin.math.round(right[i] * 10).toInt() + 120 else 0).toByte()
        }
        now[81] = (madeAt shr 24).toByte(); now[82] = (madeAt shr 16).toByte(); now[83] = (madeAt shr 8).toByte(); now[84] = madeAt.toByte()
    }

    private suspend fun equalizer(now: ByteArray, presetId: Int, tenths: IntArray): Boolean {
        // The earbuds take sound changes only with both of them out.
        if (now[A3947.BOTH].toInt() != 1) return false
        val ok = ack(A3947.EQ_SET, A3947.equalizerBody(now, presetId, tenths))
        if (ok) {
            val id = if (presetId == com.blazify.buds.core.model.Equalizer.CUSTOM) 0xFEFE else presetId
            now[A3947.PRESET] = id.toByte()
            now[A3947.PRESET + 1] = (id shr 8).toByte()
            for (i in 0 until 10) now[A3947.EQ_LEFT + i] = (tenths[i].coerceIn(-120, 134) + 120).toByte()
        }
        return ok
    }

    override suspend fun ring(left: Boolean, right: Boolean) {
        send(Packet(A3947.RING, byteArrayOf(if (left) 1 else 0, if (right) 1 else 0, 0)))
    }

    override suspend fun fitTest(): FitResult? {
        val answers = Channel<Packet>(Channel.UNLIMITED)
        fitAnswers = answers
        try {
            // As Soundcore's own app does: the bare command. The earbuds switch to Normal, play a tone for about ten
            // seconds, switch back, and answer with a byte for each side: 0 a good seal, anything else a poor one.
            send(Packet(A3947.FIT_TEST))
            repeat(4) {
                val answer = withTimeoutOrNull(16_000L) { answers.receive() } ?: return null
                if (answer.body.size >= 2) {
                    fun seal(byte: Byte): Boolean = byte.toInt() == 0
                    return FitResult(seal(answer.body[0]), seal(answer.body[1]))
                }
            }
            return null
        } finally {
            fitAnswers = null
        }
    }

    @kotlin.concurrent.Volatile
    private var watching = true

    override fun watch(on: Boolean) {
        watching = on
        if (on) wake.trySend(Unit)
    }

    /** Try to connect again now, not at the next scheduled try. */
    fun retry() {
        wake.trySend(Unit)
    }

    override fun close() {
        val job = runner
        runner = null
        connection?.close()
        scope.launch { job?.cancelAndJoin() }
    }
}
