package com.blazify.buds.core.oneplus

import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Family
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.FitResult
import com.blazify.buds.core.model.ListeningMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Packets recorded from real OnePlus Buds 4 (and a few other models) by QuickBuds' owners, from its PROTOCOL.md and
 * testdata/protocol_cases.json.
 */
class MelodyTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun one(s: String): MelodyMessage = hex(s).let { MelodyFrame.Reader().feed(it, it.size).single() }

    @Test
    fun writesMessagesAsTheEarbudsAppDoes() {
        assertContentEquals(hex("AA 07 00 00 00 01 01 00 00"), MelodyFrame.encode(Melody.HANDSHAKE, 1))
        // Noise control off, sent by HeyMelody to a Buds 4.
        assertContentEquals(hex("AA 0A 00 00 04 04 06 03 00 01 01 01"), MelodyFrame.encode(Melody.SET_NOISE, 6, Melody.setNoise(0)))
    }

    @Test
    fun readsMessagesWhateverWayTheyArrive() {
        val battery = "AA 0D 00 00 04 02 FF 06 00 01 02 01 64 02 64"
        val wear = "AA 0F 00 00 04 02 0C 08 00 02 03 01 07 02 07 03 04"
        val reader = MelodyFrame.Reader()
        // Noise first, then two messages in one read.
        val both = reader.feed(hex("13 37 $battery $wear"))
        assertEquals(listOf(0x0204, 0x0204), both.map { it.command })
        assertEquals(0xFF, both[0].seq)
        assertContentEquals(hex("01 02 01 64 02 64"), both[0].payload)
        // One message split over two reads.
        assertTrue(reader.feed(hex("AA 0D 00 00 04")).isEmpty())
        assertEquals(1, reader.feed(hex("02 FF 06 00 01 02 01 64 02 64")).size)
        // A length that never ends isn't a message.
        assertEquals(1, MelodyFrame.Reader().feed(hex("AA 80 80 80 $battery")).size)
    }

    @Test
    fun joinsALongMessageSentInSlices() {
        val first = "AA 67 01 00 00 15 81 04 2C 01 " + (0x00..0x5E).joinToString(" ") { hex2(it) }
        val middle = "AA 67 02 00 01 " + (0x5F..0xC2).joinToString(" ") { hex2(it) }
        val last = "AA 6C 03 00 02 " + ((0xC3..0xFF) + (0x00..0x2B)).joinToString(" ") { hex2(it) }
        val reader = MelodyFrame.Reader()
        assertTrue(reader.feed(hex(first)).isEmpty())
        assertTrue(reader.feed(hex(middle)).isEmpty())
        val message = reader.feed(hex(last)).single()
        assertEquals(0x8115, message.command)
        assertEquals(300, message.payload.size)
        assertEquals(0x2B, message.payload.last().toInt())
        // A last slice with no first one is dropped.
        assertTrue(MelodyFrame.Reader().feed(hex("AA 0D 03 00 02 00 00 00 00 00 00 00 00 00 00")).isEmpty())
    }

    @Test
    fun readsTheBattery() {
        val news = one("AA 0F 00 00 04 02 FF 08 00 01 03 01 5A 02 5A 03 50")
        assertEquals(mapOf(1 to Battery(90), 2 to Battery(90), 3 to Battery(80)), Melody.battery(news.payload, 1))
        // An answer: the case charging (top bit), and a level 0 left out as not known.
        val answer = one("AA 0F 00 00 06 81 F0 08 00 00 03 01 64 02 64 03 D0")
        assertEquals(Battery(80, charging = true), Melody.battery(answer.payload, 1)?.get(3))
        assertNull(Melody.battery(one("AA 0F 00 00 04 02 FF 08 00 01 03 01 5A 02 5A 03 00").payload, 1)?.get(3))
        // A list that runs past the end is no list.
        assertNull(Melody.battery(hex("00 03 01 64 02 64 03"), 1))
    }

    @Test
    fun knowsWhatABuds4Accepts() {
        val accepts = Melody.Accepts.of(hex("00 FF 77 5A EA 67 0E 20 07"))!!
        listOf(Melody.FIND, Melody.SET_TAPS, Melody.SET_SWITCH, Melody.SET_NOISE, Melody.SET_EQ, Melody.SAVE_EQ, Melody.SUBSCRIBE, Melody.BUDS_SPATIAL)
            .forEach { assertTrue(accepts.has(it), hex4(it)) }
        // Its list leaves out the spatial type, and a pair with a 4-byte list can't have commands past it.
        assertFalse(accepts.has(Melody.SPATIAL_TYPE))
        assertFalse(Melody.Accepts.of(hex("00 9B 2C 50 80"))!!.has(Melody.SAVE_EQ))
        assertNull(Melody.Accepts.of(hex("01")))
    }

    @Test
    fun findsTheModel() {
        assertEquals(0x065414, Melody.productId(hex("00 14 54 06")))
        assertEquals("OnePlus Buds 4", MelodyModel.find("Raja's buds", 0x065414)?.name)
        // OPPO Enco Buds2 report the id of a realme pair: the name wins.
        assertEquals("OPPO Enco Buds2", MelodyModel.find("OPPO Enco Buds2", 0x060C12)?.name)
        // A colour of the first OnePlus Buds with its own id.
        assertEquals(0x060414, Melody.productId(hex("00 01 01 10")))
        assertTrue(MelodyModel.knows("realme Buds Air7 Pro"))
        assertEquals(MELODY_MODELS.size, MELODY_MODELS.map { it.id to it.name }.distinct().size)
    }

    @Test
    fun readsTheFirmwareAndSwitches() {
        val firmware = hex("00 04") + "1,2,138,2,2,138,3,1,01,3,2,105".encodeToByteArray()
        assertEquals("138.138.105", Melody.firmware(firmware))
        assertEquals(138, Melody.firmwareNumber("138.138.105"))
        val switches = Melody.switches(hex("00 08 05 00 04 00 0B 01 11 01 18 01 06 00 1B 00 1D 01"), 1)
        assertEquals(mapOf(5 to 0, 4 to 0, 0x0B to 1, 0x11 to 1, 0x18 to 1, 6 to 0, 0x1B to 0, 0x1D to 1), switches)
        // A switch the earbuds changed themselves.
        assertEquals(mapOf(6 to 1), Melody.switches(one("AA 0A 00 00 03 05 FF 03 00 01 06 01").payload, 0))
    }

    @Test
    fun readsAndSetsBuds4NoiseControl() {
        val noise = MelodyNoise(MelodyModel.find(null, 0x065414)!!.modes)
        // Reported bits, as the Buds 4 sent them: off is reported as bit 3, transparency as bit 8.
        assertEquals(ListeningMode.Normal, noise.read(0x0008)?.mode)
        assertEquals(ListeningMode.Transparency, noise.read(0x0100)?.mode)
        assertEquals(MelodyNoise.Reading(ListeningMode.NoiseCancelling, CancelStyle.Manual, 1), noise.read(0x0020))
        assertEquals(CancelStyle.Adaptive, noise.read(0x0080)?.style)
        assertEquals(listOf(6, 5, 4), noise.levels)
        // Set bits: off 0, the plain cancelling 1, smart 7, strongest level 4.
        assertEquals(0, noise.bitFor(ListeningMode.Normal, null))
        assertEquals(1, noise.bitFor(ListeningMode.NoiseCancelling, null))
        assertEquals(7, noise.bitFor(CancelStyle.Adaptive, null, null))
        assertEquals(4, noise.bitFor(CancelStyle.Manual, 5, null))
        assertEquals(3, noise.stepOf(1))
        assertContentEquals(hex("01 01 80"), Melody.setNoise(7))
        assertContentEquals(hex("01 01 00 08"), Melody.setNoise(11))
        // The hold's cycle: off, cancelling, transparency and adaptive.
        assertTrue(noise.cycles)
        assertContentEquals(hex("02 01 07 08"), Melody.setCycle(0x0807))
        val cycle = one("AA 0C 00 00 04 02 FF 05 00 03 02 01 07 08").payload
        assertEquals(0x0807, Melody.mask(cycle, 3))
    }

    @Test
    fun readsRealmeNoiseControl() {
        // realme: one table both ways; strong and plain cancelling share bit 3.
        val noise = MelodyNoise(MelodyModel.find("realme Buds Air7 Pro", null)!!.modes)
        assertEquals(3, noise.bitFor(ListeningMode.NoiseCancelling, null))
        assertEquals(MelodyNoise.Reading(ListeningMode.NoiseCancelling, CancelStyle.Manual, 2), noise.read(0x08))
        assertEquals(ListeningMode.Transparency, noise.read(0x02)?.mode)
        assertEquals(CancelStyle.Adaptive, noise.read(0x20)?.style)
    }

    @Test
    fun readsAndWritesTheTouchTable() {
        val answer = one(
            "AA 51 00 00 08 81 C9 4A 00 00 12 01 01 01 01 01 01 02 01 01 01 03 00 01 01 04 08 01 01 06 00 01 01 05 07 02 01 01 00 " +
                "02 01 02 01 02 01 03 00 02 01 04 08 02 01 06 00 02 01 05 07 01 06 02 00 01 06 03 00 01 06 06 00 02 06 02 00 02 06 03 00 02 06 06 00",
        )
        val taps = Melody.taps(answer.payload)!!
        assertEquals(18, taps.size)
        assertEquals(Melody.Tap(1, 1, 1, 1), taps[0])
        assertEquals(Melody.Tap(2, 6, 6, 0), taps.last())
        // Written back whole, as HeyMelody does.
        assertContentEquals(answer.payload.copyOfRange(1, answer.payload.size), Melody.setTaps(taps))
    }

    @Test
    fun readsAndSavesCustomCurves() {
        val answer = one(
            "AA 46 00 00 22 81 A6 3F 00 00 02 01 FA 06 04 06 47 6F 6F 64 45 51 06 3E 00 FF FA 00 FF E8 03 FE A0 0F 01 40 1F 02 80 3E 05 " +
                "00 FA 06 05 07 43 75 73 74 6F 6D 31 06 3E 00 00 FA 00 00 E8 03 00 A0 0F 00 40 1F 00 80 3E 00",
        )
        val curves = Melody.customEqs(answer.payload, 1)!!
        assertEquals(listOf("GoodEQ", "Custom1"), curves.map { it.name })
        val good = curves[0]
        assertEquals(4, good.id)
        assertTrue(good.selected)
        assertEquals(listOf(62, 250, 1000, 4000, 8000, 16000), good.frequencies)
        assertEquals(listOf(-1, -1, -2, 1, 2, 5), good.gains)
        assertContentEquals(
            hex("02 FA 06 04 06 47 6F 6F 64 45 51 06 3E 00 FF FA 00 FF E8 03 FE A0 0F 01 40 1F 02 80 3E 05"),
            Melody.saveEq(Melody.EQ_SAVE, good),
        )
    }

    @Test
    fun answersWhatTheEarbudsAsk() {
        val now = 0x684EE180L
        assertContentEquals(hex("AA 0C 00 00 00 85 02 05 00 00 80 E1 4E 68"), Melody.answer(one("AA 07 00 00 00 05 02 00 00"), now))
        assertContentEquals(hex("AA 0A 00 00 0C 85 07 03 00 00 01 00"), Melody.answer(one("AA 07 00 00 0C 05 07 00 00"), now))
        assertContentEquals(hex("AA 09 00 00 04 82 FF 02 00 00 F2"), Melody.answer(one("AA 08 00 00 04 02 FF 01 00 F2"), now))
        assertContentEquals(
            hex("AA 1D 00 00 04 82 FF 16 00 00 F4 7B 22 63 6D 64 22 3A 22 61 32 64 70 5F 63 61 74 6F 6E 22 7D"),
            Melody.answer(one("AA 1C 00 00 04 02 FF 15 00 F4 7B 22 63 6D 64 22 3A 22 61 32 64 70 5F 63 61 74 6F 6E 22 7D"), now),
        )
        assertContentEquals(hex("AA 08 00 00 00 86 03 01 00 01"), Melody.answer(one("AA 07 00 00 00 06 03 00 00"), now))
        // News and answers need nothing back.
        assertNull(Melody.answer(one("AA 07 00 00 01 01 04 00 00"), now))
        assertNull(Melody.answer(one("AA 07 00 00 00 85 02 00 00"), now))
        assertNull(Melody.answer(one("AA 0D 00 00 04 02 FF 06 00 01 02 01 64 02 64"), now))
    }

    @Test
    fun theCatalogSendsTheseEarbudsHere() {
        fun family(name: String) = Catalog.model(Device(name, "00:11:22:33:44:55", true)).family
        listOf("OnePlus Buds 3", "OnePlus Nord Buds 2", "OnePlus Buds Pro 2", "OPPO Enco X2", "realme Buds Air7 Pro", "realme Buds T200 Lite")
            .forEach { assertEquals(Family.Melody, family(it), it) }
        // realme earbuds not in the list may speak something else.
        assertEquals(Family.None, family("realme Buds Q"))
    }

    @Test
    fun theModelListReadsBack() {
        val buds4 = MelodyModel.find("OnePlus Buds 4", null)!!
        assertEquals(listOf("Balanced", "Clear Vocals", "Bass"), buds4.presets.map { it.name })
        assertTrue(buds4.has(MelodyModel.CUSTOM_EQ))
        assertEquals(listOf(0x05, 0x04, 0x0B, 0x11, 0x18, 0x06, 0x1B, 0x1D, 0x1C), buds4.askIds)
        // The fourth Buds 3 preset needs firmware 119.
        val buds3 = MelodyModel.find("OnePlus Buds 3", null)!!
        assertEquals(119, buds3.presets.first { it.name == "Bold" }.minFirmware)
        // Most keep three custom curves; the Nord Buds 2 and Buds Ace two.
        assertEquals(3, buds4.curves)
        assertEquals(2, MelodyModel.find("OnePlus Nord Buds 2", null)!!.curves)
        MELODY_MODELS.forEach { model ->
            model.modes
            model.presets
            model.touchRows
        }
    }

    @Test
    fun readsTheFitTest() {
        // OnePlus Buds 4, both sealing well, as QuickBuds recorded it.
        assertEquals(FitResult(true, true), Melody.fit(one("AA 0C 00 00 04 02 FF 05 00 04 01 01 02 01").payload, 1))
        // Average and poor both ask for the earbud to be settled or another tip.
        assertEquals(FitResult(false, false), Melody.fit(hex("04 01 00 02 06"), 1))
        // An error code, or a side left out, is no verdict for that side; none at all is no result.
        assertEquals(FitResult(null, true), Melody.fit(hex("04 01 09 02 01"), 1))
        assertEquals(FitResult(true, null), Melody.fit(hex("04 01 01"), 1))
        assertNull(Melody.fit(hex("04"), 1))
        assertTrue(Melody.Accepts.of(hex("00 FF 77 5A EA 67 0E 20 07"))!!.has(Melody.FIT_TEST))
    }
}
