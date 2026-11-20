package com.blazify.buds.core.jieli

/**
 * One message in JieLi's RCSP protocol: `FE DC BA`, a kind (a command wanting an answer, an answer, or news the
 * earbuds send unasked), what it's about, a two-byte length, the body, and `EF`. A command's body starts with a
 * running number; its answer starts with a status (0 is fine) and that number.
 */
data class Rcsp(val kind: Int, val opcode: Int, val body: ByteArray) {
    val isAnswer get() = kind and 0x80 == 0 && kind and 0x40 == 0
    val isNews get() = kind == NEWS

    override fun toString(): String = "Rcsp(%02x %02x, %s)".format(kind, opcode, body.joinToString(" ") { "%02x".format(it) })

    companion object {
        const val COMMAND = 0xC0
        const val NEWS = 0x80

        fun encode(packet: Rcsp): ByteArray =
            byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), packet.kind.toByte(), packet.opcode.toByte(), (packet.body.size shr 8).toByte(), packet.body.size.toByte()) +
                packet.body + byteArrayOf(0xEF.toByte())
    }

    /** Collects bytes as they arrive and hands back whole messages; anything else is skipped. */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, count: Int): List<Rcsp> {
            buffer += bytes.copyOf(count)
            val out = mutableListOf<Rcsp>()
            while (true) {
                var start = -1
                for (i in 0..buffer.size - 3) {
                    if (buffer[i] == 0xFE.toByte() && buffer[i + 1] == 0xDC.toByte() && buffer[i + 2] == 0xBA.toByte()) { start = i; break }
                }
                if (start < 0) { buffer = buffer.copyOfRange(maxOf(0, buffer.size - 2), buffer.size); break }
                if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
                if (buffer.size < 8) break
                val length = ((buffer[5].toInt() and 0xFF) shl 8) or (buffer[6].toInt() and 0xFF)
                val total = 7 + length + 1
                if (buffer.size < total) break
                if (buffer[total - 1] != 0xEF.toByte()) { buffer = buffer.copyOfRange(3, buffer.size); continue }
                out += Rcsp(buffer[3].toInt() and 0xFF, buffer[4].toInt() and 0xFF, buffer.copyOfRange(7, total - 1))
                buffer = buffer.copyOfRange(total, buffer.size)
            }
            return out
        }
    }
}
