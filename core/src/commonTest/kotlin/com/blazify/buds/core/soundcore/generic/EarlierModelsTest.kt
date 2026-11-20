package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.demo.with
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.models.A3004
import com.blazify.buds.core.soundcore.generic.models.A3005
import com.blazify.buds.core.soundcore.generic.models.A3027
import com.blazify.buds.core.soundcore.generic.models.A3028
import com.blazify.buds.core.soundcore.generic.models.A3031
import com.blazify.buds.core.soundcore.generic.models.A3033
import com.blazify.buds.core.soundcore.generic.models.A3909
import com.blazify.buds.core.soundcore.generic.models.A3926
import com.blazify.buds.core.soundcore.generic.models.A3930
import com.blazify.buds.core.soundcore.generic.models.EARLIER_MODELS
import com.blazify.buds.core.soundcore.hex
import com.blazify.buds.core.soundcore.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The earlier models, read from real answers where OpenSCQ30 has one, and some of their messages checked byte for byte. */
class EarlierModelsTest {
    private fun sample(model: SoundcoreModel): Parsed = model.parse(hex(SoundcoreSamples.STATE.getValue(model.code))).also { p ->
        SoundcoreSamples.FIRMWARE[model.code]?.let { readFirmware(p, hex(it)) }
    }

    private fun repeat(byte: String, times: Int) = List(times) { byte }.joinToString(" ")

    @Test
    fun listHoldsEveryModelOnce() {
        assertEquals(
            listOf("A3004", "A3005", "A3027", "A3028", "A3031", "A3033", "A3909", "A3926", "A3930"),
            EARLIER_MODELS.map { it.code },
        )
    }

    @Test
    fun q20i() {
        val p = sample(A3004)
        val state = A3004.state(p, "Q20i")
        assertEquals(80, state.overall?.percent)
        assertEquals(false, state.overall?.charging)
        assertNull(state.left)
        assertEquals("00.26", state.firmware)
        assertEquals("300489095be238b0", state.serial)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        // A preset goes with the limiter's curve: the id, ten bands, ten more.
        val step = A3004.steps(p, Change.Preset(2), state)!!.single()
        assertEquals(EqSetup.SET_DRC, step.packet.command)
        assertEquals(22, step.packet.body.size)
        assertEquals("02 00 a0 96 82 78 78 78 78 78 78 00", step.packet.body.copyOfRange(0, 12).toHex())
    }

    @Test
    fun q11i() {
        val p = sample(A3005)
        val state = A3005.state(p, "Q11i")
        // 7 means 80%: the level counts from 0 for 10%.
        assertEquals(80, state.overall?.percent)
        assertEquals("01.21", state.firmware)
        assertEquals("3005BABAFB2C9C18", state.serial)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(true, state.autoPowerOff?.enabled)
        assertEquals(60, state.autoPowerOff?.minutes)
        val presets = state.equalizer!!.presets
        assertTrue(presets.none { it.id == 2 })
        assertEquals("Bass booster", presets.last().name)
        // Bass booster has its own id on these.
        val step = A3005.steps(p, Change.Preset(0x7E7E), state)!!.single()
        assertEquals("7e 7e", step.packet.body.copyOfRange(0, 2).toHex())
        val off = A3005.steps(p, Change.PowerOff(true, 120), state)!!.single()
        assertEquals("01 03", off.packet.body.toHex())
    }

    @Test
    fun lifeQ30() {
        val p = sample(A3028)
        val state = A3028.state(p, "Life Q30")
        assertEquals(100, state.overall?.percent)
        assertEquals("04.33", state.firmware)
        assertEquals("30286DC893444798", state.serial)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertEquals(CancelStyle.Transport, state.sound?.cancelStyle)
        assertEquals(listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor), state.sound?.options?.cancelStyles)
        // Piano.
        assertEquals(14, state.equalizer?.presetId)
        assertEquals(3.0, state.equalizer?.left?.get(1))
        assertEquals(true, state.autoPowerOff?.enabled)
        assertEquals(60, state.autoPowerOff?.minutes)
    }

    /** OpenSCQ30's own example for this model: older firmware, so no auto power-off at the end. */
    @Test
    fun lifeQ30OlderFirmware() {
        val body = "05 00 fe fe 3c b4 8f a0 8e b4 74 88 00 00 " + repeat("00", 21) +
            " 02 00 01 00 30 32 2e 33 30 33 30 32 39 30 38 36 45 43 38 32 46 31 32 41 43"
        val p = A3028.parse(hex(body))
        val state = A3028.state(p, "Life Q30")
        assertEquals(ListeningMode.Normal, state.sound?.mode)
        assertEquals(CancelStyle.Transport, state.sound?.cancelStyle)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(listOf(-6.0, 6.0, 2.3, 4.0, 2.2, 6.0, -0.4, 1.6), state.equalizer?.left)
        assertNull(state.autoPowerOff)

        // The earbuds say the mode changed to cancelling, outdoors.
        assertTrue(A3028.pushed(p, Packet(SoundScheme.SOUND_UPDATE, hex("00 01 01 00"))))
        val after = A3028.state(p, "Life Q30")
        assertEquals(ListeningMode.NoiseCancelling, after.sound?.mode)
        assertEquals(CancelStyle.Outdoor, after.sound?.cancelStyle)

        val change = Change.Mode(ListeningMode.Transparency)
        val steps = A3028.steps(p, change, after.with(change))!!
        assertEquals(listOf("01 01 01 00"), steps.map { it.packet.body.toHex() })
    }

    @Test
    fun lifeQ35() {
        val body = "03 01 01 00 " + repeat("78", 8) + " 00 00 " + repeat("00", 21) +
            " 01 02 00 00 30 31 2e 30 32 33 30 32 37 30 30 30 30 30 30 30 30 30 30 30 30 01"
        val p = A3027.parse(hex(body))
        val state = A3027.state(p, "Life Q35")
        assertEquals(60, state.overall?.percent)
        assertEquals(true, state.overall?.charging)
        assertEquals(1, state.equalizer?.presetId)
        assertEquals(ListeningMode.Transparency, state.sound?.mode)
        assertEquals(CancelStyle.Indoor, state.sound?.cancelStyle)
        assertEquals("01.02", state.firmware)
        assertEquals(mapOf(Toggle.WearDetection to true), state.toggles)
        // Newer firmware adds one byte at the end.
        A3027.parse(hex("$body 01"))
        val step = A3027.steps(p, Change.Switch(Toggle.WearDetection, false), state)!!.single()
        assertEquals(SoundcoreModel.WEARING_DETECTION, step.packet.command)
        assertEquals("00", step.packet.body.toHex())
    }

    @Test
    fun vortex() {
        val body = "00 01 03 04 01 00 01 00 " + repeat("78", 16) + " " + repeat("00", 12) + " 02 01 00 00 00 01 01 02"
        val p = A3031.parse(hex(body))
        val state = A3031.state(p, "Vortex")
        assertEquals(60, state.left?.percent)
        assertEquals(true, state.left?.charging)
        assertEquals(80, state.right?.percent)
        assertEquals(ListeningMode.Normal, state.sound?.mode)
        assertEquals(CancelStyle.Outdoor, state.sound?.cancelStyle)
        assertEquals(1, state.equalizer?.presetId)
        assertEquals(mapOf(Toggle.TouchTone to true), state.toggles)
        assertEquals(90, state.autoPowerOff?.minutes)
        assertNull(state.controls)
        // The equalizer only changes with both earbuds out.
        p.both = false
        assertNull(A3031.steps(p, Change.Preset(3), A3031.state(p, "Vortex")))
    }

    @Test
    fun life2Neo() {
        val body = "02 00 05 00 " + repeat("78", 8) + " 30 31 2e 30 30 33 30 33 33 30 30 30 30 30 30 30 30 30 30 30 30 00"
        val state = A3033.state(A3033.parse(hex(body)), "Life 2 Neo")
        assertEquals(40, state.overall?.percent)
        assertEquals(5, state.equalizer?.presetId)
        assertEquals("01.00", state.firmware)
        assertEquals(mapOf(Toggle.WearDetection to false), state.toggles)
        assertNull(state.sound)
    }

    @Test
    fun liberty2Pro() {
        val p = sample(A3909)
        val state = A3909.state(p, "Liberty 2 Pro")
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals(true, state.right?.charging)
        assertEquals("01.28", state.firmware)
        assertEquals("390998523D8CBEAF", state.serial)
        // Only the preset's id is sent; the bands are Dance's.
        assertEquals(6, state.equalizer?.presetId)
        assertEquals(listOf(2.0, -3.0, -1.0, 1.0, 2.0, 2.0, 1.0, -3.0), state.equalizer?.left)
        val controls = state.controls!!
        assertEquals(4, controls.actions.size)
        assertEquals(TouchAction.PreviousSong, controls.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.VolumeDown, controls.actions[Ear.Left to Press.Long])
        assertEquals(TouchAction.NextSong, controls.actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.VolumeUp, controls.actions[Ear.Right to Press.Long])
        assertTrue(TouchAction.PlayPause !in controls.available)
        assertTrue(TouchAction.None !in controls.available)
    }

    @Test
    fun liberty2ProEqualizerInWholeDecibels() {
        val p = sample(A3909)
        val state = A3909.state(p, "Liberty 2 Pro")
        // Rock, both channels, a byte of 12 meaning flat.
        val rock = A3909.steps(p, Change.Preset(17), state)!!.single()
        assertEquals(EqSetup.SET_PLAIN, rock.packet.command)
        assertEquals("11 00 " + List(2) { "0f 0e 0b 0b 0d 0f 0f 0f" }.joinToString(" "), rock.packet.body.toHex())

        val bands = listOf(1.0, -2.4, 0.5, 12.0, -6.0, 0.0, 3.6, -0.5)
        val change = Change.Bands(bands)
        val custom = A3909.steps(p, change, state.with(change))!!.single()
        assertEquals("fe fe " + List(2) { "0d 0a 0d 18 06 0c 10 0b" }.joinToString(" "), custom.packet.body.toHex())
        custom.apply(p)
        val after = A3909.state(p, "Liberty 2 Pro")
        assertEquals(Equalizer.CUSTOM, after.equalizer?.presetId)
        assertEquals(listOf(1.0, -2.0, 1.0, 12.0, -6.0, 0.0, 4.0, -1.0), after.equalizer?.left)

        // A fresh state brings only the custom id back; the bands last sent are still shown.
        val fresh = A3909.parse(hex(SoundcoreSamples.STATE.getValue("A3909").replaceRange(18, 23, "fe fe")))
        assertEquals(listOf(1.0, -2.0, 1.0, 12.0, -6.0, 0.0, 4.0, -1.0), A3909.state(fresh, "x").equalizer?.left)
    }

    /** OpenSCQ30's example from its issue 264: the left earbud is away, so its level is unknown. */
    @Test
    fun liberty2ProWithOneEarbud() {
        val body = "01 00 ff 05 00 00 fe fe 01 01 01 " + repeat("0c", 3) + " 0f " + repeat("0c", 12) + " 69 8e a8 67 02 01 03 00 00"
        val state = A3909.state(A3909.parse(hex(body)), "Liberty 2 Pro")
        assertNull(state.left)
        assertEquals(100, state.right?.percent)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(TouchAction.PreviousSong, state.controls?.actions?.get(Ear.Left to Press.Double))
    }

    @Test
    fun liberty2ProTouchGoesOneAtATime() {
        val p = sample(A3909)
        val change = Change.Touch(Ear.Left, Press.Double, TouchAction.VoiceAssistant)
        val step = A3909.steps(p, change, A3909.state(p, "x"))!!.single()
        assertEquals(ButtonLayout.SET_ACTION, step.packet.command)
        assertEquals("00 00 05", step.packet.body.toHex())
        assertNull(A3909.steps(p, Change.Touch(Ear.Left, Press.Double, TouchAction.PlayPause), A3909.state(p, "x")))
    }

    /** OpenSCQ30's own test: from the default state, the left single tap set to play/pause. */
    @Test
    fun lifeDot2S() {
        val body = "00 00 00 00 00 00 00 00 " + repeat("78", 16) + " 00 00 00 " + repeat("78", 16) + " 00 00 00 00 " +
            repeat("01 00", 6)
        val p = A3926.parse(hex(body))
        val state = A3926.state(p, "Life Dot 2S")
        assertEquals(6, state.controls?.actions?.size)
        val change = Change.Touch(Ear.Left, Press.Single, TouchAction.PlayPause)
        val step = A3926.steps(p, change, state)!!.single()
        assertEquals(ButtonLayout.SET_ACTION, step.packet.command)
        assertEquals("00 02 06", step.packet.body.toHex())
        // With one earbud out the equalizer can't change; with both, it goes with the hearing profile.
        assertNull(A3926.steps(p, Change.Preset(1), state))
        p.both = true
        val eq = A3926.steps(p, Change.Preset(1), A3926.state(p, "x"))!!.single()
        assertEquals(EqSetup.SET_HEAR_ID, eq.packet.command)
        assertNotNull(A3926.steps(p, Change.ResetControls, state))
    }

    @Test
    fun liberty2ProPlus() {
        val p = sample(A3930)
        val state = A3930.state(p, "Liberty 2 Pro+")
        assertEquals(0, state.left?.percent)
        assertEquals("00.00", state.firmware)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(setOf(ListeningMode.Normal, ListeningMode.Transparency), state.sound?.options?.modes?.toSet())
        assertEquals(emptyList<CancelStyle>(), state.sound?.options?.cancelStyles)
        assertEquals(6, state.controls?.actions?.size)
        assertTrue(TouchAction.ListeningMode !in state.controls!!.available)
        assertTrue(state.toggles.isEmpty())

        val mode = Change.Mode(ListeningMode.Transparency)
        assertEquals(listOf("01 00 01 00"), A3930.steps(p, mode, state.with(mode))!!.map { it.packet.body.toHex() })

        // A touch that was off comes on with its new action, in two messages since these take one change at a time.
        val touch = Change.Touch(Ear.Right, Press.Double, TouchAction.NextSong)
        val steps = A3930.steps(p, touch, state)!!
        assertEquals(listOf(ButtonLayout.SET_ACTION, ButtonLayout.SET_ENABLED), steps.map { it.packet.command })
        assertEquals(listOf("01 00 03", "01 00 01"), steps.map { it.packet.body.toHex() })
    }

    @Test
    fun shortAnswersDontFit() {
        EARLIER_MODELS.forEach { model ->
            assertFailsWith<ShortMessage>(model.code) { model.parse(hex("01 00 00")) }
        }
    }
}
