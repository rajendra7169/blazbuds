package com.blazify.buds.core.soundcore

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A3947Test {
    /** A real Liberty 4 NC's answer, both earbuds on firmware 07.61, connected to a phone and a computer. */
    private val real = hex(
        "09 ff 00 00 01 01 01 a9 00 00 01 05 05 00 00 30 37 2e 36 31 30 37 2e 36 31 33 39 34 37 61 31 62 32 63 33 64 34 65 35 66 36 30 2e 31 2e 36 fe fe 93 8a 78 7f 8d 98 7e 89 00 00 ff ff ff ff ff ff ff ff ff ff 01 00 91 96 8c 94 83 82 7b 73 00 00 91 96 8c 94 83 82 7b 73 00 00 6a f2 2b 2b 00 92 96 8c 95 85 82 7b 74 00 00 92 96 8c 95 85 82 7b 74 00 00 00 00 12 11 66 11 66 11 32 11 33 11 44 11 44 00 ff 00 ff 33 02 50 00 01 00 01 03 00 00 00 00 ff ff 04 64 00 00 00 01 00 00 01 00 00 5a 00 01 01 01 02 01 01 ff 7b",
    )

    private fun body(): ByteArray = PacketCodec.Reader().feed(real).single().body

    @Test
    fun readsTheRealEarbuds() {
        val raw = body()
        assertEquals(159, raw.size)
        val state = A3947.read(raw, ldac = false, hosts = emptyList(), name = "Liberty 4 NC")
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals(80, state.case?.percent)
        assertEquals("07.61", state.firmware)
        assertEquals("3947a1b2c3d4e5f6", state.serial)
        assertTrue(state.bothConnected)
        val sound = state.sound!!
        assertEquals(ListeningMode.Normal, sound.mode)
        assertEquals(CancelStyle.Adaptive, sound.cancelStyle)
        assertEquals(setOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency), sound.cycle)
        val eq = state.equalizer!!
        assertEquals(listOf(2.7, 1.8, 0.0, 0.7, 2.1, 3.2, 0.6, 1.7), eq.left)
        assertEquals(null, eq.preset)
        assertEquals(TouchAction.PlayPause, state.controls!!.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.None, state.controls!!.actions[Ear.Right to Press.Triple])
        assertEquals(true, state.multipoint!!.enabled)
        assertEquals(30, state.autoPowerOff!!.minutes)
        assertEquals(false, state.volumeLimit!!.enabled)
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(false, state.toggles[Toggle.GamingMode])
    }

    @Test
    fun soundModesChangeOneFieldAtATimeParentsFirst() {
        val raw = body()
        val now = raw.copyOfRange(A3947.SOUND, A3947.SOUND + 7)
        val sound = A3947.sound(raw, A3947.SOUND, 0x33).copy(mode = ListeningMode.NoiseCancelling, cancelStyle = CancelStyle.Manual, manualLevel = 3)
        val steps = A3947.soundSteps(now, A3947.soundTarget(now, sound))
        assertEquals(
            listOf("00 50 00 01 00 01 03", "00 50 00 00 00 01 03", "00 30 00 00 00 01 03"),
            steps.map { it.toHex() },
        )
        assertEquals(
            "08 ee 00 00 00 06 81 11 00 00 30 00 00 00 01 03 c2",
            PacketCodec.encode(Packet(A3947.SOUND_SET, steps.last())).toHex(),
        )
    }

    @Test
    fun limiterCurveMatchesTheOfficialApp() {
        assertContentEquals(intArrayOf(8, -4, 1, -1, 1, -1, 4, -9, 0, -120), A3947.drc(intArrayOf(60, 0, 0, 0, 0, 0, 0, -60, 0, 0)))
        assertContentEquals(intArrayOf(3, 2, 0, 0, 0, 0, 0, 0, 0, -120), A3947.drc(A3947.PRESETS[2].second))
    }

    @Test
    fun equalizerMessageMatchesAKnownGoodOne() {
        // The hearing profile from OpenSCQ30's known-good capture, put into the real state.
        val raw = body()
        val curve = hex("70 75 8c 94 96 8e 86 83 3c 3c")
        raw[A3947.HEAR_ID] = 0
        curve.copyInto(raw, 61); curve.copyInto(raw, 71)
        hex("68 64 22 40").copyInto(raw, 81)
        raw[85] = 2
        curve.copyInto(raw, 86); curve.copyInto(raw, 96)
        raw[A3947.HEAR_ID_GENRE] = 6
        val body = A3947.equalizerBody(raw, 0xFE, intArrayOf(60, 0, 0, 0, 0, 0, 0, -60, 0, 0))
        val expected = "08 ee 00 00 00 03 87 67 00 fe fe 06 00 b4 78 78 78 78 78 78 3c 78 78 b4 78 78 78 78 78 78 3c " +
            "78 78 00 00 00 70 75 8c 94 96 8e 86 83 3c 3c 70 75 8c 94 96 8e 86 83 3c 3c 68 64 22 40 02 70 " +
            "75 8c 94 96 8e 86 83 3c 3c 70 75 8c 94 96 8e 86 83 3c 3c 80 74 79 77 79 77 7c 6f 78 00 80 74 " +
            "79 77 79 77 7c 6f 78 00 00 8f"
        assertEquals(expected, PacketCodec.encode(Packet(A3947.EQ_SET, body)).toHex())
    }

    @Test
    fun customBandsKeepTheHiddenBandsAsRead() {
        val tenths = A3947.customTenths(body(), listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.5))
        assertContentEquals(intArrayOf(10, 0, 0, 0, 0, 0, 0, -15, -120, -120), tenths)
    }

    @Test
    fun aTouchChangesOnlyTheCurrentHalf() {
        // Left triple is off (00 ff); setting it to volume up sends f0, then turns the touch on.
        val packets = A3947.touchBodies(body(), Ear.Left, Press.Triple, TouchAction.VolumeUp)
        assertEquals(listOf("08 ee 00 00 00 04 81 0d 00 00 05 f0 7d", "08 ee 00 00 00 04 83 0d 00 00 05 01 90"), packets.map { PacketCodec.encode(it).toHex() })
        assertTrue(A3947.touchBodies(body(), Ear.Left, Press.Single, TouchAction.PlayPause).isEmpty())
    }

    @Test
    fun readsTheRememberedDevices() {
        val name6 = "Pixel 6a".encodeToByteArray() + ByteArray(24)
        val name7 = "Pixel 7a".encodeToByteArray() + ByteArray(24)
        val list = hex("01 01 28 01 66 14 92 2c 3a d4") + name6 + hex("28 01 67 14 92 2c 3a d4") + name7
        val hosts = A3947.hosts(list)
        assertEquals(listOf("Pixel 6a", "Pixel 7a"), hosts.map { it.name })
        assertEquals("D4:3A:2C:92:14:66", hosts[0].address)
        assertEquals("66 14 92 2c 3a d4", A3947.macBytes(hosts[0].address).toHex())
        assertFalse(hosts.isEmpty())
    }
}
