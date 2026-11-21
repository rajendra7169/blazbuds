package com.blazify.buds.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The app's own QR codes, read back by a well-known reader. */
class QrCodeTest {
    private fun read(code: QrCode): String {
        val scale = 4
        val margin = 4
        val side = (code.size + margin * 2) * scale
        val pixels = IntArray(side * side) { i ->
            val x = (i % side) / scale - margin
            val y = (i / side) / scale - margin
            val dark = x in 0 until code.size && y in 0 until code.size && code.dark(x, y)
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
    }

    @Test
    fun aSoundsCodeReadsBack() {
        val text = SavedSound("Late night", listOf(2.5, 1.0, 0.0, -1.5, -2.0, -3.0, -3.5, -4.0)).code()
        val code = QrCode.encode(text)!!
        assertEquals(text, read(code))
    }

    @Test
    fun everySizeReadsBack() {
        // From the smallest code to version 15, with the version blocks that come from version 7 on.
        listOf(1, 10, 14, 40, 80, 120, 150, 200, 260, 330, 370).forEach { n ->
            val text = (0 until n).map { "BLAZBUDS-EQ:éq0123456789"[it % 24] }.joinToString("")
            val code = QrCode.encode(text)!!
            assertEquals(text, read(code), "$n characters, version ${code.version}, mask ${code.mask}")
        }
    }

    @Test
    fun tooLongIsNoCode() {
        assertNull(QrCode.encode("x".repeat(500)))
    }
}
