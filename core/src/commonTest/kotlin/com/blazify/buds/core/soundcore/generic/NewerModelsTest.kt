package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.demo.with
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.model.Travel
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.models.A3936
import com.blazify.buds.core.soundcore.generic.models.A3952
import com.blazify.buds.core.soundcore.generic.models.A3953
import com.blazify.buds.core.soundcore.generic.models.A3954
import com.blazify.buds.core.soundcore.generic.models.A3955
import com.blazify.buds.core.soundcore.generic.models.A3957
import com.blazify.buds.core.soundcore.generic.models.A3959
import com.blazify.buds.core.soundcore.generic.models.A3968
import com.blazify.buds.core.soundcore.generic.models.D1202
import com.blazify.buds.core.soundcore.generic.models.D1202C
import com.blazify.buds.core.soundcore.generic.models.NEWER_MODELS
import com.blazify.buds.core.soundcore.hex
import com.blazify.buds.core.soundcore.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The newer Soundcore models, read from the samples OpenSCQ30's device faker carries, and their writes checked byte for
 * byte against what OpenSCQ30 sends (the expected bytes come from its own tests where it has them).
 */
class NewerModelsTest {
    private fun parsed(model: SoundcoreModel, sample: String = SoundcoreSamples.STATE.getValue(model.code)) = model.parse(hex(sample))

    private fun state(model: SoundcoreModel, p: Parsed) = model.state(p, model.name)

    /** Makes [change] on [p] the way the app does: from the state shown, with the change put in. */
    private fun write(model: SoundcoreModel, p: Parsed, change: Change): List<Step> {
        val shown: BudsState = state(model, p)
        return model.steps(p, change, shown.with(change)) ?: error("${model.code} can't make $change")
    }

    private fun bodies(steps: List<Step>) = steps.map { it.packet.body.toHex() }

    @Test
    fun everyModelReadsItsSample() {
        NEWER_MODELS.filter { it.code in SoundcoreSamples.STATE }.forEach { model ->
            val state = state(model, parsed(model))
            assertTrue(state.left != null || state.right != null, "${model.code} battery")
            assertTrue(state.sound != null, "${model.code} sound")
            assertTrue(state.equalizer != null, "${model.code} equalizer")
            assertTrue(state.controls != null, "${model.code} touches")
        }
        assertEquals(10, NEWER_MODELS.size)
        assertEquals("R60i NC", D1202C.name)
    }

    @Test
    fun spaceA40() {
        val state = state(A3936, parsed(A3936))
        assertEquals(100, state.left?.percent)
        assertEquals(60, state.right?.percent)
        assertEquals(80, state.case?.percent)
        assertEquals("04.19", state.firmware)
        assertEquals("3936a477583dda9e", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.Normal, sound.mode)
        assertEquals(CancelStyle.Adaptive, sound.cancelStyle)
        assertEquals(3, sound.manualLevel)
        assertEquals(3, sound.options.manualLevels)
        assertEquals(TransparencyStyle.Full, sound.transparencyStyle)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        val touches = state.controls!!
        assertEquals(6, touches.actions.size)
        assertEquals(TouchAction.None, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.PlayPause, touches.actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.GamingMode, touches.actions[Ear.Right to Press.Long])
        assertEquals(false, state.toggles[Toggle.TouchTone])
        assertEquals(false, state.toggles[Toggle.GamingMode])
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(30, state.autoPowerOff?.minutes)
    }

    @Test
    fun liberty3Pro() {
        val p = parsed(A3952)
        val state = state(A3952, p)
        assertEquals(80, state.left?.percent)
        assertEquals(80, state.case?.percent)
        assertEquals("04.96", state.firmware)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Manual, sound.cancelStyle)
        assertEquals(3, sound.manualLevel)
        assertTrue(sound.windReduction)
        assertEquals(TouchAction.VolumeDown, state.controls?.actions?.get(Ear.Left to Press.Single))
        assertEquals(TouchAction.ListeningMode, state.controls?.actions?.get(Ear.Left to Press.Double))
        assertEquals(true, state.toggles[Toggle.TouchTone])
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(false, state.toggles[Toggle.WearingTone])
        // Its own Acoustic preset.
        assertEquals(listOf(3.0, -1.0, 0.0, 0.0, 2.0, 1.0, 1.0, -1.0), state.equalizer?.presets?.first { it.id == 1 }?.gains)
        // A hearing profile and an age range: the equalizer goes with the profile switched off, as 03 87.
        val eq = write(A3952, p, Change.Preset(1)).single()
        assertEquals(EqSetup.SET_HEAR_ID, eq.packet.command)
    }

    @Test
    fun liberty3ProChangesModeInTheAppsOrder() {
        // From cancelling (manual) to transparency with voices: the mode first, then the kind of transparency.
        val p = parsed(A3952)
        val steps = write(A3952, p, Change.TransparencyKind(TransparencyStyle.Voice))
        assertEquals(listOf("01 30 00 00 01 01", "01 30 01 00 01 01"), bodies(steps))
        steps.forEach { it.apply(p) }
        assertEquals(TransparencyStyle.Voice, state(A3952, p).sound?.transparencyStyle)
    }

    @Test
    fun liberty4() {
        val p = parsed(A3953)
        val state = state(A3953, p)
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.case?.percent)
        assertEquals("03.23", state.firmware)
        assertEquals("395354C633CCEEE8", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.Normal, sound.mode)
        assertTrue(sound.windReduction)
        assertEquals(emptyList(), sound.options.cancelStyles)
        assertEquals(setOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency), sound.cycle)
        val touches = state.controls!!
        assertEquals(6, touches.actions.size)
        assertEquals(TouchAction.PlayPause, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.ListeningMode, touches.actions[Ear.Right to Press.Double])
        // The triple taps come after the long presses the app can't change.
        assertEquals(TouchAction.PreviousSong, touches.actions[Ear.Left to Press.Triple])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Right to Press.Triple])
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(false, state.toggles[Toggle.Ldac])
        assertEquals(true, state.toggles[Toggle.WearingTone])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(true, state.toggles[Toggle.VoicePrompt])
        assertEquals(false, state.multipoint?.enabled)
        assertEquals(30, state.autoPowerOff?.minutes)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        // Only the mode changes; the strengths and the wind bit stay.
        assertEquals(listOf("00 30 00 00 01 01"), bodies(write(A3953, p, Change.Mode(ListeningMode.NoiseCancelling))))
        // A triple tap, by its own id (5), on the right.
        val tap = write(A3953, p, Change.Touch(Ear.Right, Press.Triple, TouchAction.VolumeUp)).single()
        assertEquals(ButtonLayout.SET_ACTION, tap.packet.command)
        assertEquals("01 05 50", tap.packet.body.toHex())
    }

    @Test
    fun liberty4Pro() {
        val p = parsed(A3954)
        val state = state(A3954, p)
        assertEquals(99, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals(100, state.case?.percent)
        assertEquals("03.29", state.firmware)
        assertEquals(ListeningMode.Normal, state.sound?.mode)
        assertEquals(false, state.sound?.windReduction)
        val touches = state.controls!!
        assertEquals(8, touches.actions.size)
        assertEquals(TouchAction.PlayPause, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.PreviousSong, touches.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.None, touches.actions[Ear.Left to Press.Triple])
        assertEquals(TouchAction.ListeningMode, touches.actions[Ear.Right to Press.Long])
        assertEquals(true, state.volumeLimit?.enabled)
        assertEquals(95, state.volumeLimit?.decibels)
        assertEquals(false, state.toggles[Toggle.WearDetection])
        assertEquals(false, state.toggles[Toggle.SoundLeakCompensation])
        assertEquals(false, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(false, state.multipoint?.enabled)
        assertEquals(10, state.autoPowerOff?.minutes)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        // Cancelling starts its slider at the middle (5); the strength runs the other way on the slider.
        val toCancelling = write(A3954, p, Change.Mode(ListeningMode.NoiseCancelling))
        assertEquals(listOf("00 05 00 00"), bodies(toCancelling))
        toCancelling.forEach { it.apply(p) }
        assertEquals(1, state(A3954, p).sound?.manualLevel)
        assertEquals(listOf("00 01 00 00"), bodies(write(A3954, p, Change.Cancelling(CancelStyle.Manual, level = 5))))
    }

    @Test
    fun liberty4ProEqualizerAsOpenScq30SendsIt() {
        val p = parsed(A3954)
        val steps = write(A3954, p, Change.Bands(listOf(6.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -6.0)))
        val packet = steps.single().packet
        assertEquals(Packet.command(0x03, 0x87), packet.command)
        assertEquals(
            "fe fe 00 00 b4 78 78 78 78 78 78 3c 78 78 b4 78 78 78 78 78 78 3c 78 78 00 00 00 91 93 8b 8d 7a 6f 69 64 3c 3c " +
                "91 93 8b 8d 7a 6f 69 64 3c 3c 00 00 00 00 01 91 93 8b 82 7a 85 85 72 3c 3c 91 93 8b 82 7a 85 85 72 3c 3c " +
                "b4 00 78 00 78 00 78 00 78 00 78 00 78 00 3c 00 78 00 78 00 b4 00 78 00 78 00 78 00 78 00 78 00 78 00 3c 00 " +
                "78 00 78 00 00 00",
            packet.body.toHex(),
        )
    }

    @Test
    fun p40i() {
        val p = parsed(A3955)
        val state = state(A3955, p)
        assertEquals(80, state.left?.percent)
        assertEquals(40, state.right?.percent)
        assertEquals(20, state.case?.percent)
        assertEquals("01.67", state.firmware)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Outdoor, sound.cancelStyle)
        assertEquals(5, sound.manualLevel)
        assertTrue(sound.windReduction)
        assertEquals(5, sound.options.cancelStyles.size)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(6.0, state.equalizer?.left?.first())
        val touches = state.controls!!
        assertEquals(8, touches.actions.size)
        assertEquals(TouchAction.VolumeDown, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.None, touches.actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Right to Press.Long])
        assertEquals(true, state.toggles[Toggle.TouchTone])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(82, state.volumeLimit?.decibels)
        assertEquals(60, state.autoPowerOff?.minutes)
        // From a scene to manual: the kind first (cancelling is already on), then the strength is already 5.
        assertEquals(listOf("00 51 00 00 01 00 01"), bodies(write(A3955, p, Change.Cancelling(CancelStyle.Manual))))
    }

    @Test
    fun p40iEqualizerWithoutAHearingProfile() {
        // OpenSCQ30's test: the profile was never made (255), so it goes blank, with FF FF after the bands.
        val p = parsed(A3955)
        val steps = write(A3955, p, Change.Bands(listOf(6.0, -6.0, 6.0, -6.0, 6.0, -6.0, 6.0, -6.0)))
        assertEquals(EqSetup.SET_HEAR_ID, steps.single().packet.command)
        assertEquals(
            "fe fe 00 00 b4 3c b4 3c b4 3c b4 3c 78 78 b4 3c b4 3c b4 3c b4 3c 78 78 ff ff 00 ff ff ff ff ff ff ff ff ff 00 " +
                "ff ff ff ff ff ff ff ff ff 00 00 00 00 00 00 ff ff ff ff ff ff ff ff ff 00 ff ff ff ff ff ff ff ff ff 00 " +
                "85 64 8d 62 8e 63 8c 6a 78 00 85 64 8d 62 8e 63 8c 6a 78 00 00",
            steps.single().packet.body.toHex(),
        )
    }

    @Test
    fun p40iEqualizerWithAHearingProfile() {
        val sample = "00 01 04 02 00 00 30 31 2e 36 37 30 31 2e 36 37 33 39 35 35 39 38 34 37 34 34 36 36 65 66 65 33 30 2e 31 2e 38 01 " +
            "fe fe b4 98 8c 8c 78 85 88 8e 78 78 ff ff ff ff ff ff ff ff ff ff 00 01 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 " +
            "00 00 00 00 02 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 00 00 0a 11 ff 63 66 33 33 44 33 33 00 51 00 02 01 00 01 " +
            "00 00 00 00 ff ff 01 31 01 01 01 52 02 01 03 01 01 ff ff ff ff ff ff ff ff ff 19"
        val p = parsed(A3955, sample)
        val steps = write(A3955, p, Change.Bands(List(8) { -6.0 }))
        assertEquals(
            "fe fe 00 00 3c 3c 3c 3c 3c 3c 3c 3c 78 78 3c 3c 3c 3c 3c 3c 3c 3c 78 78 00 00 00 78 78 78 78 78 78 78 78 78 78 " +
                "78 78 78 78 78 78 78 78 78 78 00 00 00 00 02 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 " +
                "73 75 74 74 74 74 74 72 78 00 73 75 74 74 74 74 74 72 78 00 00",
            steps.single().packet.body.toHex(),
        )
    }

    @Test
    fun liberty5() {
        val p = parsed(A3957)
        val state = state(A3957, p)
        assertEquals(90, state.left?.percent)
        assertEquals(30, state.case?.percent)
        assertEquals("3957F49D8AC20334", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Manual, sound.cancelStyle)
        assertEquals(5, sound.manualLevel)
        assertEquals(Travel.Plane, sound.travel)
        assertEquals(listOf(CancelStyle.Manual, CancelStyle.Travel), sound.options.cancelStyles)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(TouchAction.PlayPause, state.controls?.actions?.get(Ear.Left to Press.Single))
        assertEquals(TouchAction.None, state.controls?.actions?.get(Ear.Right to Press.Triple))
        assertEquals(true, state.toggles[Toggle.WearDetection])
        assertEquals(true, state.toggles[Toggle.WearingTone])
        assertEquals(false, state.toggles[Toggle.Ldac])
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(false, state.volumeLimit?.enabled)
        assertEquals(90, state.volumeLimit?.decibels)
        // Gaming mode has its own command on the Liberty 5.
        val gaming = write(A3957, p, Change.Switch(Toggle.GamingMode, true)).single()
        assertEquals(Packet.command(0x10, 0x85), gaming.packet.command)
    }

    @Test
    fun liberty5GoesToTravelInTheAppsOrder() {
        // From normal to cancelling for a car: the mode, then the kind, then the vehicle, as OpenSCQ30's planner does.
        val p = parsed(A3957)
        p.sound = intArrayOf(2, 0x50, 0, 0, 0, 0, 0)
        val steps = write(A3957, p, Change.Cancelling(CancelStyle.Travel, travel = Travel.Car))
        assertEquals(listOf("00 50 00 00 00 00 00", "00 50 00 02 00 00 00", "00 50 00 02 00 00 03"), bodies(steps))
        steps.forEach { it.apply(p) }
        assertEquals(Travel.Car, state(A3957, p).sound?.travel)
        assertEquals(CancelStyle.Travel, state(A3957, p).sound?.cancelStyle)
        // Train and bus have no place on these earbuds: nothing is sent.
        assertEquals(emptyList(), write(A3957, p, Change.Cancelling(CancelStyle.Travel, travel = Travel.Train)))
    }

    @Test
    fun p30i() {
        val p = parsed(A3959)
        val state = state(A3959, p)
        assertEquals(0, state.left?.percent)
        assertEquals(90, state.right?.percent)
        assertNull(state.case)
        assertEquals("01.64", state.firmware)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Manual, sound.cancelStyle)
        assertEquals(5, sound.manualLevel)
        assertTrue(sound.windReduction)
        assertFalse(sound.options.transparencyStyles)
        val touches = state.controls!!
        assertEquals(TouchAction.VolumeDown, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.VolumeUp, touches.actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.PreviousSong, touches.actions[Ear.Left to Press.Triple])
        assertEquals(TouchAction.ListeningMode, touches.actions[Ear.Left to Press.Long])
        assertEquals(true, state.toggles[Toggle.TouchTone])
        assertEquals(false, state.toggles[Toggle.Surround])
        assertEquals(false, state.toggles[Toggle.GamingMode])
        assertEquals(10, state.autoPowerOff?.minutes)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
    }

    @Test
    fun p30iManualStrengthAsOpenScq30SendsIt() {
        val p = parsed(A3959)
        assertEquals(listOf("00 25 00 00 01 ff 01"), bodies(write(A3959, p, Change.Cancelling(CancelStyle.Manual, level = 2))))
    }

    @Test
    fun p30iEqualizerAsOpenScq30SendsIt() {
        val p = parsed(A3959)
        val steps = write(A3959, p, Change.Bands(listOf(-1.9, 0.0, 4.1, 5.1, 5.1, 3.2, 1.3, -3.5)))
        assertEquals(EqSetup.SET_DRC, steps.single().packet.command)
        assertEquals("fe fe 65 78 a1 ab ab 98 85 55 78 78 76 77 7c 7b 7c 79 7b 72 78 00", steps.single().packet.body.toHex())
    }

    @Test
    fun p30iWithoutGamingModeBeforeFirmware160() {
        val old = SoundcoreSamples.STATE.getValue("A3959").replace("30 31 2e 36 34 30 31 2e 36 34", "30 31 2e 35 39 30 31 2e 35 39")
        val p = parsed(A3959, old)
        assertEquals("01.59", state(A3959, p).firmware)
        assertFalse(Toggle.GamingMode in state(A3959, p).toggles)
        assertEquals(emptyList(), write(A3959, p, Change.Switch(Toggle.GamingMode, true)))
    }

    @Test
    fun sportX20() {
        val p = parsed(A3968)
        val state = state(A3968, p)
        assertEquals(100, state.left?.percent)
        assertEquals(40, state.case?.percent)
        assertEquals("3968b038e26bbe9a", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Manual, sound.cancelStyle)
        assertEquals(TransparencyStyle.Voice, sound.transparencyStyle)
        assertEquals(ListeningMode.entries.toSet(), sound.cycle)
        val touches = state.controls!!
        assertEquals(6, touches.actions.size)
        assertEquals(TouchAction.VolumeDown, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.VolumeUp, touches.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Right to Press.Double])
        assertEquals(true, state.toggles[Toggle.Surround])
        assertEquals(false, state.toggles[Toggle.TouchTone])
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(30, state.autoPowerOff?.minutes)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        // The last byte always goes as 0xFF.
        assertEquals(listOf("01 22 01 00 00 ff"), bodies(write(A3968, p, Change.Mode(ListeningMode.Transparency))))
        // No age range in its state: OpenSCQ30 treats it as 0, so the equalizer goes with the hearing profile (03 87).
        assertEquals(EqSetup.SET_HEAR_ID, write(A3968, p, Change.Preset(0)).single().packet.command)
    }

    @Test
    fun p31i() {
        val p = parsed(D1202)
        val state = state(D1202, p)
        assertEquals(100, state.left?.percent)
        assertEquals(40, state.case?.percent)
        assertEquals("12023409C9B403CE", state.serial)
        val sound = state.sound!!
        assertEquals(ListeningMode.NoiseCancelling, sound.mode)
        assertEquals(CancelStyle.Transport, sound.cancelStyle)
        assertEquals(5, sound.manualLevel)
        assertFalse(sound.windReduction)
        assertFalse(sound.environmentDetection)
        assertTrue(sound.options.environment)
        assertFalse(sound.options.cycle)
        val touches = state.controls!!
        assertEquals(TouchAction.None, touches.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.NextSong, touches.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.ListeningMode, touches.actions[Ear.Right to Press.Long])
        assertFalse(TouchAction.VoiceAssistant in touches.choices(Press.Single))
        assertTrue(TouchAction.VoiceAssistant in touches.choices(Press.Double))
        assertEquals(true, state.toggles[Toggle.TouchTone])
        assertEquals(true, state.toggles[Toggle.LowBatteryPrompt])
        assertEquals(false, state.toggles[Toggle.Ldac])
        assertEquals(false, state.volumeLimit?.enabled)
        assertEquals(30, state.autoPowerOff?.minutes)
        // Real-time adaptive cancelling, shown as environment detection; the sixth byte goes as 0.
        assertEquals(listOf("00 50 00 01 00 00 00 01"), bodies(write(D1202, p, Change.Cancelling(CancelStyle.Adaptive)).let { first ->
            first.forEach { it.apply(p) }
            write(D1202, p, Change.Environment(true))
        }))
    }

    @Test
    fun p31iPresetAsOpenScq30SendsIt() {
        val p = parsed(D1202)
        val steps = write(D1202, p, Change.Preset(1))
        assertEquals(
            "01 00 00 00 a0 82 8c 8c a0 a0 a0 8c 78 00 a0 82 8c 8c a0 a0 a0 8c 78 00 00 00 00 78 78 78 78 78 78 78 78 78 78 " +
                "78 78 78 78 78 78 78 78 78 78 00 00 00 00 02 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 78 " +
                "a0 00 82 00 8c 00 8c 00 a0 00 a0 00 a0 00 8c 00 78 00 00 00 a0 00 82 00 8c 00 8c 00 a0 00 a0 00 a0 00 8c 00 " +
                "78 00 00 00 00 00",
            steps.single().packet.body.toHex(),
        )
        steps.single().apply(p)
        assertEquals(1, state(D1202, p).equalizer?.presetId)
    }
}
