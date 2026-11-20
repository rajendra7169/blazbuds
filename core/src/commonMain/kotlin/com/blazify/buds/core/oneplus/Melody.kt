package com.blazify.buds.core.oneplus

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.FitResult

/**
 * The commands of the OnePlus / OPPO / realme protocol this app uses, and readers for their answers. Commands
 * 0x01xx ask, 0x04xx change, 0x0204 is news from the earbuds and 0x05xx are the earbuds asking the phone. Each
 * answer starts with a status byte, 00 for "fine".
 */
object Melody {
    /** The control channel HeyMelody opens; only this one carries the protocol on the models checked. */
    const val SERVICE = "0000079a-d102-11e1-9b23-00025b00a5a5"

    /** The plain serial port, which some realme earbuds use instead. */
    const val SERIAL = "00001101-0000-1000-8000-00805f9b34fb"

    const val HANDSHAKE = 0x0100
    const val PRODUCT = 0x0103
    const val FIRMWARE = 0x0105
    const val BATTERY = 0x0106
    const val TAPS = 0x0108
    const val NOISE = 0x010C
    const val SWITCHES = 0x010D
    const val EQ = 0x010F
    const val CUSTOM_EQS = 0x0122
    const val OFFERED = 0x0200
    const val SUBSCRIBE_ONE = 0x0201
    const val NEWS = 0x0204
    const val SUBSCRIBE = 0x0205
    const val FIND = 0x0400
    const val SET_TAPS = 0x0401
    const val SET_SWITCH = 0x0403
    const val SET_NOISE = 0x0404
    const val FIT_TEST = 0x0405
    const val SET_EQ = 0x0406
    const val PHONE_STATE = 0x0413
    const val SAVE_EQ = 0x0418
    const val GAME_SOUND = 0x0423
    const val SPATIAL_TYPE = 0x012A
    const val BUDS_SPATIAL = 0xEF03
    const val SWITCH_NEWS = 0x0503
    const val EQ_NEWS = 0x0504

    // What each switch id is (0x010D / 0x0403).
    const val WEAR = 0x04
    const val GAME = 0x06
    const val DUAL = 0x11
    const val SPATIAL = 0x1B
    const val WIND = 0x1A
    const val BASS = 0x1D

    // News codes (the first byte of a 0x0204 payload).
    const val NEWS_BATTERY = 0x01
    const val NEWS_NOISE = 0x03
    const val NEWS_FIT = 0x04
    const val NEWS_GAME = 0x05

    /** Ids asked of a model whose list gives no ids of its own, as HeyMelody does. */
    val DEFAULT_ASK = listOf(0x05, 0x04, 0x0B, 0x11, 0x13, 0x18, 0x06, 0x1B, 0x1C, 0x27, 0x28, 0x1D, 0x09, 0x17, 0x30, 0x31, 0x3A, 0x32, 0x35, 0x37, 0x38, 0x3B, 0x0C)

    /** Commands every pair accepts, whatever its list says. */
    private val ALWAYS = setOf(0x0100, 0x0101, 0x0102, 0x0103, 0x0104, 0x0106, 0x010B, 0x010D, 0x0F00, 0x0F03, 0x0F04)

    /**
     * Which bit of the earbuds' answer to the handshake allows which commands. The earbuds' own apps never send a
     * command whose bit is off, so neither does this one.
     */
    private val BITS: Map<Int, Int> = buildMap {
        fun bit(n: Int, vararg commands: Int) = commands.forEach { put(it, n) }
        bit(0, 0x0105); bit(1, 0x0106); bit(2, 0x0107); bit(3, 0x0108, 0x0401, 0x0416); bit(4, 0x0109)
        bit(5, 0x0400); bit(6, 0x0402); bit(7, 0x0403); bit(8, 0x010C, 0x0404); bit(9, 0x0405)
        bit(10, 0x0406, 0x010F); bit(11, 0x0407); bit(13, 0x0408); bit(14, 0x0409); bit(17, 0x0114)
        bit(19, 0x040E, 0x0115, 0x0116); bit(20, 0x040F); bit(21, 0x0410, 0x0119); bit(22, 0x0205); bit(23, 0x0F00)
        bit(25, 0x0118, 0x0411); bit(26, 0x011A, 0x0412); bit(27, 0x011C, 0x0413); bit(29, 0x0112, 0x040B)
        bit(30, 0x011E, 0x011F, 0x0415); bit(31, 0x040D); bit(33, 0x0121, 0x0417); bit(34, 0x0122, 0x0418)
        bit(35, 0x0419); bit(36, 0x011D, 0x0414); bit(37, 0x0123, 0x041A); bit(38, 0x0124, 0x041B)
        bit(39, 0x0125, 0x041C, 0x0127, 0x041D, 0x041F); bit(40, 0x0421, 0x0023, 0x0024, 0x0022, 0x0126, 0x0129)
        bit(41, 0xEF01); bit(42, 0xEF02); bit(43, 0xEF03, 0x041E); bit(44, 0x0420); bit(45, 0x001C)
        bit(47, 0x0422, 0x012A); bit(48, 0xEF04); bit(49, 0x0423, 0x012B); bit(51, 0x0424); bit(52, 0xEF06)
        bit(54, 0x012C); bit(55, 0x0425, 0x012E, 0x0426); bit(56, 0x012F); bit(57, 0x0427, 0x0130)
        bit(58, 0x0131, 0x0428); bit(59, 0x0429, 0x0132); bit(60, 0x0014); bit(61, 0x042D, 0x0133); bit(62, 0x042E)
        bit(63, 0xEF07); bit(64, 0xEF08, 0x0136, 0x0433); bit(65, 0xEF09); bit(66, 0x0431, 0x0134)
        bit(67, 0x0432, 0x0135, 0x0435); bit(68, 0x0117)
    }

    /** What the earbuds accept, from their answer to the handshake: `00`, then bits read lowest first. */
    class Accepts(private val bits: ByteArray) {
        fun has(command: Int): Boolean {
            if (command in ALWAYS) return true
            val bit = BITS[command] ?: return false
            val byte = bit / 8
            return byte < bits.size && (bits[byte].toInt() shr (bit % 8)) and 1 == 1
        }

        companion object {
            fun of(answer: ByteArray): Accepts? =
                if (answer.isEmpty() || answer[0].toInt() != 0) null else Accepts(answer.copyOfRange(1, answer.size))
        }
    }

    /**
     * The product id from `00 <3 bytes, low first>`, as the six hex digits the model list uses. Some colours of
     * the first OnePlus Buds report an id of their own and are folded back.
     */
    fun productId(answer: ByteArray): Int? {
        if (answer.size < 4 || answer[0].toInt() != 0) return null
        val id = (answer[1].toInt() and 0xFF) or ((answer[2].toInt() and 0xFF) shl 8) or ((answer[3].toInt() and 0xFF) shl 16)
        return when (id) {
            in 0x100100..0x100102 -> 0x060414
            in 0x100200..0x100202 -> 0x060814
            in 0x108100..0x108102 -> 0x068414
            in 0x108200..0x108202 -> 0x068814
            else -> id
        }
    }

    /**
     * Battery: `<count>` then `<part> <level>` pairs, part 1 left, 2 right, 3 the case. The low seven bits are the
     * percent, the top bit says charging; 0 means not known. Null when the list doesn't add up.
     */
    fun battery(list: ByteArray, from: Int = 0): Map<Int, Battery>? {
        if (from >= list.size) return null
        val count = list[from].toInt() and 0xFF
        if (count == 0 || from + 1 + count * 2 > list.size) return null
        return buildMap {
            for (i in 0 until count) {
                val part = list[from + 1 + i * 2].toInt() and 0xFF
                val raw = list[from + 2 + i * 2].toInt() and 0xFF
                val percent = raw and 0x7F
                if (percent in 1..100) put(part, Battery(percent, charging = raw and 0x80 != 0))
            }
        }
    }

    /**
     * Firmware: `00 <count>` then text, `part,kind,version` three at a time. The kind-2 versions in order make the
     * number the earbuds' own app shows, like 138.138.105.
     */
    fun firmware(answer: ByteArray): String? {
        if (answer.size < 3 || answer[0].toInt() != 0) return null
        val fields = answer.copyOfRange(2, answer.size).decodeToString().split(',').map { it.trim() }
        val versions = fields.chunked(3).filter { it.size == 3 && it[1] == "2" }.map { it[2] }
        return versions.joinToString(".").ifEmpty { null }
    }

    /** The firmware number the preset list is gated on: the lower of the two earbuds' versions, 0 if unknown. */
    fun firmwareNumber(firmware: String?): Int {
        val parts = firmware?.split('.')?.map { it.toIntOrNull() ?: 0 }.orEmpty()
        return if (parts.size == 3) parts.take(2).filter { it != 0 }.minOrNull() ?: 0 else parts.firstOrNull() ?: 0
    }

    /**
     * The fit test's verdict, `<part> <seal>` pairs from [from] (part 1 left, 2 right): 1 a good seal, 0 average and
     * 6 poor, which both mean the earbud wants settling or another tip, as the earbuds' own app says; anything else,
     * or a part left out, is no verdict for that side. Null when neither side has one.
     */
    fun fit(news: ByteArray, from: Int): FitResult? {
        fun seal(part: Int): Boolean? {
            var at = from
            while (at + 1 < news.size) {
                if (news[at].toInt() and 0xFF == part) {
                    return when (news[at + 1].toInt() and 0xFF) {
                        1 -> true
                        0, 6 -> false
                        else -> null
                    }
                }
                at += 2
            }
            return null
        }
        val left = seal(1)
        val right = seal(2)
        return if (left == null && right == null) null else FitResult(left, right)
    }

    /** Switches: `<count>` then `<id> <value>` pairs; only the ids the earbuds have come back. */
    fun switches(list: ByteArray, from: Int): Map<Int, Int> {
        if (from >= list.size) return emptyMap()
        val count = list[from].toInt() and 0xFF
        return buildMap {
            for (i in 0 until count) {
                val at = from + 1 + i * 2
                if (at + 1 >= list.size) break
                put(list[at].toInt() and 0xFF, list[at + 1].toInt() and 0xFF)
            }
        }
    }

    /** A noise-control value: 1 to 4 bytes, lowest first. */
    fun mask(bytes: ByteArray, from: Int): Int {
        var value = 0
        for (i in minOf(bytes.size, from + 4) - 1 downTo from) value = (value shl 8) or (bytes[i].toInt() and 0xFF)
        return value
    }

    /** A mask in the fewest bytes that hold it, lowest first, as the earbuds take it. */
    fun maskBytes(mask: Int): ByteArray {
        val size = when {
            mask ushr 8 == 0 -> 1
            mask ushr 16 == 0 -> 2
            mask ushr 24 == 0 -> 3
            else -> 4
        }
        return ByteArray(size) { (mask ushr (8 * it)).toByte() }
    }

    /** Sets the noise-control mode by its bit. */
    fun setNoise(bit: Int): ByteArray = byteArrayOf(1, 1) + maskBytes(1 shl bit)

    /** Sets which modes a hold steps through, one setting for both earbuds. */
    fun setCycle(mask: Int): ByteArray = byteArrayOf(2, 1) + maskBytes(mask)

    /** One touch setting: which earbud (1 left, 2 right), which button group, which touch and what it does. */
    data class Tap(val side: Int, val button: Int, val touch: Int, val function: Int)

    /** The touch table: `00 <count>` then four bytes per entry. */
    fun taps(answer: ByteArray): List<Tap>? {
        if (answer.size < 2 || answer[0].toInt() != 0) return null
        return (0 until (answer.size - 2) / 4).map { i ->
            val o = 2 + i * 4
            Tap(answer[o].toInt() and 0xFF, answer[o + 1].toInt() and 0xFF, answer[o + 2].toInt() and 0xFF, answer[o + 3].toInt() and 0xFF)
        }
    }

    /** The whole touch table back, as a change sends it. */
    fun setTaps(taps: List<Tap>): ByteArray =
        byteArrayOf(taps.size.toByte()) + taps.flatMap { listOf(it.side, it.button, it.touch, it.function) }.map { it.toByte() }.toByteArray()

    /** A sound curve the earbuds keep: [tag] holds the gain range (FA 06 is -6 to +6) and is sent back as it came. */
    data class CustomEq(val id: Int, val name: String, val frequencies: List<Int>, val gains: List<Int>, val selected: Boolean, val tag: ByteArray = byteArrayOf(-6, 6))

    /**
     * The earbuds' own curves: `<count>` then `<selected> <tag 2> <id> <name length> <name> <bands> [hz (2, low
     * first) gain]...`, starting at [from] (after the status byte of an answer; at once in their news).
     */
    fun customEqs(answer: ByteArray, from: Int): List<CustomEq>? {
        if (answer.size < from + 1) return null
        val count = answer[from].toInt() and 0xFF
        var o = from + 1
        val out = ArrayList<CustomEq>()
        repeat(count) {
            if (o + 5 > answer.size) return null
            val selected = answer[o].toInt() == 1
            val tag = answer.copyOfRange(o + 1, o + 3)
            val id = answer[o + 3].toInt() and 0xFF
            val nameSize = answer[o + 4].toInt() and 0xFF
            o += 5
            if (o + nameSize + 1 > answer.size) return null
            val name = answer.copyOfRange(o, o + nameSize).decodeToString()
            o += nameSize
            val bands = answer[o].toInt() and 0xFF
            o += 1
            if (o + bands * 3 > answer.size) return null
            val frequencies = (0 until bands).map { (answer[o + it * 3].toInt() and 0xFF) or ((answer[o + it * 3 + 1].toInt() and 0xFF) shl 8) }
            val gains = (0 until bands).map { answer[o + it * 3 + 2].toInt() }
            o += bands * 3
            out += CustomEq(id, name, frequencies, gains, selected, tag)
        }
        return out
    }

    const val EQ_CREATE = 1
    const val EQ_SAVE = 2

    /** Makes a curve (create, id 0, the earbuds pick one) or saves and picks it (save). */
    fun saveEq(action: Int, eq: CustomEq): ByteArray {
        val name = eq.name.encodeToByteArray()
        val bands = eq.frequencies.indices.flatMap { i ->
            listOf(eq.frequencies[i].toByte(), (eq.frequencies[i] shr 8).toByte(), eq.gains[i].toByte())
        }
        return byteArrayOf(action.toByte()) + eq.tag + byteArrayOf(eq.id.toByte(), name.size.toByte()) + name +
            byteArrayOf(eq.frequencies.size.toByte()) + bands.toByteArray()
    }

    /**
     * What to answer when the earbuds ask the phone something, or null for messages that need no answer. Some
     * earbuds drop the link a few seconds after connecting if their questions go unanswered.
     */
    fun answer(message: MelodyMessage, now: Long): ByteArray? {
        if (message.isAnswer) return null
        val reply = message.command or 0x8000
        val group = message.command shr 8
        val payload = when {
            // The time, in seconds since 1970.
            message.command == 0x0500 -> byteArrayOf(0) + ByteArray(4) { (now ushr (8 * it)).toByte() }
            // Is the app in front one the earbuds care about: no.
            message.command == 0x050C -> byteArrayOf(0, 1, 0)
            // The phone's abilities: none of the extras.
            message.command == 0x051C -> byteArrayOf(0, 0, 0)
            // The phone's spatial sound mode.
            message.command == 0x051D -> byteArrayOf(1)
            message.command == NEWS && message.at(0) == 0xF2 -> byteArrayOf(0, 0xF2.toByte())
            message.command == NEWS && message.at(0) == 0xF4 -> {
                val json = message.payload.copyOfRange(1, message.payload.size)
                when {
                    json.isEmpty() -> byteArrayOf(0, 0xF4.toByte())
                    json[0] == '{'.code.toByte() -> byteArrayOf(0, 0xF4.toByte()) + json
                    else -> byteArrayOf(1, 0xF4.toByte())
                }
            }
            group in 0x01..0x05 || group == 0x0F -> return null
            // A command from a group this app doesn't know.
            else -> byteArrayOf(1)
        }
        return MelodyFrame.encode(reply, message.seq, payload)
    }
}
