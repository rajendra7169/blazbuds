package com.blazify.buds.core.link

/** Bluetooth on a computer: the same few calls on Linux and Windows, so the app picks one and never asks which. */
interface DesktopBluetooth {
    /** Opens the earbuds' service [uuid], finding its channel first. */
    fun open(address: String, uuid: String, timeoutMs: Int = 6000): Connection

    /** Opens a fixed RFCOMM [channel], for earbuds whose control port is always the same. */
    fun rfcomm(address: String, channel: Int, timeoutMs: Int = 6000): Connection

    /** Pairs this computer knows, and which are connected now. */
    fun devices(): List<Device>

    /** Whether a device is connected, and its battery when the system reads one (else null). */
    fun info(address: String): Pair<Boolean, Int?>

    /** Whether the computer has a Bluetooth radio that is on, where the system can tell. */
    fun radioOn(): Boolean = true
}
