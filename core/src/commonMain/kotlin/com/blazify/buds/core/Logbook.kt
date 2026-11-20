package com.blazify.buds.core

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/**
 * The last few hundred things the app and the earbuds said to each other, kept only in memory, for "Report a problem".
 * Nothing leaves the device unless the person copies it themselves.
 */
@OptIn(ExperimentalAtomicApi::class)
object Logbook {
    private const val KEEP = 400
    private val start = TimeSource.Monotonic.markNow()
    private val lines = ArrayDeque<String>()

    // Written from the earbuds' reader threads and read from the screen; a tiny spin guard suits such short work.
    private val busy = AtomicBoolean(false)

    fun add(line: String) {
        val tenths = start.elapsedNow().inWholeMilliseconds / 100
        val entry = "+${tenths / 10}.${tenths % 10}s ${hideAddresses(line)}"
        guarded {
            lines.addLast(entry)
            while (lines.size > KEEP) lines.removeFirst()
        }
    }

    /** Device addresses written out (AA:BB:CC:DD:EE:FF) keep only their last two pairs: enough to tell them apart. */
    fun hideAddresses(line: String): String =
        ADDRESS.replace(line) { m -> "XX:XX:XX:XX:" + m.value.takeLast(5) }

    private val ADDRESS = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")

    /** Everything kept, oldest first. */
    fun all(): List<String> = guarded { lines.toList() }

    private fun <T> guarded(block: () -> T): T {
        while (!busy.compareAndSet(false, true)) Unit
        try {
            return block()
        } finally {
            busy.store(false)
        }
    }
}
