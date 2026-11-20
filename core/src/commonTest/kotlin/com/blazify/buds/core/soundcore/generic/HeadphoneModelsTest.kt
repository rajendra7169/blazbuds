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
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.models.A3035
import com.blazify.buds.core.soundcore.generic.models.A3040
import com.blazify.buds.core.soundcore.generic.models.A3062
import com.blazify.buds.core.soundcore.generic.models.A3876
import com.blazify.buds.core.soundcore.generic.models.D1301
import com.blazify.buds.core.soundcore.generic.models.D1402
import com.blazify.buds.core.soundcore.generic.models.HEADPHONE_MODELS
import com.blazify.buds.core.soundcore.hex
import com.blazify.buds.core.soundcore.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The headphones and the models with families of their own, read from OpenSCQ30's samples, and their messages checked
 * byte for byte against what OpenSCQ30's own tests expect.
 */
class HeadphoneModelsTest {
    private fun sample(model: SoundcoreModel): Parsed = model.parse(hex(SoundcoreSamples.STATE.getValue(model.code)))

    private fun body(vararg values: Int) = values.joinToString(" ") { (it and 0xFF).toString(16).padStart(2, '0') }

    private fun ints(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun SoundcoreModel.write(p: Parsed, change: Change): List<Step> = steps(p, change, state(p, name).with(change))!!

    private fun List<Step>.bodies() = map { it.packet.body.toHex() }

    @Test
    fun everyModelReadsItsSample() {
        HEADPHONE_MODELS.forEach { model ->
            val state = model.state(sample(model), model.name)
            assertTrue(state.overall != null || state.left != null, "${model.code} has a battery")
            assertTrue(state.firmware != null, "${model.code} has a firmware version")
            assertEquals(SoundcoreSamples.NAMES.getValue(model.code).second, model.service, "${model.code}'s service")
        }
    }

    // Space One (A3035)

    /** OpenSCQ30's A3035 test packet with the hidden bands 9 and 10 at 250, to see they are set back to 0. */
    private val spaceOneWithHiddenBands = ints(
        5, 255, 48, 54, 46, 56, 55, 51, 48, 51, 53, 55, 48, 53, 48, 50, 56, 56, 65,
        57, 68, 70, 52, 0, 0, 120, 120, 120, 120, 120, 120, 120, 120, 250,
        250, 30, 255, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0,
        255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 4, 4, 7, 3, 0, 0x50,
        0, 0, 1, 5, 0, 1, 0, 0, 0, 49, 1, 0, 1, 0, 1, 2, 0, 90, 0, 1, 1, 0, 0, 0,
    )

    @Test
    fun spaceOneReadsItsSample() {
        val state = A3035.state(sample(A3035), "Space One")
        assertEquals(100, state.overall?.percent)
        assertEquals("06.87", state.firmware)
        assertEquals("30357050288A9DF4", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Manual, sound.cancelStyle)
        assertEquals(5, sound.manualLevel)
        assertEquals(true, sound.windReduction)
        assertEquals(setOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency), sound.cycle)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        assertEquals(0x7E7E, state.equalizer?.presets?.last()?.id)
        assertNull(state.controls)
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(true, state.toggles[Toggle.Ldac])
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(90, state.autoPowerOff?.minutes)
        assertEquals(false, state.volumeLimit?.enabled)
        assertEquals(90, state.volumeLimit?.decibels)
        assertEquals(false, state.multipoint?.enabled)
    }

    @Test
    fun spaceOnePicksAPresetAsOpenScq30Does() {
        val p = A3035.parse(spaceOneWithHiddenBands)
        val step = A3035.write(p, Change.Preset(1)).single()
        assertEquals(EqSetup.SET_HEAR_ID, step.packet.command)
        assertEquals(
            body(
                1, 0, 0, 0, 160, 130, 140, 140, 160, 160, 160, 140, 120, 0, 255, 255, 0,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 0, 125, 118, 123, 120, 124, 122, 124,
                121, 120, 0, 0,
            ),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun spaceOneSetsBandsByHandAsOpenScq30Does() {
        val p = A3035.parse(spaceOneWithHiddenBands)
        val step = A3035.write(p, Change.Bands(listOf(-6.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 6.0))).single()
        assertEquals(
            body(
                254, 254, 0, 0, 60, 120, 120, 120, 120, 120, 120, 180, 120, 120, 255, 255,
                0, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 0, 112, 124, 119, 121, 119, 121, 116,
                129, 120, 0, 0,
            ),
            step.packet.body.toHex(),
        )
        step.apply(p)
        assertEquals(Equalizer.CUSTOM, A3035.state(p, "x").equalizer?.presetId)
    }

    @Test
    fun spaceOneChangesTheTransparencyStrengthByWayOfTransparency() {
        // OpenSCQ30's test: setting the transparency strength to 3 while cancelling goes to transparency, sets it, and comes back.
        val now = sample(A3035).sound!!
        val target = now.copyOf().also { it[5] = 3 }
        assertEquals(
            listOf(body(1, 81, 1, 0, 1, 5), body(1, 81, 1, 0, 1, 3), body(0, 81, 0, 0, 1, 3)),
            A3035.sound!!.steps(now, target).map { ints(*it).toHex() },
        )
    }

    @Test
    fun spaceOneSwitchesToAdaptive() {
        val p = sample(A3035)
        val steps = A3035.write(p, Change.Cancelling(CancelStyle.Adaptive))
        assertEquals(listOf(body(0, 0x51, 0, 1, 1, 5)), steps.bodies())
        assertEquals(SoundScheme.SET_SOUND, steps.single().packet.command)
    }

    @Test
    fun spaceOneSwitchesWithItsOwnCommands() {
        val p = sample(A3035)
        assertEquals(Packet.command(0x01, 0xAF), A3035.write(p, Change.Switch(Toggle.LowBatteryPrompt, false)).single().packet.command)
        assertEquals(Packet.command(0x01, 0xAE), A3035.write(p, Change.Switch(Toggle.VoicePrompt, false)).single().packet.command)
        assertEquals(Packet.command(0x01, 0x81), A3035.write(p, Change.Switch(Toggle.WearDetection, false)).single().packet.command)
    }

    // Space One Pro (A3062)

    @Test
    fun spaceOneProReadsItsSample() {
        val state = A3062.state(sample(A3062), "Space One Pro")
        assertEquals(100, state.overall?.percent)
        assertEquals("03.37", state.firmware)
        assertEquals("3062DB212C13E97C", state.serial)
        assertEquals(ListeningMode.Transparency, state.sound?.mode)
        assertEquals(CancelStyle.Adaptive, state.sound?.cancelStyle)
        assertEquals(false, state.sound?.windReduction)
        assertEquals(5, state.equalizer?.presetId)
        assertEquals(listOf(-3.0, 2.0, 4.0, 4.0, 3.0, 2.0, 0.0, -2.0), state.equalizer?.left)
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(false, state.toggles[Toggle.Ldac])
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(60, state.autoPowerOff?.minutes)
    }

    @Test
    fun spaceOneProSendsTheEqualizerAsOpenScq30Does() {
        // OpenSCQ30's set_equalizer_matches_known_good_packet.
        val p = Parsed().apply {
            eq = EqState(0, listOf(IntArray(10)))
            extra["hearId"] = intArrayOf(0) + IntArray(9) { 120 } + 60 + intArrayOf(0, 0, 0, 0, 2) + IntArray(9) { 130 } + 60
            extra["genre"] = intArrayOf(0, 0)
        }
        val tenths = intArrayOf(60, 125, 60, 148, 113, 95, 144, 111, 120, 120).map { it - 120 }.toIntArray()
        val step = A3062.equalizer!!.steps(p, Equalizer.CUSTOM, tenths).single()
        assertEquals(EqSetup.SET_HEAR_ID, step.packet.command)
        assertEquals(
            body(
                254, 254, 0, 0, 60, 125, 60, 148, 113, 95, 144, 111, 120, 120, 0, 0, 0,
                120, 120, 120, 120, 120, 120, 120, 120, 120, 60, 0, 0, 0, 0, 2,
                130, 130, 130, 130, 130, 130, 130, 130, 130, 60,
                60, 125, 60, 148, 113, 95, 144, 111, 120, 120, 0,
            ),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun spaceOneProKeepsItsTransparencyByte() {
        val p = sample(A3062)
        val steps = A3062.write(p, Change.Mode(ListeningMode.Normal))
        assertEquals(listOf(body(2, 0x51, 1, 1, 0, 5)), steps.bodies())
    }

    // Space Q45 (A3040)

    @Test
    fun spaceQ45ReadsItsSample() {
        val p = sample(A3040)
        val state = A3040.state(p, "Space Q45")
        assertEquals(60, state.overall?.percent)
        assertEquals("02.34", state.firmware)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Adaptive, sound.cancelStyle)
        assertEquals(TransparencyStyle.Voice, sound.transparencyStyle)
        assertEquals(ListeningMode.entries.toSet(), sound.cycle)
        assertEquals(17, state.equalizer?.presetId)
        assertEquals(listOf(3.0, 2.0, -1.0, -1.0, 1.0, 3.0, 4.0, 5.0), state.equalizer?.left)
        assertEquals(false, state.toggles[Toggle.Ldac])
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(30, state.autoPowerOff?.minutes)
        assertEquals(listOf(0x11, 0), p.extra["genre"]?.toList())
    }

    @Test
    fun spaceQ45ChangesModeAsOpenScq30Does() {
        assertEquals(listOf(body(2, 80, 0, 1, 0, 5)), A3040.write(sample(A3040), Change.Mode(ListeningMode.Normal)).bodies())
    }

    @Test
    fun spaceQ45PicksAPresetAsOpenScq30Does() {
        val step = A3040.write(sample(A3040), Change.Preset(0)).single()
        assertEquals(EqSetup.SET_HEAR_ID, step.packet.command)
        assertEquals(
            body(
                0, 0, 17, 0, 120, 120, 120, 120, 120, 120, 120, 120, 120, 0, 120, 120, 120,
                120, 120, 120, 120, 120, 120, 0, 0, 1, 0, 143, 143, 128, 138, 150, 150,
                143, 143, 60, 60, 143, 143, 128, 138, 150, 150, 143, 143, 60, 60, 100, 152,
                140, 205, 0, 143, 143, 128, 138, 150, 150, 143, 143, 60, 60, 143, 143, 128,
                138, 150, 150, 143, 143, 60, 60, 120, 120, 120, 120, 120, 120, 120, 120,
                120, 0, 120, 120, 120, 120, 120, 120, 120, 120, 120, 0, 0,
            ),
            step.packet.body.toHex(),
        )
    }

    // Space 2 (D1402)

    /** OpenSCQ30's second Space 2 packet (firmware 01.59), which holds a real hearing profile. */
    private val space2WithHearingProfile = ints(
        4, 0, 48, 49, 46, 53, 57, 49, 52, 48, 50, 56, 52, 57, 68, 52, 66, 66, 48, 66, 69, 53, 65,
        0, 0, 120, 120, 120, 120, 120, 120, 120, 120, 120, 0, 30, 3, 0, 125, 135, 150, 150, 137,
        119, 109, 103, 60, 60, 255, 255, 255, 255, 1, 125, 125, 124, 125, 127, 126, 126, 121, 60,
        60, 0, 0, 6, 4, 15, 255, 3, 5, 0, 95, 255, 0, 0, 1, 49, 0, 0, 0, 1, 1, 0, 1, 1, 3, 1, 90,
        0, 0, 1, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255, 255,
    )

    @Test
    fun space2ReadsItsSamples() {
        val state = D1402.state(sample(D1402), "Space 2")
        assertEquals(30, state.overall?.percent)
        assertEquals("02.59", state.firmware)
        assertEquals("1402849D4B8706A8", state.serial)
        assertEquals(1, state.equalizer?.presetId)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertEquals(5, state.sound?.manualLevel)
        assertEquals(listOf(CancelStyle.Manual), state.sound?.options?.cancelStyles)
        assertEquals(30, state.autoPowerOff?.minutes)
        assertEquals(false, state.volumeLimit?.enabled)
        assertEquals(true, state.toggles[Toggle.Ldac])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(false, state.toggles[Toggle.Surround])
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(true, state.multipoint?.enabled)

        val other = D1402.state(D1402.parse(space2WithHearingProfile), "Space 2")
        assertEquals(50, other.overall?.percent)
        assertEquals(300, other.autoPowerOff?.minutes)
        assertEquals(true, other.volumeLimit?.enabled)
        assertEquals(false, other.toggles[Toggle.Ldac])
        assertEquals(false, other.toggles[Toggle.WearDetection])
    }

    @Test
    fun space2KeepsItsUnknownSoundBytes() {
        assertEquals(listOf(body(1, 0x5F, 255, 0, 0, 1)), D1402.write(sample(D1402), Change.Mode(ListeningMode.Transparency)).bodies())
        assertEquals(listOf(body(0, 0x3F, 255, 0, 0, 1)), D1402.write(sample(D1402), Change.Cancelling(CancelStyle.Manual, 3)).bodies())
    }

    @Test
    fun space2ChangesTheStrengthByWayOfCancelling() {
        val p = sample(D1402).also { it.sound!![0] = 1 }
        // From transparency, a new strength alone goes there and back; the app's cancelling change also goes to cancelling.
        val target = p.sound!!.copyOf().also { it[1] = 0x3F }
        assertEquals(
            listOf(body(0, 0x5F, 255, 0, 0, 1), body(0, 0x3F, 255, 0, 0, 1), body(1, 0x3F, 255, 0, 0, 1)),
            D1402.sound!!.steps(p.sound!!, target).map { ints(*it).toHex() },
        )
        assertEquals(
            listOf(body(0, 0x5F, 255, 0, 0, 1), body(0, 0x3F, 255, 0, 0, 1)),
            D1402.write(p, Change.Cancelling(CancelStyle.Manual, 3)).bodies(),
        )
    }

    @Test
    fun space2WritesAnAbsentHearingProfileAsItKeepsIt() {
        val step = D1402.write(sample(D1402), Change.Preset(9)).single()
        assertEquals(EqSetup.SET_HEAR_ID, step.packet.command)
        assertEquals(
            body(
                9, 0, 0, 0, 100, 100, 110, 120, 120, 120, 100, 100, 120, 0, 30, 255, 0,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 0,
                118, 119, 119, 120, 120, 121, 118, 118, 120, 0, 0,
            ),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun space2SendsItsHearingProfileBack() {
        val step = D1402.write(D1402.parse(space2WithHearingProfile), Change.Preset(9)).single()
        assertEquals(
            body(
                9, 0, 0, 0, 100, 100, 110, 120, 120, 120, 100, 100, 120, 0, 30, 3, 0,
                125, 135, 150, 150, 137, 119, 109, 103, 60, 60, 255, 255, 255, 255, 1,
                125, 125, 124, 125, 127, 126, 126, 121, 60, 60,
                118, 119, 119, 120, 120, 121, 118, 118, 120, 0, 0,
            ),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun space2LdacAnd3dSoundTurnEachOtherOff() {
        val p = sample(D1402)
        val surround = D1402.write(p, Change.Switch(Toggle.Surround, true)).single()
        assertEquals(SoundcoreModel.SURROUND, surround.packet.command)
        assertEquals(body(1), surround.packet.body.toHex())
        surround.apply(p)
        assertEquals(false, D1402.state(p, "x").toggles[Toggle.Ldac])
        val ldac = D1402.write(p, Change.Switch(Toggle.Ldac, true)).single()
        assertEquals(SoundcoreModel.LDAC_SET, ldac.packet.command)
        ldac.apply(p)
        assertEquals(false, D1402.state(p, "x").toggles[Toggle.Surround])
    }

    // V20i (A3876)

    @Test
    fun v20iReadsItsSample() {
        val state = A3876.state(sample(A3876), "V20i")
        assertEquals(60, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals("03.15", state.firmware)
        assertEquals("3876BE4A96276C14", state.serial)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        val controls = state.controls!!
        assertEquals(8, controls.actions.size)
        assertEquals(TouchAction.None, controls.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.PlayPause, controls.actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.NextSong, controls.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.VoiceAssistant, controls.actions[Ear.Right to Press.Triple])
        assertEquals(TouchAction.VolumeUp, controls.actions[Ear.Right to Press.Long])
        assertTrue(TouchAction.VoiceAssistant !in controls.choices(Press.Single))
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(false, state.toggles[Toggle.GamingMode])
        assertEquals(30, state.autoPowerOff?.minutes)
        assertEquals(true, state.multipoint?.enabled)
    }

    @Test
    fun v20iSendsOneSideOfTheEqualizerAsOpenScq30Does() {
        val step = A3876.write(sample(A3876), Change.Bands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 6.0))).single()
        assertEquals(EqSetup.SET_DRC, step.packet.command)
        assertEquals(
            body(254, 254, 120, 120, 120, 120, 120, 120, 120, 180, 120, 120, 120, 120, 120, 120, 120, 121, 116, 129, 120, 0),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun v20iSetsOneTouch() {
        val p = sample(A3876)
        val steps = A3876.write(p, Change.Touch(Ear.Left, Press.Long, TouchAction.PlayPause))
        // Action byte low half for both earbuds in, high half kept.
        assertEquals(listOf(body(0, 1, 0xF6)), steps.bodies())
    }

    // Sleep A30 (D1301)

    @Test
    fun sleepA30ReadsItsSample() {
        val state = D1301.state(sample(D1301), "Sleep A30")
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals("81.00", state.firmware)
        assertEquals("1301000000000000", state.serial)
        assertEquals(ListeningMode.Normal, state.sound?.mode)
        val controls = state.controls!!
        // The left double tap changes between the phone and the earbuds' own sounds, which the app doesn't show.
        assertEquals(3, controls.actions.size)
        assertEquals(TouchAction.PlayPause, controls.actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.VolumeUp, controls.actions[Ear.Left to Press.Triple])
        assertEquals(false, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(false, state.toggles[Toggle.TouchLock])
    }

    @Test
    fun sleepA30SwitchesCancellingOn() {
        val step = D1301.write(sample(D1301), Change.Mode(ListeningMode.NoiseCancelling)).single()
        assertEquals(Packet.command(0x06, 0x87), step.packet.command)
        assertEquals(body(1), step.packet.body.toHex())
    }
}
