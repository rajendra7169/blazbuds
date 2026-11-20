package com.blazify.buds.core.nothing

import com.blazify.buds.core.model.Battery
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bytes from real earbuds where there are some: a Nothing Ear (B171) report in PairFree's notes, a CMF Buds 2 Plus
 * (B184) in nada's, an Ear (3a) and a CMF Buds Pro 2 in nadamas's, and the writes in Gadgetbridge's protocol test.
 */
class NothingTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun theCrcIsModbus() {
        assertEquals(0x4B37, NothingFrame.crc16("123456789".encodeToByteArray()))
    }

    @Test
    fun buildsFramesAsNothingsAppDoes() {
        assertContentEquals(hex("55600107c00000006d1f"), NothingFrame.encode(NothingCommands.READ_BATTERY, number = 0))
        assertContentEquals(hex("5560011ec0010003039de9"), NothingFrame.encode(NothingCommands.READ_NOISE, NothingCommands.NOISE_GROUPS, 3))
        assertContentEquals(hex("5560010ff0030005010700fb47"), NothingFrame.encode(NothingCommands.SET_NOISE, hex("010700"), 5))
        // Gadgetbridge's: bass boost on at strength 4, and the Dirac Electronic preset.
        assertContentEquals(hex("55600151f0020070010426e5"), NothingFrame.encode(NothingCommands.SET_BASS, hex("0104"), 0x70))
        assertContentEquals(hex("5560011df00200140200ebcd"), NothingFrame.encode(NothingCommands.SET_DIRAC, hex("0200"), 0x14))
    }

    @Test
    fun readsAnswersSplitAndJoinedAsTheyArrive() {
        // A noise report from the earbuds, then Gadgetbridge's two writes, cut anywhere.
        val bytes = hex("5520011e4006000301070002040011ba" + "55600151f0020070010426e5" + "5560011df00200140200ebcd")
        val reader = NothingFrame.Reader()
        val first = reader.feed(bytes.copyOfRange(0, 13))
        val rest = reader.feed(bytes.copyOfRange(13, bytes.size))
        assertEquals(0, first.size)
        assertEquals(3, rest.size)
        assertEquals(NothingCommands.READ_NOISE, rest[0].id)
        assertTrue(rest[0].isAnswer && rest[0].ok)
        assertEquals(NothingNoise.TRANSPARENCY to NothingNoise.ADAPTIVE, NothingCommands.noise(rest[0].payload))
        assertEquals(NothingCommands.SET_BASS, rest[1].id)
        assertContentEquals(hex("0104"), rest[1].payload)
        assertEquals(0x70, rest[1].number)
        assertEquals(NothingCommands.SET_DIRAC, rest[2].id)
    }

    @Test
    fun dropsAFrameWithABadCrcAndKeepsGoing() {
        val bytes = hex("5520011e4006000301070002040011bb" + "55600107c00000006d1f")
        val frames = NothingFrame.Reader().feed(bytes)
        assertEquals(listOf(NothingCommands.READ_BATTERY), frames.map { it.id })
    }

    @Test
    fun readsTheEar1sFramesWithoutACrc() {
        val frames = NothingFrame.Reader().feed(hex("55400107c0000000"))
        assertEquals(NothingCommands.READ_BATTERY, frames.single().id)
    }

    @Test
    fun anUnsupportedAnswerSaysSo() {
        // The control word's low bits carry the result: 3 is "not supported".
        val frame = NothingFrame.Reader().feed(NothingFrame.encode(0x404E, number = 1).also { it[1] = 0x63 }.let { f ->
            val crc = NothingFrame.crc16(f, f.size - 2)
            f[f.size - 2] = crc.toByte(); f[f.size - 1] = (crc shr 8).toByte(); f
        }).single()
        assertEquals(NothingFrame.UNSUPPORTED, frame.result)
    }

    @Test
    fun nadasNoiseReportsFromACmfBuds2Plus() {
        assertEquals(NothingNoise.TRANSPARENCY to NothingNoise.ADAPTIVE, NothingCommands.noise(hex("010700020400")))
        assertEquals(NothingNoise.HIGH to NothingNoise.HIGH, NothingCommands.noise(hex("010100020100")))
    }

    @Test
    fun readsANothingEarAsPairFreesReportHasIt() {
        assertEquals("B171", NothingCommands.productId(hex("71b1")))
        assertEquals("Nothing Ear", NothingModel.byId("B171")?.name)
        assertEquals("1.0.1.56", hex("312e302e312e3536").decodeToString())
        assertEquals(mapOf(2 to Battery(95), 3 to Battery(100)), NothingCommands.batteries(hex("02025f0364")))
        // In-ear detection is extra feature 1, on.
        assertEquals(1, NothingCommands.extras(hex("09 01 01 02 01 07 01 09 01 0a 01 0b 00 0e 01 12 01 15 01"))[NothingCommands.IN_EAR])
        val keys = NothingCommands.keys(hex("08 02 01 02 08 03 01 02 09 02 01 03 0b 03 01 03 0b 02 01 07 16 03 01 07 16 02 01 09 01 03 01 09 01"))
        assertEquals(8, keys.size)
        assertEquals(NothingCommands.Key(2, 1, 2, NothingAction.PREVIOUS), keys[0])
        assertEquals(NothingCommands.Key(3, 1, 2, NothingAction.NEXT), keys[1])
        assertEquals(NothingCommands.Key(2, 1, 7, 0x16), keys[4])
        // Its custom curve was never set: a count of zero.
        assertNull(NothingCommands.curve(ByteArray(53)))
    }

    @Test
    fun theOtherModelNumbersRead() {
        assertEquals("B190", NothingCommands.productId(hex("90b1")))
        assertEquals("B187", NothingCommands.productId(hex("87b1")))
        assertEquals("B184", NothingCommands.productId(hex("84b1")))
        assertEquals("CMF Buds Pro 2", NothingModel.byId("B187")?.name)
    }

    @Test
    fun chargingIsTheTopBit() {
        assertEquals(mapOf(4 to Battery(60, charging = true)), NothingCommands.batteries(hex("0104bc")))
        assertEquals(mapOf(6 to Battery(80)), NothingCommands.batteries(hex("010650")))
    }

    @Test
    fun theOwnCurveGoesOutAndComesBack() {
        val shape = NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 1.0f)
        val bytes = NothingCommands.curve(3.0, -2.0, 1.4, shape)
        assertEquals(1 + 4 + 3 * 13 + 9, bytes.size)
        // Count, then the overall gain: minus the highest boost, -3 dB.
        assertContentEquals(hex("03 00 00 40 c0"), bytes.copyOfRange(0, 5))
        // The peak first: kind 1, -2 dB at 980 Hz, Q 0.7.
        assertContentEquals(hex("01 00 00 00 c0 00 00 75 44 33 33 33 3f"), bytes.copyOfRange(5, 18))
        assertEquals(listOf(3.0, -2.0, 1.0), NothingCommands.curve(bytes))
    }

    @Test
    fun aFlatCurveHasNoOverallCut() {
        val bytes = NothingCommands.curve(0.0, -1.0, 0.0, NothingCurve(140f, 0.8f, 980f, 0.7f, 6900f, 1.0f))
        assertContentEquals(hex("03 00 00 00 00"), bytes.copyOfRange(0, 5))
    }

    @Test
    fun touchWritesAsNothingsAppDoes() {
        assertContentEquals(hex("01 03 01 07 0a"), NothingCommands.setKey(NothingCommands.Key(3, 1, 7, NothingAction.NOISE)))
    }

    @Test
    fun strengthsSnapToTheNearest() {
        val ear1 = NothingModel.byId("B181")!!.strengths
        assertEquals(NothingNoise.LOW, NothingBuds.nearest(1, ear1))
        assertEquals(NothingNoise.HIGH, NothingBuds.nearest(3, ear1))
        assertEquals(NothingNoise.HIGH, NothingBuds.nearest(5, ear1))
        val ear2 = NothingModel.byId("B155")!!.strengths
        assertEquals(NothingNoise.MID, NothingBuds.nearest(3, ear2))
        assertEquals(NothingNoise.HIGH, NothingBuds.nearest(4, ear2))
    }
}
