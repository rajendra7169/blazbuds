package com.blazify.buds.core.huawei

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
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
 * case in whole percent, which is charging, and the model, firmware and serial. They talk on a plain serial-port
 * channel; some have a second one that speaks something else, so each way in [opens] is tried until one answers in
 * this protocol.
 */
class HuaweiBuds(
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
    private val answers = Channel<HuaweiPacket>(Channel.UNLIMITED)
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
        if (packet.failed) return
        when (packet.id) {
            (BATTERY_SERVICE shl 8) or BATTERY, (BATTERY_SERVICE shl 8) or BATTERY_PUSH -> {
                log("<- $packet")
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
            (INFO_SERVICE shl 8) or INFO -> {
                log("<- $packet")
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

    override suspend fun apply(change: Change) {}

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

        /** The plain serial-port service these earbuds talk on. */
        const val SERIAL = "00001101-0000-1000-8000-00805f9b34fb"
    }
}
