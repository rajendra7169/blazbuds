package com.blazify.buds.core.sony

/**
 * One message on Sony's control channel: `3E`, its kind, a sequence number (0 or 1), the payload's length in four
 * bytes, the payload, a checksum, and `3C`. The checksum is the low byte of the sum of everything from the kind to the
 * payload's end. Between the two ends, `3C`, `3D` and `3E` are sent as `3D` and the byte with bit 4 cleared.
 *
 * Every message of kind [TABLE1] or [TABLE2] is answered at once with an empty [ACK] carrying the other sequence
 * number, and the sender takes that number for its next message. Adapted from libmdr's `Command.cpp` (MIT).
 */
class SonyFrame(val kind: Int, val seq: Int, val payload: ByteArray) {
    val isAck: Boolean get() = kind == ACK

    override fun toString(): String = "${hex(byteArrayOf(kind.toByte()))} #$seq ${hex(payload)}"

    companion object {
        /** An acknowledgement. */
        const val ACK = 0x01

        /** A message of the first command table, which holds nearly everything. */
        const val TABLE1 = 0x0C

        /** A message of the second table (voice guidance and other extras). */
        const val TABLE2 = 0x0E

        private const val START = 0x3E
        private const val END = 0x3C
        private const val ESCAPE = 0x3D
        private const val ESCAPE_MASK = 0xEF

        fun encode(kind: Int, seq: Int, payload: ByteArray): ByteArray {
            val body = ByteArray(6 + payload.size)
            body[0] = kind.toByte()
            body[1] = seq.toByte()
            body[2] = (payload.size ushr 24).toByte()
            body[3] = (payload.size ushr 16).toByte()
            body[4] = (payload.size ushr 8).toByte()
            body[5] = payload.size.toByte()
            payload.copyInto(body, 6)
            val out = ArrayList<Byte>(body.size + 8)
            out += START.toByte()
            for (b in body + checksum(body).toByte()) {
                val v = b.toInt() and 0xFF
                if (v == START || v == END || v == ESCAPE) {
                    out += ESCAPE.toByte()
                    out += (v and ESCAPE_MASK).toByte()
                } else {
                    out += b
                }
            }
            out += END.toByte()
            return out.toByteArray()
        }

        /** The answer to a message that came with sequence number [received]. */
        fun ack(received: Int): ByteArray = encode(ACK, 1 - received, ByteArray(0))

        fun checksum(bytes: ByteArray): Int = bytes.fold(0) { sum, b -> sum + (b.toInt() and 0xFF) } and 0xFF

        /** A whole frame, `3E` to `3C`; null when it doesn't add up. */
        fun decode(frame: ByteArray): SonyFrame? {
            if (frame.size < 2 || frame.first().toInt() != START || frame.last().toInt() != END) return null
            val plain = ArrayList<Byte>(frame.size)
            var i = 1
            while (i < frame.size - 1) {
                val v = frame[i].toInt() and 0xFF
                if (v == ESCAPE) {
                    if (i + 1 >= frame.size - 1) return null
                    plain += ((frame[i + 1].toInt() and 0xFF) or 0x10).toByte()
                    i += 2
                } else {
                    plain += frame[i]
                    i++
                }
            }
            if (plain.size < 7) return null
            val bytes = plain.toByteArray()
            val body = bytes.copyOfRange(0, bytes.size - 1)
            if (checksum(body) != (bytes.last().toInt() and 0xFF)) return null
            val length = ((bytes[2].toInt() and 0xFF) shl 24) or ((bytes[3].toInt() and 0xFF) shl 16) or
                ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
            if (length != body.size - 6) return null
            return SonyFrame(bytes[0].toInt() and 0xFF, bytes[1].toInt() and 0xFF, body.copyOfRange(6, body.size))
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** Collects bytes as they arrive and hands back whole frames; broken ones are dropped. */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, count: Int): List<SonyFrame> {
            buffer += bytes.copyOf(count)
            val out = mutableListOf<SonyFrame>()
            while (true) {
                val start = buffer.indexOf(START.toByte())
                if (start < 0) { buffer = ByteArray(0); break }
                val end = (start + 1 until buffer.size).firstOrNull { buffer[it] == END.toByte() } ?: run {
                    buffer = buffer.copyOfRange(start, buffer.size)
                    return out
                }
                decode(buffer.copyOfRange(start, end + 1))?.let { out += it }
                buffer = buffer.copyOfRange(end + 1, buffer.size)
            }
            return out
        }
    }
}
