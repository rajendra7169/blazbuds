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
        val entry = "+${tenths / 10}.${tenths % 10}s ${hideText(hideAddresses(line))}"
        guarded {
            lines.addLast(entry)
            while (lines.size > KEEP) lines.removeFirst()
        }
    }

    /** Device addresses written out (AA:BB:CC:DD:EE:FF) keep only their last two pairs: enough to tell them apart. */
    fun hideAddresses(line: String): String =
        ADDRESS.replace(line) { m -> "XX:XX:XX:XX:" + m.value.takeLast(5) }

    /**
     * Words written out as bytes are left out. The earbuds' replies carry the names of the phones and computers they
     * remember ("Someone's iPhone") and their serial number, and a log pasted into a public report must not. Any run
     * of four or more bytes that read as text becomes "…"; the bytes around it, which are what a fault is read from,
     * stay.
     */
    fun hideText(line: String): String = HEX_RUN.replace(line) { run ->
        val bytes = run.value.trim().split(' ')
        val out = ArrayList<String>(bytes.size)
        var i = 0
        while (i < bytes.size) {
            var j = i
            while (j < bytes.size && bytes[j].toInt(16) in 0x20..0x7e) j++
            if (j - i >= 4) {
                out += "…"
                i = j
            } else {
                out += bytes[i]
                i++
            }
        }
        out.joinToString(" ") + if (run.value.endsWith(" ")) " " else ""
    }

    private val HEX_RUN = Regex("(?<![0-9A-Za-z])(?:[0-9a-fA-F]{2} ){3,}[0-9a-fA-F]{2}(?![0-9A-Za-z])")

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
