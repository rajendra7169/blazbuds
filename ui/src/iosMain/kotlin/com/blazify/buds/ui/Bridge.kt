package com.blazify.buds.ui

import com.blazify.buds.core.Logbook
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.concurrent.Volatile

/*
 * What the Swift side of the iPhone app does, and what it hands back. CoreBluetooth and ExternalAccessory are driven
 * from Swift; these few names are all it sees of the Kotlin code. They take only strings, booleans, bytes (NSData)
 * and closures, so none of Kotlin's own types (enums, data classes) cross over and get renamed on the way.
 */

/** The iPhone's Bluetooth, written in Swift. */
interface BluetoothBridge {
    /** False only when Bluetooth is surely off or not allowed; true while the iPhone hasn't said yet. */
    fun isOn(): Boolean

    /**
     * The earbuds connected now that the app can talk to, one line each: id, name and route, split by tabs. The id is
     * the iPhone's own for the peripheral, or "ea:" and a number for an accessory session. The route says which
     * service answered: soundcore, nothing, melody, jieli, honor, sony, fastpair, or ea for an accessory.
     */
    fun connectedEarbuds(): List<String>

    /**
     * Opens the control link to [id]. [route] can name the way in (an accessory's protocol); empty lets Swift choose.
     * Swift tells [pipe] once: [BudsPipe.opened] or [BudsPipe.failed].
     */
    fun connect(id: String, route: String, pipe: BudsPipe)

    /** Looks over everything iOS reports about Bluetooth and earbuds, then hands back the text. */
    fun check(done: (String) -> Unit)

    /** Calls [changed] when Bluetooth turns on or off or a pair of earbuds comes or goes. */
    fun watch(changed: () -> Unit)
}

/** The sending half of a link, written in Swift. */
interface BudsWire {
    fun send(data: NSData)

    fun close()
}

/**
 * One link to a pair of earbuds, as the drivers see it: Swift puts in what the earbuds send, and a driver reads it out
 * on its own thread, blocking as it does on a socket elsewhere.
 */
class BudsPipe : Connection {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()

    @Volatile
    private var wire: BudsWire? = null

    /** Part of a message not read yet. */
    private var rest: ByteArray? = null
    private var at = 0

    /** Swift: the link is open and [wire] sends on it. */
    fun opened(wire: BudsWire) {
        this.wire = wire
        ready.complete(Unit)
    }

    /** Swift: the link couldn't be opened. */
    fun failed(reason: String, busy: Boolean) {
        ready.completeExceptionally(LinkException(reason, busy))
        incoming.close()
    }

    /** Swift: the earbuds sent these bytes. */
    fun received(data: NSData) {
        incoming.trySend(data.toBytes())
    }

    /** Swift: the link is gone. */
    fun ended() {
        incoming.close()
    }

    internal suspend fun ready() = ready.await()

    override fun read(into: ByteArray): Int {
        val chunk = rest ?: runBlocking { incoming.receiveCatching().getOrNull() } ?: return -1
        val count = minOf(into.size, chunk.size - at)
        chunk.copyInto(into, 0, at, at + count)
        at += count
        if (at >= chunk.size) {
            rest = null
            at = 0
        } else {
            rest = chunk
        }
        return count
    }

    override fun write(bytes: ByteArray) {
        val out = wire ?: throw LinkException("The earbuds stopped listening")
        out.send(bytes.toData())
    }

    override fun close() {
        incoming.close()
        wire?.close()
        wire = null
    }
}

/** Lets Swift write in the app's own log, which Help › Report a problem copies. */
object BudsLog {
    fun add(line: String) = Logbook.add(line)
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
