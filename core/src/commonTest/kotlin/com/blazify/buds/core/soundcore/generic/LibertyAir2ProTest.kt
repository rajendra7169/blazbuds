package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.demo.with
import com.blazify.buds.core.soundcore.PacketCodec
import com.blazify.buds.core.soundcore.generic.models.A3951
import com.blazify.buds.core.soundcore.hex
import com.blazify.buds.core.soundcore.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibertyAir2ProTest {
    private fun parsed(): Parsed = A3951.parse(hex(SoundcoreSamples.STATE.getValue("A3951"))).also {
        readFirmware(it, hex(SoundcoreSamples.FIRMWARE.getValue("A3951")))
        it.switches[Toggle.Ldac] = true
    }

    @Test
    fun readsTheSample() {
        val state = A3951.state(parsed(), "Liberty Air 2 Pro")
        // The sample's levels are both 0.
        assertEquals(0, state.left?.percent)
        assertEquals(0, state.right?.percent)
        assertEquals("00.00", state.firmware)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertEquals(CancelStyle.Transport, state.sound?.cancelStyle)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        assertEquals(6, state.controls?.actions?.size)
        assertEquals(true, state.toggles[Toggle.Ldac])
        assertTrue(Toggle.TouchTone in state.toggles)
    }

    @Test
    fun picksAPreset() {
        val p = parsed()
        val steps = A3951.steps(p, Change.Preset(0x15), A3951.state(p, "x"))!!
        val bytes = PacketCodec.encode(steps.single().packet)
        // The equalizer with the hearing profile switched off goes as 03 87 when the earbuds have an age range, 03 86 when not.
        assertEquals(if (p.age == 0xFF) "03 86" else "03 87", bytes.copyOfRange(5, 7).toHex())
    }

    @Test
    fun changesCancellingKindByWayOfCancelling() {
        val p = parsed()
        p.sound = intArrayOf(2, 0, 0, 0)
        val before = A3951.state(p, "x")
        val change = Change.Cancelling(CancelStyle.Indoor)
        val steps = A3951.steps(p, change, before.with(change))!!
        assertEquals(listOf("00 00 00 00", "00 02 00 00"), steps.map { it.packet.body.toHex() })
    }

    @Test
    fun turningASingleTapOffTurnsBothOff() {
        val p = parsed()
        val change = Change.Touch(Ear.Left, Press.Single, TouchAction.None)
        val steps = A3951.steps(p, change, A3951.state(p, "x"))
        assertNotNull(steps)
        A3951.state(p, "x").controls!!.let { assertTrue(TouchAction.None in it.choices(Press.Single)) }
        steps.forEach { it.apply(p) }
        val after = A3951.state(p, "x").controls!!
        assertEquals(TouchAction.None, after.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.None, after.actions[Ear.Right to Press.Single])
        assertEquals(Equalizer.CUSTOM, 0xFE)
    }
}
