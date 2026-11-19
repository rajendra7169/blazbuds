package com.blazify.buds.core.link

/**
 * The smallest part of Bluetooth's service lookup the app needs: which RFCOMM channel serves a given service.
 * Android and Windows look this up themselves; on Linux the app asks the earbuds.
 */
object Sdp {
    /** A ServiceSearchAttributeRequest for [uuid]'s protocol list (attribute 0x0004). */
    fun request(uuid: ByteArray, transaction: Int = 1): ByteArray {
        require(uuid.size == 16)
        val params = byteArrayOf(0x35, 17, 0x1C) + uuid +
            byteArrayOf(0xFF.toByte(), 0xFF.toByte()) +
            byteArrayOf(0x35, 3, 0x09, 0x00, 0x04) +
            byteArrayOf(0)
        return byteArrayOf(
            0x06,
            (transaction shr 8).toByte(),
            transaction.toByte(),
            (params.size shr 8).toByte(),
            params.size.toByte(),
        ) + params
    }

    /** The RFCOMM channel in a reply: the RFCOMM protocol id (UUID16 0x0003) followed by a one-byte number. */
    fun channel(reply: ByteArray): Int? {
        if (reply.isEmpty() || reply[0].toInt() != 0x07) return null
        for (i in 0..reply.size - 5) {
            if (reply[i].toInt() == 0x19 && reply[i + 1].toInt() == 0x00 && reply[i + 2].toInt() == 0x03 && reply[i + 3].toInt() == 0x08) {
                return reply[i + 4].toInt() and 0xFF
            }
        }
        return null
    }

    fun uuid(text: String): ByteArray = text.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
