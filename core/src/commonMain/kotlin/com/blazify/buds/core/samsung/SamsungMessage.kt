package com.blazify.buds.core.samsung

/**
 * One message on a Galaxy Buds control channel: a start byte, a two-byte header with the size, the message id, its
 * payload, a CRC-16 (XMODEM, little-endian) of the id and payload, and an end byte. The Buds+ and later use `FD … DD`
 * and keep the size in the low ten bits of a little-endian header; the 2019 Buds use `FE … EE` with a type byte and
 * then a one-byte size. The size counts the id, the payload and the CRC.
 *
 * Adapted from GalaxyBudsClient's SppMessage (Tim Schneeberger, GPL-3.0).
 */
class SamsungMessage(val id: Int, val payload: ByteArray = ByteArray(0)) {
    override fun toString(): String = "Samsung(%02x: %s)".format(id, payload.joinToString(" ") { "%02x".format(it) })

    companion object {
        const val SOM = 0xFD
        const val EOM = 0xDD
        const val LEGACY_SOM = 0xFE
        const val LEGACY_EOM = 0xEE

        fun encode(message: SamsungMessage, legacy: Boolean): ByteArray {
            val size = 1 + message.payload.size + 2
            val out = ByteArray(size + 4)
            out[0] = (if (legacy) LEGACY_SOM else SOM).toByte()
            if (legacy) {
                out[1] = 0 // a request
                out[2] = size.toByte()
            } else {
                out[1] = size.toByte()
                out[2] = (size shr 8).toByte()
            }
            out[3] = message.id.toByte()
            message.payload.copyInto(out, 4)
            val crc = crc16(out, 3, 1 + message.payload.size)
            out[4 + message.payload.size] = crc.toByte()
            out[5 + message.payload.size] = (crc shr 8).toByte()
            out[out.size - 1] = (if (legacy) LEGACY_EOM else EOM).toByte()
            return out
        }

        /** CRC-16/XMODEM (polynomial 0x1021, starting at 0) over [count] bytes from [from]. */
        fun crc16(bytes: ByteArray, from: Int = 0, count: Int = bytes.size - from): Int {
            var crc = 0
            for (i in from until from + count) {
                crc = crc xor ((bytes[i].toInt() and 0xFF) shl 8)
                repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
                crc = crc and 0xFFFF
            }
            return crc
        }
    }

    /**
     * Cuts a byte stream into messages. Bytes that don't make a whole, correct message are skipped one at a time until
     * the next start byte, so a broken message never stops the ones after it. Fragments of large transfers (firmware,
     * logs) are dropped, as nothing here needs them.
     */
    class Reader(private val legacy: Boolean) {
        private var pending = ByteArray(0)
        private val som = (if (legacy) LEGACY_SOM else SOM).toByte()
        private val eom = (if (legacy) LEGACY_EOM else EOM).toByte()

        fun feed(bytes: ByteArray, count: Int = bytes.size): List<SamsungMessage> {
            pending += bytes.copyOf(count)
            val out = mutableListOf<SamsungMessage>()
            var at = 0
            while (true) {
                while (at < pending.size && pending[at] != som) at++
                if (pending.size - at < 4) break
                val total = frame(at)
                when {
                    total > 0 -> {
                        val header = (pending[at + 1].toInt() and 0xFF) or ((pending[at + 2].toInt() and 0xFF) shl 8)
                        val fragment = !legacy && header and 0x2000 != 0
                        if (!fragment) out += SamsungMessage(pending[at + 3].toInt() and 0xFF, pending.copyOfRange(at + 4, at + total - 3))
                        at += total
                    }
                    total == 0 -> at++
                    else -> {
                        // Not all here yet; but a start byte that was really noise could claim a long message, so if a
                        // whole message follows, that start was false.
                        val next = (at + 1 until pending.size).firstOrNull { pending[it] == som && frame(it) > 0 } ?: break
                        at = next
                    }
                }
            }
            pending = pending.copyOfRange(at, pending.size)
            return out
        }

        /** The length of the whole, correct message starting at [at]; 0 if there isn't one, -1 if it isn't all here. */
        private fun frame(at: Int): Int {
            if (pending.size - at < 4) return -1
            val b1 = pending[at + 1].toInt() and 0xFF
            val b2 = pending[at + 2].toInt() and 0xFF
            val size = if (legacy) b2 else (b1 or (b2 shl 8)) and 0x3FF
            if (size < 3) return 0
            val total = size + 4
            if (pending.size - at < total) return -1
            val crcAt = at + 3 + size - 2
            val crc = (pending[crcAt].toInt() and 0xFF) or ((pending[crcAt + 1].toInt() and 0xFF) shl 8)
            return if (pending[at + total - 1] == eom && crc == crc16(pending, at + 3, size - 2)) total else 0
        }
    }
}
