package com.blazify.buds.ui

import kotlin.math.abs

/**
 * A QR code for a short text, made here rather than with a library: byte mode, medium error correction (a code still
 * reads with about 15% of it smudged), versions 1 to 15, which hold up to 412 bytes. Written from the QR code
 * standard (ISO/IEC 18004).
 *
 * [size] modules a side; [dark] says whether a module is dark, row by row. A light margin of four modules goes round
 * it when it is drawn.
 */
class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    fun dark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        /** The code for [text], or null when it is too long for version 15. */
        fun encode(text: String): QrCode? {
            val data = text.encodeToByteArray()
            val version = (1..MAX_VERSION).firstOrNull { capacityBits(it) >= headerBits(it) + data.size * 8 } ?: return null
            val codewords = withErrorCorrection(version, dataCodewords(version, data))
            val builder = Builder(version)
            builder.drawFunctionPatterns()
            builder.drawCodewords(codewords)
            // Every mask makes a readable code; the one with the least penalty is the easiest to scan.
            var best = 0
            var lowest = Int.MAX_VALUE
            for (m in 0 until 8) {
                builder.applyMask(m)
                builder.drawFormatBits(m)
                val penalty = builder.penalty()
                if (penalty < lowest) { lowest = penalty; best = m }
                builder.applyMask(m) // the same mask again undoes it
            }
            builder.applyMask(best)
            builder.drawFormatBits(best)
            return QrCode(version, best, builder.modules)
        }

        const val MAX_VERSION = 15

        // Medium error correction, by version (index 0 unused): codewords of correction per block, and blocks.
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24)
        private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10)

        /** Format bits for medium correction. */
        private const val ECL_BITS = 0

        private fun headerBits(version: Int) = 4 + if (version <= 9) 8 else 16

        /** How many modules carry data and correction, after the patterns. */
        fun rawModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewordCount(version: Int) = rawModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        private fun capacityBits(version: Int) = dataCodewordCount(version) * 8

        /** The data: byte mode, the length, the bytes, an end mark, then the standard padding. */
        private fun dataCodewords(version: Int, data: ByteArray): ByteArray {
            val bits = ArrayList<Boolean>()
            fun put(value: Int, count: Int) { for (i in count - 1 downTo 0) bits += (value ushr i) and 1 == 1 }
            put(0b0100, 4)
            put(data.size, if (version <= 9) 8 else 16)
            data.forEach { put(it.toInt() and 0xFF, 8) }
            val capacity = capacityBits(version)
            put(0, minOf(4, capacity - bits.size))
            put(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacity) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
            return ByteArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, b -> (acc shl 1) or if (bits[i * 8 + b]) 1 else 0 }.toByte() }
        }

        /** Splits the data into blocks, adds Reed-Solomon correction to each, and interleaves them. */
        private fun withErrorCorrection(version: Int, data: ByteArray): ByteArray {
            val blocks = BLOCKS[version]
            val eccLen = ECC_PER_BLOCK[version]
            val raw = rawModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLen = raw / blocks
            val divisor = rsDivisor(eccLen)
            val all = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until blocks) {
                val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
                val dat = data.copyOfRange(k, k + len)
                k += len
                val ecc = rsRemainder(dat, divisor)
                // Short blocks get a placeholder byte, skipped when interleaving, so every block lines up.
                val block = if (i < shortBlocks) dat + byteArrayOf(0) + ecc else dat + ecc
                all += block
            }
            val out = ArrayList<Byte>(raw)
            for (i in 0 until shortLen + 1) {
                for (j in all.indices) {
                    if (i != shortLen - eccLen || j >= shortBlocks) out += all[j][i]
                }
            }
            return out.toByteArray()
        }

        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }

        private fun rsDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }
    }

    private class Builder(val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val align = alignmentPositions()
            val last = align.size - 1
            for (i in align.indices) for (j in align.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                alignment(align[i], align[j])
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(abs(dx), abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, dist != 2 && dist != 4)
            }
        }

        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) set(x + dx, y + dy, maxOf(abs(dx), abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
            val result = IntArray(count)
            result[0] = 6
            var pos = size - 7
            for (i in count - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        fun drawFormatBits(mask: Int) {
            val data = ECL_BITS shl 3 or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = (data shl 10 or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = version shl 12 or rem
            for (i in 0 until 18) {
                val dark = (bits ushr i) and 1 == 1
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!function[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (function[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }

        /** The standard's penalty: long runs, 2×2 blocks, look-alikes of the corner squares, and too much dark or light. */
        fun penalty(): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) run++ else {
                        if (run >= 5) result += 3 + run - 5
                        run = 1
                    }
                }
                if (run >= 5) result += 3 + run - 5
                // 1:1:3:1:1 with four light modules on one side.
                val pattern = booleanArrayOf(true, false, true, true, true, false, true)
                for (i in 0..size - 7) {
                    if ((0 until 7).all { get(i + it) == pattern[it] }) {
                        val before = (1..4).all { i - it < 0 || !get(i - it) }
                        val after = (7..10).all { i + it >= size || !get(i + it) }
                        if (before || after) result += 40
                    }
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += k.coerceAtLeast(0) * 10
            return result
        }
    }
}
