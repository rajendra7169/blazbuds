package com.blazify.buds.core.link

/**
 * A byte pipe to one pair of earbuds over Bluetooth, opened by the platform: Linux sockets, Android's Bluetooth,
 * Windows sockets, or the iPhone's accessory session.
 */
interface Connection {
    /** Blocks until some bytes arrive; returns how many, or -1 once the link is gone. */
    fun read(into: ByteArray): Int

    fun write(bytes: ByteArray)

    fun close()
}

/** A pair of earbuds the computer or phone knows about. */
data class Device(val name: String, val address: String, val connected: Boolean)

class LinkException(message: String, val busy: Boolean = false) : Exception(message)
