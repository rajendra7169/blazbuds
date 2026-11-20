/**
 * OnePlus, OPPO and realme earbuds: the protocol their own apps (HeyMelody, realme Link) speak over a serial-port
 * channel.
 *
 * What these files know comes from other people's careful work, used under their licences:
 * - QuickBuds by spizganed (GPL-3.0, github.com/spizganed/QuickBuds): its PROTOCOL.md, its model list and its tests
 *   with packets recorded from real earbuds. The model table in MelodyModels.kt is written out from its list, and the
 *   frame reader follows its framer. Blazbuds is GPL-3.0 too.
 * - MacMelody by Omraj09 and BudsControl by amarsakore (both MIT): their notes and tests, used to check the facts.
 * - PairFree's notes on OnePlus earbuds and Gadgetbridge (AGPL-3.0): facts only, no code taken.
 */
package com.blazify.buds.core.oneplus

/**
 * One message: a command, the number the sender picked for it (the answer repeats it; FF is news from the earbuds),
 * and its payload. An answer has the top bit of the command set: 0x0106 is answered by 0x8106.
 */
class MelodyMessage(val command: Int, val seq: Int, val payload: ByteArray) {
    val isAnswer: Boolean get() = command and 0x8000 != 0

    /** The payload's byte at [i], 0-255, or -1 past the end. */
    fun at(i: Int): Int = if (i in payload.indices) payload[i].toInt() and 0xFF else -1

    override fun toString(): String = "Melody(" + hex4(command) + " #" + hex2(seq) + ": " + hex(payload) + ")"
}

/**
 * How messages travel: `AA`, a length (7 bits a byte, low first, the top bit saying more follow), a slice byte
 * (0 a whole message, 1, 2, 3 the first, middle and last slice of a long one), a zero, then for a whole message the
 * command (2 bytes, low first), the number, the payload length (2 bytes, low first) and the payload. No checksum.
 */
object MelodyFrame {
    fun encode(command: Int, seq: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val body = byteArrayOf(
            0, 0,
            command.toByte(), (command shr 8).toByte(),
            seq.toByte(),
            payload.size.toByte(), (payload.size shr 8).toByte(),
        ) + payload
        // The length counts everything after itself.
        val out = ArrayList<Byte>(body.size + 3)
        out += 0xAA.toByte()
        var length = body.size
        do {
            val low = length and 0x7F
            length = length ushr 7
            out += (if (length != 0) low or 0x80 else low).toByte()
        } while (length != 0)
        return out.toByteArray() + body
    }

    /** Collects bytes as they arrive and hands back whole messages, joining slices; anything else is skipped. */
    class Reader {
        private var buffer = ByteArray(0)
        private var slices: ByteArray? = null

        fun feed(bytes: ByteArray, count: Int = bytes.size): List<MelodyMessage> {
            buffer += bytes.copyOf(count)
            val out = mutableListOf<MelodyMessage>()
            while (buffer.isNotEmpty()) {
                val start = buffer.indexOf(0xAA.toByte())
                if (start < 0) {
                    buffer = ByteArray(0)
                    break
                }
                if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
                // The length: up to three bytes; three without an end can't be one, so this AA is noise.
                var length = 0
                var lengthBytes = 0
                for (i in 1 until minOf(buffer.size, 4)) {
                    val b = buffer[i].toInt() and 0xFF
                    length = length or ((b and 0x7F) shl (7 * (i - 1)))
                    if (b and 0x80 == 0) {
                        lengthBytes = i
                        break
                    }
                }
                if (lengthBytes == 0) {
                    if (buffer.size >= 4) {
                        buffer = buffer.copyOfRange(1, buffer.size)
                        continue
                    }
                    break
                }
                if (buffer.size < 2 + lengthBytes) break
                val slice = buffer[1 + lengthBytes].toInt()
                val sliced = slice in 1..3
                val total = 1 + lengthBytes + length
                if (length < (if (sliced) 4 else 7) || total > 4096) {
                    buffer = buffer.copyOfRange(1, buffer.size)
                    continue
                }
                if (buffer.size < total) break
                val frame = buffer.copyOfRange(0, total)
                buffer = buffer.copyOfRange(total, buffer.size)
                // After AA, the length, the slice byte and the zero: a slice has its counter next, then its part.
                val rest = frame.copyOfRange(1 + lengthBytes + 2, total)
                if (sliced) join(slice, rest.copyOfRange(1, rest.size))?.let { out += it } else message(rest)?.let { out += it }
            }
            return out
        }

        /** A slice's part; after the last one, the message they make. A middle or last part with no first is dropped. */
        private fun join(slice: Int, part: ByteArray): MelodyMessage? {
            slices = if (slice == 1) part else (slices ?: return null) + part
            if (slice != 3) return null
            val whole = slices ?: return null
            slices = null
            return message(whole)
        }

        /** `command (2) number (1) length (2) payload`; a length past the bytes there keeps the bytes there. */
        private fun message(data: ByteArray): MelodyMessage? {
            if (data.size < 5) return null
            val size = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
            return MelodyMessage(
                (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8),
                data[2].toInt() and 0xFF,
                data.copyOfRange(5, 5 + minOf(size, data.size - 5)),
            )
        }
    }
}

private const val DIGITS = "0123456789abcdef"

internal fun hex2(n: Int): String = "" + DIGITS[(n shr 4) and 0xF] + DIGITS[n and 0xF]

internal fun hex4(n: Int): String = hex2(n shr 8) + hex2(n)

internal fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { hex2(it.toInt()) }
