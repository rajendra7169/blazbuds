package com.blazify.buds.core.samsung

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The recorded statuses read as GalaxyBudsClient's own tests expect them to be. */
class SamsungStatusTest {
    private fun status(model: SamsungModel, bytes: ByteArray): SamsungStatus {
        val message = SamsungMessage.Reader(model.legacy).feed(bytes).single()
        return SamsungStatus.extended(model, message.payload)!!
    }

    @Test
    fun buds2019() {
        val s = status(SamsungModel.Buds, SamsungCaptures.BUDS_REV3)
        assertEquals(3, s.revision)
        assertEquals(70, s.left)
        assertEquals(95, s.right)
        assertNull(s.case)
        assertEquals(true, s.coupled)
        assertEquals(0, s.noise) // ambient sound off
        assertEquals(false, s.voiceFocus)
        assertEquals(3, s.preset) // on, 7 = Dynamic without Dolby Atmos
        assertEquals(2, s.holdLeft) // volume
        assertEquals(2, s.holdRight)
        assertEquals(false, s.touchLocked)
    }

    @Test
    fun budsPlus() {
        val s = status(SamsungModel.BudsPlus, SamsungCaptures.BUDS_PLUS_REV13)
        assertEquals(87, s.left)
        assertEquals(84, s.right)
        assertNull(s.case) // 101: the case isn't telling
        assertEquals(0, s.noise)
        assertEquals(3, s.preset)
        assertEquals(2, s.holdLeft) // ambient sound
        assertEquals(false, s.touchLocked)
        assertNull(s.charging)
    }

    @Test
    fun budsLive() {
        val s = status(SamsungModel.BudsLive, SamsungCaptures.BUDS_LIVE_REV9)
        assertEquals(15, s.left)
        assertEquals(97, s.right)
        assertEquals(100, s.case)
        assertEquals(1, s.noise) // cancelling on
        assertEquals(0, s.preset)
        assertEquals(2, s.holdLeft) // noise cancelling
        assertEquals(false, s.touchLocked)
    }

    @Test
    fun budsPro() {
        val s = status(SamsungModel.BudsPro, SamsungCaptures.BUDS_PRO_REV10)
        assertEquals(89, s.left)
        assertEquals(94, s.right)
        assertEquals(100, s.case)
        assertEquals(0, s.noise)
        assertEquals(1, s.preset)
        assertEquals(2, s.holdRight) // noise controls
        assertEquals(false, s.touchLocked)
    }

    @Test
    fun buds2() {
        val s = status(SamsungModel.Buds2, SamsungCaptures.BUDS2_REV10)
        assertEquals(100, s.left)
        assertEquals(51, s.case)
        assertEquals(1, s.noise)
        assertEquals(3, s.preset)
        // 0x3F: every gesture on, but the whole touchpad off.
        assertEquals(true, s.touchLocked)
        assertEquals(0x3F, s.gestures)
        assertEquals(Triple(false, false, false), s.charging)
    }

    @Test
    fun buds2Pro() {
        val s = status(SamsungModel.Buds2Pro, SamsungCaptures.BUDS2_PRO_REV13)
        assertEquals(13, s.revision)
        assertEquals(88, s.case)
        assertEquals(0, s.noise)
        assertEquals(1, s.preset)
        assertEquals(false, s.touchLocked)
        assertEquals(Triple(false, false, false), s.charging)
    }

    @Test
    fun budsFe() {
        val s = status(SamsungModel.BudsFe, SamsungCaptures.BUDS_FE_REV2)
        assertEquals(58, s.left)
        assertEquals(89, s.right)
        assertNull(s.case)
        assertEquals(0, s.noise)
        assertEquals(0, s.preset)
        assertEquals(3, s.holdLeft) // volume
        assertEquals(false, s.touchLocked)
        assertEquals(Triple(false, false, false), s.charging)
    }

    @Test
    fun shortStatusCarriesChargingOnLaterModels() {
        // Revision, left 40, right 41, coupled, main, both in the case, case 77, left and case charging.
        val payload = byteArrayOf(2, 40, 41, 1, 0, 0x33, 77, 0x11)
        val s = SamsungStatus.basic(SamsungModel.Buds3, payload, revision = 2)!!
        assertEquals(40, s.left)
        assertEquals(77, s.case)
        assertEquals(Triple(true, false, true), s.charging)
        assertNull(SamsungStatus.basic(SamsungModel.BudsPro, payload, revision = 10)!!.charging)
    }

    @Test
    fun knowsTheModelsByName() {
        assertEquals(SamsungModel.Buds, SamsungModel.of("Galaxy Buds (A1B2)"))
        assertEquals(SamsungModel.BudsPlus, SamsungModel.of("Galaxy Buds+ (A1B2)"))
        assertEquals(SamsungModel.BudsLive, SamsungModel.of("Galaxy Buds Live (A1B2)"))
        assertEquals(SamsungModel.BudsPro, SamsungModel.of("Galaxy Buds Pro (A1B2)"))
        assertEquals(SamsungModel.Buds2, SamsungModel.of("Galaxy Buds2 (A1B2)"))
        assertEquals(SamsungModel.Buds2Pro, SamsungModel.of("Buds2 Pro"))
        assertEquals(SamsungModel.BudsFe, SamsungModel.of("Galaxy Buds FE"))
        assertEquals(SamsungModel.BudsCore, SamsungModel.of("Galaxy Buds Core"))
        assertEquals(SamsungModel.Buds3, SamsungModel.of("Galaxy Buds3"))
        assertEquals(SamsungModel.Buds3Pro, SamsungModel.of("Galaxy Buds3 Pro"))
        assertEquals(SamsungModel.Buds3Fe, SamsungModel.of("Galaxy Buds3 FE"))
        assertEquals(SamsungModel.Buds4, SamsungModel.of("Galaxy Buds4"))
        assertEquals(SamsungModel.Buds4Pro, SamsungModel.of("Galaxy Buds4 Pro"))
        assertNull(SamsungModel.of("Pixel Buds Pro"))
        assertNull(SamsungModel.of("Redmi Buds 4 Pro"))
        assertNull(SamsungModel.of("OnePlus Buds Pro 2"))
    }

    @Test
    fun firmwareNames() {
        // Buds+: variant U, year 2021 (U), month A, release 1.
        assertEquals("R175XXU0AUA1", SamsungBuds.firmware(SamsungModel.BudsPlus, byteArrayOf(0, 0x11, 1, 0x60, 1)))
        // 2019 Buds: release numbers up to 15 are written in hex.
        assertEquals("R170XXU0ATBC", SamsungBuds.firmware(SamsungModel.Buds, byteArrayOf(0x11, 1, 0x51, 12)))
        // Buds3 Pro and later send the name whole.
        val named = byteArrayOf(0, 0x11) + "R630XXU0AXK1".encodeToByteArray() + ByteArray(8)
        assertEquals("R630XXU0AXK1", SamsungBuds.firmware(SamsungModel.Buds3Pro, named))
    }
}
