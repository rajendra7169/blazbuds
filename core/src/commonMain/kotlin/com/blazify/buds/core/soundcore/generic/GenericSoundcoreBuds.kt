package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.demo.with
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.PacketCodec
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
 * Any Soundcore model besides the Liberty 4 NC, live, driven by its [SoundcoreModel] description. It works like the
 * Liberty 4 NC's driver: it opens the control channel through [open], asks for everything, keeps in step with what the
 * earbuds push, asks again every few seconds while someone is looking, shows a change at once and goes back if the
 * earbuds don't confirm it, and tries again by itself when the link drops.
 */
class GenericSoundcoreBuds(
    private val name: String,
    val model: SoundcoreModel,
    private val scope: CoroutineScope,
    private val open: suspend () -> Connection,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, model.code, Link.Connecting))
    override val state: StateFlow<BudsState> = flow

    private var connection: Connection? = null
    private var parsed: Parsed? = null
    private var hosts: List<Host> = emptyList()
    private val pending = mutableMapOf<Int, ArrayDeque<CompletableDeferred<Packet>>>()

    /** Answers still on their way for messages sent more than once; see the Liberty 4 NC's driver. */
    private val late = mutableMapOf<Int, Int>()
    private val pendingLock = Mutex()
    private val sending = Mutex()
    private var deviceList: Channel<Packet>? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

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
                failAll()
                flow.update { it.copy(link = if (busy) Link.Busy else Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    private fun read(body: ByteArray): Parsed? = try {
        model.parse(body)
    } catch (e: Exception) {
        log("state doesn't fit ${model.code}: ${e.message}")
        null
    }

    private suspend fun session() {
        val link = withContext(Dispatchers.IO) { open() }
        connection = link
        val reading = scope.launch(Dispatchers.IO) { readLoop(link) }
        try {
            val answer = request(Packet(SoundcoreModel.STATE)) ?: throw LinkException("The earbuds didn't answer")
            val first = read(answer.body) ?: throw LinkException("These don't look like ${model.name} earbuds")
            parsed = first
            if (model.askFirmware) request(Packet(SoundcoreModel.FIRMWARE))?.let { readFirmware(first, it.body) }
            if (model.askLdac) request(Packet(SoundcoreModel.LDAC))?.body?.firstOrNull()?.let { first.switches[Toggle.Ldac] = it.toInt() == 1 }
            publish()
            if (model.twoDevices && first.twoDevices == true) loadHosts()
            val polling = scope.launch {
                while (true) {
                    delay(POLL)
                    if (watching && !sending.isLocked) refreshState()
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

    /** Asks for everything again and keeps what only came from other answers. */
    private suspend fun refreshState() {
        val body = request(Packet(SoundcoreModel.STATE))?.body ?: return
        val fresh = read(body) ?: return
        parsed?.let { old ->
            if (fresh.firmwareLeft == null && fresh.firmwareRight == null) {
                fresh.firmwareLeft = old.firmwareLeft
                fresh.firmwareRight = old.firmwareRight
            }
            if (fresh.serial == null) fresh.serial = old.serial
            if (model.askLdac) old.switches[Toggle.Ldac]?.let { fresh.switches[Toggle.Ldac] = it }
        }
        parsed = fresh
        publish()
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
        if (packet.command == SoundcoreModel.DEVICES) deviceList?.let { it.trySend(packet); return }
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
        val now = parsed ?: return
        if (packet.command == SoundcoreModel.STATE) {
            read(packet.body)?.let { parsed = it }
        } else if (!model.pushed(now, packet)) {
            log("unhandled $packet")
            return
        }
        publish()
    }

    private fun publish() {
        val now = parsed ?: return
        flow.value = model.state(now, name, hosts)
    }

    private suspend fun request(packet: Packet): Packet? = sending.withLock {
        val link = connection ?: return null
        val bytes = PacketCodec.encode(packet)
        val first = if (packet.command == SoundcoreModel.STATE) 1_500L else 500L
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

    /** The devices the earbuds remember, which can come over several messages. */
    private suspend fun loadHosts() {
        val list = Channel<Packet>(Channel.UNLIMITED)
        deviceList = list
        try {
            send(Packet(SoundcoreModel.DEVICES))
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
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        refreshState()
    }

    override suspend fun apply(change: Change) {
        val now = parsed ?: return
        val before = flow.value
        val wanted = before.with(change)
        flow.value = wanted
        val ok = runCatching { send(change, now, wanted) }.getOrDefault(false)
        if (!ok) refresh() else publish()
    }

    private suspend fun send(change: Change, now: Parsed, wanted: BudsState): Boolean {
        when (change) {
            is Change.HostConnection -> {
                if (!model.twoDevices) return false
                send(Packet(if (change.connect) CONNECT else DISCONNECT, A3947.macBytes(change.address)))
                delay(2_500)
                loadHosts()
                return true
            }
            is Change.Forget -> {
                if (!model.twoDevices) return false
                return (request(Packet(FORGET, A3947.macBytes(change.address))) != null).also { if (it) loadHosts() }
            }
            else -> {}
        }
        val steps = model.steps(now, change, wanted) ?: return false
        for (step in steps) {
            if (request(step.packet) == null) return false
            step.apply(parsed ?: now)
        }
        when (change) {
            Change.ResetControls -> refreshState()
            is Change.TwoDevices -> if (change.on) loadHosts()
            is Change.Switch -> if (change.toggle == Toggle.Ldac) {
                // LDAC and two devices usually exclude each other; read back what the earbuds now have.
                delay(1_500)
                refreshState()
            }
            else -> {}
        }
        return true
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

    private companion object {
        val DROP = CompletableDeferred<Packet>()
        const val POLL = 3_000L
        val DISCONNECT = Packet.command(0x0B, 0x81)
        val CONNECT = Packet.command(0x0B, 0x82)
        val FORGET = Packet.command(0x0B, 0x83)
    }
}
