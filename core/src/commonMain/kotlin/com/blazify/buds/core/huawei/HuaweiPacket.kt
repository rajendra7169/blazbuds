package com.blazify.buds.core.huawei

/**
 * One message in the protocol Huawei and Honor earbuds share: `5A`, a length, `00`, a service and command byte, then
 * typed fields (type, length, value), and a CRC-16 (XMODEM) of all that. Asking for something sends the field types
 * wanted with empty values; the answer fills them in. A field of type 127 is an error code.
 */
data class HuaweiPacket(val service: Int, val command: Int, val fields: Map<Int, ByteArray> = emptyMap()) {
    val id: Int get() = (service shl 8) or command

    fun field(type: Int): ByteArray? = fields[type]

    val failed: Boolean get() = ERROR in fields

    override fun toString(): String =
        "Huawei(%02x %02x, %s)".format(service, command, fields.entries.joinToString { (t, v) -> "$t=" + v.joinToString(" ") { "%02x".format(it) } })

    companion object {
        const val ERROR = 127

        /** A question: the field types wanted, each empty. */
        fun ask(service: Int, command: Int, vararg types: Int) = HuaweiPacket(service, command, types.associateWith { ByteArray(0) })

        fun encode(packet: HuaweiPacket): ByteArray {
            val body = ArrayList<Byte>()
            body += packet.service.toByte()
            body += packet.command.toByte()
            // Fields in the order given; an empty map keeps no particular order, so sort for a stable message.
            for ((type, value) in packet.fields.entries.sortedBy { it.key }) {
                body += type.toByte()
                body += value.size.toByte()
                value.forEach { body += it }
            }
            val length = body.size + 1
            val head = byteArrayOf(0x5A, (length shr 8).toByte(), length.toByte(), 0) + body.toByteArray()
            val crc = crc16(head, head.size)
            return head + byteArrayOf((crc shr 8).toByte(), crc.toByte())
        }

        /** CRC-16/XMODEM: polynomial 0x1021, starting at 0. */
        fun crc16(data: ByteArray, count: Int): Int {
            var crc = 0
            for (i in 0 until count) {
                crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
                repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
                crc = crc and 0xFFFF
            }
            return crc
        }
    }

    /** Collects bytes as they arrive and hands back whole, checked messages; anything else is skipped. */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, count: Int): List<HuaweiPacket> {
            buffer += bytes.copyOf(count)
            val out = mutableListOf<HuaweiPacket>()
            while (true) {
                val start = buffer.indexOfFirst { it == 0x5A.toByte() }
                if (start < 0) { buffer = ByteArray(0); break }
                if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
                if (buffer.size < 4) break
                val length = ((buffer[1].toInt() and 0xFF) shl 8) or (buffer[2].toInt() and 0xFF)
                val total = 3 + length + 2
                if (length < 3 || length > 4096) { buffer = buffer.copyOfRange(1, buffer.size); continue }
                if (buffer.size < total) break
                val frame = buffer.copyOfRange(0, total)
                buffer = buffer.copyOfRange(total, buffer.size)
                val crc = ((frame[total - 2].toInt() and 0xFF) shl 8) or (frame[total - 1].toInt() and 0xFF)
                if (crc16(frame, total - 2) != crc) continue
                val service = frame[4].toInt() and 0xFF
                val command = frame[5].toInt() and 0xFF
                val fields = mutableMapOf<Int, ByteArray>()
                var at = 6
                while (at + 2 <= total - 2) {
                    val type = frame[at].toInt() and 0xFF
                    val size = frame[at + 1].toInt() and 0xFF
                    if (at + 2 + size > total - 2) break
                    fields[type] = frame.copyOfRange(at + 2, at + 2 + size)
                    at += 2 + size
                }
                out += HuaweiPacket(service, command, fields)
            }
            return out
        }
    }
}
