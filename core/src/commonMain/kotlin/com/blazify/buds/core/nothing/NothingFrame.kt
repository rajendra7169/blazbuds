package com.blazify.buds.core.nothing

/**
 * One message in the protocol Nothing and CMF earbuds and headphones speak on their own serial channel: `55`, a
 * control word, a command, the payload's length, a running number, the payload, then a CRC-16 (MODBUS). Every
 * two-byte number is low byte first.
 *
 * The control word's low five bits are the answer's result (0 done, 2 not activated, 3 not supported…) and 0x20
 * says a CRC follows. Questions are C0xx, changes F0xx, and the answer to either carries the same command with the
 * top bit cleared (`C007` is answered by `4007`); news the earbuds send by themselves are E0xx.
 *
 * The frame layout follows nada by chukfinley and nadamas by ezvk (a fork of SoaOaoS's something-x), all MIT.
 */
class NothingFrame(val control: Int, val command: Int, val payload: ByteArray, val number: Int = 0) {
    /** The command this frame is about, with the top bit set, so an answer and its question match. */
    val id: Int get() = command or 0x8000

    /** Whether this is an answer to something asked, rather than news. */
    val isAnswer: Boolean get() = command and 0x8000 == 0

    /** The result an answer carries: [OK], [NOT_ACTIVATED], [UNSUPPORTED]… */
    val result: Int get() = control and 0x1F

    val ok: Boolean get() = result == OK

    override fun toString(): String = "Nothing(" + hex4(command) + " #" + number + " r" + result + ": " + hex(payload) + ")"

    companion object {
        const val OK = 0
        const val NOT_ACTIVATED = 2
        const val UNSUPPORTED = 3

        /** What the phone app sends: a CRC follows, more than one frame may make up a message, and it's a headset. */
        const val CONTROL = 0x0160
        private const val HAS_CRC = 0x20
        private const val MAX_PAYLOAD = 4096

        /** A whole frame for [command] with [payload], numbered [number], with its CRC. */
        fun encode(command: Int, payload: ByteArray = ByteArray(0), number: Int = 0): ByteArray {
            val head = byteArrayOf(
                0x55,
                CONTROL.toByte(), (CONTROL shr 8).toByte(),
                command.toByte(), (command shr 8).toByte(),
                payload.size.toByte(), (payload.size shr 8).toByte(),
                number.toByte(),
            ) + payload
            val crc = crc16(head, head.size)
            return head + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
        }

        /** CRC-16/MODBUS: the reflected polynomial 0xA001, starting at 0xFFFF. */
        fun crc16(data: ByteArray, count: Int = data.size): Int {
            var crc = 0xFFFF
            for (i in 0 until count) {
                crc = crc xor (data[i].toInt() and 0xFF)
                repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
            }
            return crc and 0xFFFF
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { b ->
            val v = b.toInt() and 0xFF
            "0123456789abcdef"[v shr 4].toString() + "0123456789abcdef"[v and 0xF]
        }

        fun hex4(n: Int): String = hex(byteArrayOf((n shr 8).toByte(), n.toByte())).replace(" ", "")
    }

    /**
     * Collects bytes as they arrive and hands back whole frames. A frame whose CRC is wrong is dropped; the Ear (1)
     * answers without one, which its control word says.
     */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, count: Int = bytes.size): List<NothingFrame> {
            buffer += bytes.copyOf(count)
            val out = mutableListOf<NothingFrame>()
            var at = 0
            while (at < buffer.size) {
                if (buffer[at] != 0x55.toByte()) { at++; continue }
                if (buffer.size - at < 8) break
                val control = u8(at + 1) or (u8(at + 2) shl 8)
                val length = u8(at + 5) or (u8(at + 6) shl 8)
                if (length > MAX_PAYLOAD) { at++; continue }
                val crcSize = if (control and HAS_CRC != 0) 2 else 0
                val total = 8 + length + crcSize
                if (buffer.size - at < total) break
                if (crcSize == 2) {
                    val sent = u8(at + total - 2) or (u8(at + total - 1) shl 8)
                    if (crc16(buffer.copyOfRange(at, at + total - 2)) != sent) { at++; continue }
                }
                out += NothingFrame(
                    control = control,
                    command = u8(at + 3) or (u8(at + 4) shl 8),
                    payload = buffer.copyOfRange(at + 8, at + 8 + length),
                    number = u8(at + 7),
                )
                at += total
            }
            buffer = buffer.copyOfRange(at, buffer.size)
            return out
        }

        private fun u8(i: Int) = buffer[i].toInt() and 0xFF
    }
}
