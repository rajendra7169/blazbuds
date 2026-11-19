package com.blazify.buds.core.soundcore

/**
 * One message between the app and Soundcore earbuds.
 *
 * On the wire every message is: a 5-byte direction marker, a 2-byte command, a 2-byte little-endian length counting
 * the whole message, the body, and a one-byte sum of everything before it.
 */
class Packet(val command: Int, val body: ByteArray = ByteArray(0)) {
    constructor(first: Int, second: Int, body: ByteArray = ByteArray(0)) : this(command(first, second), body)

    override fun toString(): String = "Packet(${commandName(command)}, ${body.toHex()})"

    companion object {
        fun command(first: Int, second: Int): Int = ((first and 0xFF) shl 8) or (second and 0xFF)

        fun commandName(command: Int): String = "%02x %02x".format(command shr 8, command and 0xFF)
    }
}

/** Turns packets into bytes for the earbuds, and bytes from the earbuds back into packets. */
object PacketCodec {
    private val TO_EARBUDS = byteArrayOf(0x08, 0xEE.toByte(), 0x00, 0x00, 0x00)
    private val FROM_EARBUDS = byteArrayOf(0x09, 0xFF.toByte(), 0x00, 0x00, 0x01)
    private const val HEADER = 5 + 2 + 2

    fun encode(packet: Packet): ByteArray = frame(TO_EARBUDS, packet)

    /** What the earbuds would send: used by the demo earbuds and the tests. */
    fun encodeFromEarbuds(packet: Packet): ByteArray = frame(FROM_EARBUDS, packet)

    private fun frame(marker: ByteArray, packet: Packet): ByteArray {
        val length = HEADER + packet.body.size + 1
        val out = ByteArray(length)
        marker.copyInto(out)
        out[5] = (packet.command shr 8).toByte()
        out[6] = packet.command.toByte()
        out[7] = length.toByte()
        out[8] = (length shr 8).toByte()
        packet.body.copyInto(out, HEADER)
        out[length - 1] = checksum(out, length - 1)
        return out
    }

    fun checksum(bytes: ByteArray, count: Int): Byte {
        var sum = 0
        for (i in 0 until count) sum += bytes[i].toInt() and 0xFF
        return sum.toByte()
    }

    /**
     * Collects bytes as they arrive, however the radio splits them, and hands back each whole packet from the
     * earbuds. Bytes that cannot start a packet, or a packet whose sum is wrong, are skipped one byte at a time, so a
     * single bad byte never loses the packets after it.
     */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, count: Int = bytes.size): List<Packet> {
            buffer += bytes.copyOf(count)
            val packets = mutableListOf<Packet>()
            var start = 0
            while (buffer.size - start >= HEADER + 1) {
                if (!startsWithMarker(start)) {
                    start++
                    continue
                }
                val length = (buffer[start + 7].toInt() and 0xFF) or ((buffer[start + 8].toInt() and 0xFF) shl 8)
                if (length < HEADER + 1 || length > 4096) {
                    start++
                    continue
                }
                if (buffer.size - start < length) break
                val whole = buffer.copyOfRange(start, start + length)
                if (checksum(whole, length - 1) != whole[length - 1]) {
                    start++
                    continue
                }
                val command = Packet.command(whole[5].toInt(), whole[6].toInt())
                packets += Packet(command, whole.copyOfRange(HEADER, length - 1))
                start += length
            }
            buffer = buffer.copyOfRange(start, buffer.size)
            return packets
        }

        private fun startsWithMarker(at: Int): Boolean = FROM_EARBUDS.indices.all { buffer[at + it] == FROM_EARBUDS[it] }
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

fun hex(text: String): ByteArray =
    text.filter { !it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
