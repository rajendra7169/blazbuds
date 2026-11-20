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
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.models.A3330
import com.blazify.buds.core.soundcore.generic.models.A3388
import com.blazify.buds.core.soundcore.generic.models.A3931
import com.blazify.buds.core.soundcore.generic.models.A3933
import com.blazify.buds.core.soundcore.generic.models.A3939
import com.blazify.buds.core.soundcore.generic.models.A3944
import com.blazify.buds.core.soundcore.generic.models.A3945
import com.blazify.buds.core.soundcore.generic.models.A3948
import com.blazify.buds.core.soundcore.generic.models.A3949
import com.blazify.buds.core.soundcore.generic.models.CLASSIC_MODELS
import com.blazify.buds.core.soundcore.generic.models.D1101
import com.blazify.buds.core.soundcore.hex
import com.blazify.buds.core.soundcore.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Life Dot 2 NC, Life Note 3 family, Life P2 Mini, A20i, P20i, C30i, AeroClip and C50i, read from the samples and
 * checked against what OpenSCQ30's own tests expect, with a few writes checked byte for byte.
 */
class ClassicModelsTest {
    private fun sample(code: String): Parsed = model(code).parse(hex(SoundcoreSamples.STATE.getValue(code)))

    private fun model(code: String): SoundcoreModel = CLASSIC_MODELS.first { it.code == code }

    private fun SoundcoreModel.read(p: Parsed): BudsState = state(p, name)

    private fun SoundcoreModel.write(p: Parsed, change: Change): List<Step> = steps(p, change, read(p).with(change))!!

    private fun bytes(vararg values: Int) = values.joinToString(" ") { it.toString(16).padStart(2, '0') }

    private fun decimal(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val Step.command get() = Packet.commandName(packet.command)

    @Test
    fun everyModelIsListedOnceWithTheServiceItsSampleAnswersOn() {
        assertEquals(10, CLASSIC_MODELS.size)
        assertEquals(CLASSIC_MODELS.size, CLASSIC_MODELS.map { it.code }.distinct().size)
        CLASSIC_MODELS.forEach { model ->
            val (_, service) = SoundcoreSamples.NAMES.getValue(model.code)
            assertEquals(service, model.service, model.code)
        }
    }

    @Test
    fun lifeDot2NcReadsItsSample() {
        val p = sample("A3931").also { readFirmware(it, hex(SoundcoreSamples.FIRMWARE.getValue("A3931"))) }
        val state = A3931.read(p)
        assertEquals(0, state.left?.percent)
        assertEquals(0, state.right?.percent)
        assertEquals("00.00", state.firmware)
        assertEquals("0000000000000000", state.serial)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertEquals(CancelStyle.Transport, state.sound?.cancelStyle)
        assertEquals(TransparencyStyle.Voice, state.sound?.transparencyStyle)
        assertEquals(listOf(CancelStyle.Transport, CancelStyle.Outdoor, CancelStyle.Indoor), state.sound?.options?.cancelStyles)
        assertEquals(0, state.equalizer?.presetId)
        assertEquals(List(8) { 0.0 }, state.equalizer?.left)
        assertEquals(6, state.controls?.actions?.size)
        assertEquals(false, state.toggles[Toggle.TouchTone])
        assertEquals(false, state.autoPowerOff?.enabled)
        assertEquals(listOf(5, 10, 30, 60), state.autoPowerOff?.choices)
    }

    @Test
    fun lifeDot2NcSendsItsEqualizerWithTheLimiterCurve() {
        val p = sample("A3931")
        val step = A3931.write(p, Change.Preset(21)).single()
        assertEquals("02 83", step.command)
        val treble = bytes(120, 120, 120, 100, 90, 80, 80, 60)
        val curve = EqSetup.drcBytes(intArrayOf(0, 0, 0, -20, -30, -40, -40, -60), 8).toHex()
        assertEquals("15 00 $treble $treble $curve $curve", step.packet.body.toHex())
    }

    @Test
    fun lifeDot2NcSetsAutoPowerOff() {
        val p = sample("A3931")
        val step = A3931.write(p, Change.PowerOff(true, 30)).single()
        assertEquals("01 86", step.command)
        assertEquals("01 02", step.packet.body.toHex())
    }

    @Test
    fun lifeNote3AndLifeP3ReadTheirSamples() {
        for (code in listOf("A3933", "A3939")) {
            val model = model(code)
            val state = model.read(sample(code))
            assertEquals(0, state.left?.percent, code)
            // A case level of 0 shows as no case.
            assertNull(state.case, code)
            assertEquals("00.00", state.firmware, code)
            assertEquals("0000000000000000", state.serial, code)
            assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode, code)
            assertEquals(TransparencyStyle.Voice, state.sound?.transparencyStyle, code)
            assertEquals(ListeningMode.entries.toSet(), state.sound?.cycle, code)
            assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId, code)
            assertEquals(8, state.equalizer?.left?.size, code)
            assertEquals(6, state.controls?.actions?.size, code)
            assertEquals(mapOf(Toggle.TouchTone to false, Toggle.WearDetection to false, Toggle.GamingMode to false), state.toggles, code)
        }
        assertEquals(A3933.layout, A3939.layout)
    }

    @Test
    fun lifeNote3WithOlderFirmwareLeavesTheSwitchesOut() {
        val full = hex(SoundcoreSamples.STATE.getValue("A3933"))
        val p = A3933.parse(full.copyOf(full.size - 10))
        val state = A3933.read(p)
        assertEquals(ListeningMode.NoiseCancelling, state.sound?.mode)
        assertTrue(state.toggles.isEmpty())
    }

    @Test
    fun lifeNote3SendsTenBandsOnEachSide() {
        // OpenSCQ30's "it resets eq band 9 and 10 values": bands 9 and 10 go back to 0 and -12 dB.
        val p = sample("A3933")
        val step = A3933.write(p, Change.Preset(21)).single()
        assertEquals("02 81", step.command)
        val treble = bytes(120, 120, 120, 100, 90, 80, 80, 60, 120, 0)
        assertEquals("15 00 $treble $treble", step.packet.body.toHex())
    }

    @Test
    fun lifeNote3SChangesItsCycleAndSwitches() {
        val p = sample("A3933")
        val cycle = A3933.write(p, Change.ModeCycle(setOf(ListeningMode.NoiseCancelling, ListeningMode.Normal))).single()
        assertEquals("06 82", cycle.command)
        assertEquals("05", cycle.packet.body.toHex())
        val gaming = A3933.write(p, Change.Switch(Toggle.GamingMode, true)).single()
        assertEquals("01 87", gaming.command)
        assertEquals("01", gaming.packet.body.toHex())
    }

    @Test
    fun lifeP2MiniReadsItsSample() {
        val state = A3944.read(sample("A3944"))
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals("02.27", state.firmware)
        assertEquals("3944F54D18CABA2C", state.serial)
        assertNull(state.sound)
        val eq = state.equalizer!!
        assertEquals(0, eq.presetId)
        assertFalse(eq.custom)
        assertEquals(listOf(5.0, 5.0, 3.0, 1.0, 1.0, 2.0, -1.0, -2.0), eq.presets.first { it.id == 2 }.gains)
        val actions = state.controls!!.actions
        assertEquals(6, actions.size)
        assertEquals(TouchAction.VolumeUp, actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.VolumeUp, actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.PlayPause, actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.PreviousSong, actions[Ear.Left to Press.Long])
        assertEquals(TouchAction.NextSong, actions[Ear.Right to Press.Long])
        assertFalse(TouchAction.ListeningMode in state.controls!!.available)
        assertEquals(true, state.toggles[Toggle.TouchTone])
    }

    @Test
    fun lifeP2MiniSendsAcousticAsOpenScq30Does() {
        val step = A3944.write(sample("A3944"), Change.Preset(1)).single()
        assertEquals("02 83", step.command)
        assertEquals(
            bytes(1, 0, 160, 130, 140, 140, 160, 160, 160, 140, 120, 0, 125, 118, 123, 120, 124, 122, 124, 121, 120, 0),
            step.packet.body.toHex(),
        )
    }

    @Test
    fun lifeNote3SReadsItsSample() {
        val state = A3945.read(sample("A3945"))
        assertEquals(80, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertNull(state.sound)
        assertEquals(0, state.equalizer?.presetId)
        val actions = state.controls!!.actions
        assertEquals(TouchAction.VolumeDown, actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.VolumeUp, actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.PlayPause, actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.NextSong, actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.VoiceAssistant, actions[Ear.Left to Press.Long])
        assertEquals(TouchAction.PreviousSong, actions[Ear.Right to Press.Long])
        assertEquals(mapOf(Toggle.TouchTone to false, Toggle.WearDetection to false, Toggle.GamingMode to false), state.toggles)
    }

    @Test
    fun lifeNote3STurnsBothSingleTapsOffInOneMessage() {
        val p = sample("A3945")
        val step = A3945.write(p, Change.Touch(Ear.Left, Press.Single, TouchAction.None)).single()
        assertEquals("04 84", step.command)
        step.apply(p)
        val actions = A3945.read(p).controls!!.actions
        assertEquals(TouchAction.None, actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.None, actions[Ear.Right to Press.Single])
    }

    @Test
    fun a20iReadsItsSample() {
        val state = A3948.read(sample("A3948"))
        assertEquals(100, state.left?.percent)
        assertEquals("24.56", state.firmware)
        assertEquals("394819FF1DCABA2C", state.serial)
        assertEquals(2, state.equalizer?.presetId)
        assertEquals(listOf(4.0, 3.0, 1.0), state.equalizer?.left?.take(3))
        assertTrue(state.equalizer!!.custom)
        assertEquals(6, state.controls?.actions?.size)
        assertEquals(true, state.toggles[Toggle.TouchTone])
    }

    @Test
    fun a20iMovesTurnedOffTouchesBackOnAsOpenScq30Does() {
        // OpenSCQ30's "disabled buttons are moved to enabled state correctly".
        val p = A3948.parse(
            decimal(
                0, 1, 5, 5, 0, 0, 50, 52, 46, 53, 54, 50, 52, 46, 53, 54, 51, 57, 52, 56, 49, 57,
                70, 70, 49, 68, 67, 65, 66, 65, 50, 67, 2, 0, 160, 150, 130, 120, 120, 120, 120,
                120, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14,
                1, 0xF6, 0, 0xF0, 1, 0x6F, 1, 0x6F, 1, 0x31, 0, 0x3F,
                255, 255, 255, 97, 0, 1, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255,
            ),
        )
        val before = A3948.read(p).controls!!.actions
        assertEquals(TouchAction.VolumeUp, before[Ear.Right to Press.Single])
        assertEquals(TouchAction.None, before[Ear.Right to Press.Double])
        assertEquals(TouchAction.None, before[Ear.Right to Press.Long])

        val single = A3948.write(p, Change.Touch(Ear.Right, Press.Single, TouchAction.VolumeUp))
        assertEquals(listOf("04 83" to bytes(1, 2, 1)), single.map { it.command to it.packet.body.toHex() })
        val double = A3948.write(p, Change.Touch(Ear.Right, Press.Double, TouchAction.VolumeUp))
        assertEquals(listOf("04 81" to bytes(1, 0, 0x60)), double.map { it.command to it.packet.body.toHex() })
        val long = A3948.write(p, Change.Touch(Ear.Right, Press.Long, TouchAction.VolumeUp))
        assertEquals(
            listOf("04 81" to bytes(1, 1, 0x30), "04 83" to bytes(1, 1, 1)),
            long.map { it.command to it.packet.body.toHex() },
        )
    }

    @Test
    fun p20iReadsItsSample() {
        val state = A3949.read(sample("A3949"))
        assertEquals(60, state.left?.percent)
        assertEquals(80, state.right?.percent)
        assertEquals("14.43", state.firmware)
        assertEquals("3949B9AEC72C9C18", state.serial)
        assertFalse(state.equalizer!!.custom)
        assertEquals(0, state.equalizer?.presetId)
        val actions = state.controls!!.actions
        assertEquals(TouchAction.VolumeUp, actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.VolumeDown, actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.PreviousSong, actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.NextSong, actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.VoiceAssistant, actions[Ear.Left to Press.Long])
        assertEquals(TouchAction.PlayPause, actions[Ear.Right to Press.Long])
        assertEquals(mapOf(Toggle.GamingMode to false, Toggle.TouchTone to true), state.toggles)
        // Presets only: bands set by hand have nowhere to go.
        assertNull(A3949.steps(sample("A3949"), Change.Bands(List(8) { 1.0 }), state))
    }

    @Test
    fun p20iWithOnlyOneEarbudReadsThatSide() {
        // OpenSCQ30's "p20i packet matches soundcore app": the right earbud alone, the left one's level unknown.
        val p = A3949.parse(
            decimal(
                1, 0, 255, 4, 0, 0, 0, 0, 0, 0, 0, 49, 52, 46, 52, 51, 51, 57, 52, 57, 66,
                57, 65, 69, 67, 55, 50, 67, 57, 67, 49, 56, 0, 0, 120, 120, 120, 120, 120,
                120, 120, 120, 120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 1, 255, 1, 255, 1,
                245, 1, 102, 1, 242, 1, 51, 255, 255, 255, 97, 0, 1, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            ),
        )
        val state = A3949.read(p)
        assertNull(state.left)
        assertEquals(80, state.right?.percent)
        assertEquals("14.43", state.firmware)
        assertFalse(state.bothConnected)
        val actions = state.controls!!.actions
        assertEquals(TouchAction.None, actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.PlayPause, actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.NextSong, actions[Ear.Right to Press.Long])
    }

    @Test
    fun c30iReadsItsSample() {
        val p = sample("A3330")
        val state = A3330.read(p)
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals(40, state.case?.percent)
        assertEquals("04.27", state.firmware)
        assertEquals("333098474479f629", state.serial)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(listOf(1.1, 1.5, 1.6, 1.1, 0.4, 0.0, 0.0, 0.0), state.equalizer?.left)
        val actions = state.controls!!.actions
        assertEquals(6, actions.size)
        assertEquals(TouchAction.VolumeDown, actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.VolumeUp, actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.NextSong, actions[Ear.Left to Press.Triple])
        assertEquals(TouchAction.PlayPause, actions[Ear.Left to Press.Long])
        // The during-a-call group is read too: answer on double press, decline on long press.
        assertEquals(listOf(1, 1, 0, 0), p.buttons[1].map { it.action and 0x0F })
        assertEquals(mapOf(Toggle.TouchTone to true, Toggle.LowBatteryPrompt to true, Toggle.TouchLock to false), state.toggles)
        assertEquals(false, state.multipoint?.enabled)
    }

    @Test
    fun c30iSetsATouchAsOpenScq30Does() {
        val step = A3330.write(sample("A3330"), Change.Touch(Ear.Right, Press.Double, TouchAction.NextSong)).single()
        assertEquals("04 81", step.command)
        assertEquals(bytes(1, 0, 0x63), step.packet.body.toHex())
    }

    @Test
    fun aeroClipReadsItsSamples() {
        val state = A3388.read(sample("A3388"))
        assertEquals(100, state.left?.percent)
        assertEquals(100, state.right?.percent)
        assertEquals(60, state.case?.percent)
        assertEquals("01.49", state.firmware)
        val actions = state.controls!!.actions
        assertEquals(4, actions.size)
        assertEquals(TouchAction.PlayPause, actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.NextSong, actions[Ear.Right to Press.Triple])
        assertEquals(true, state.multipoint?.enabled)

        // OpenSCQ30's real AeroClip on firmware 01.49.
        val real = A3388.read(A3388.parse(realAeroClip))
        assertEquals(90, real.left?.percent)
        assertEquals(80, real.case?.percent)
        assertEquals(5, real.equalizer?.presetId)
        assertEquals("3388880E8550BDE4", real.serial)
        assertEquals(mapOf(Toggle.TouchTone to true, Toggle.LowBatteryPrompt to true, Toggle.TouchLock to false), real.toggles)
        assertEquals(TouchAction.PreviousSong, real.controls!!.actions[Ear.Left to Press.Triple])
    }

    @Test
    fun aeroClipSendsTheSameBandsForEachSide() {
        // OpenSCQ30's "equalizer preset sets both channels".
        val p = A3388.parse(realAeroClip)
        val step = A3388.write(p, Change.Preset(2)).single()
        assertEquals("02 83", step.command)
        val bass = bytes(160, 150, 130, 120, 120, 120, 120, 120, 120, 0)
        assertEquals("02 00 $bass $bass", step.packet.body.toHex())
        step.apply(p)
        assertEquals(2, A3388.read(p).equalizer?.presetId)
    }

    @Test
    fun aeroClipOutOfTheCaseHasNoCaseLevel() {
        val p = A3388.parse(
            decimal(
                1, 1, 7, 7, 48, 49, 46, 52, 57, 48, 49, 46, 52, 57, 51, 51, 56, 56, 56, 56,
                48, 69, 56, 53, 57, 51, 53, 66, 66, 56, 232, 252, 16, 15, 172, 255, 7, 102,
                102, 51, 51, 85, 85, 49, 0, 1, 1, 1, 0, 1, 30, 0, 140, 150, 160, 170, 180,
                180, 170, 160, 120, 0, 1, 0, 0, 255,
            ),
        )
        val state = A3388.read(p)
        assertEquals(80, state.left?.percent)
        assertNull(state.case)
    }

    @Test
    fun c50iReadsItsSample() {
        val state = D1101.read(sample("D1101"))
        assertEquals(100, state.left?.percent)
        assertEquals(10, state.right?.percent)
        assertEquals("01.28", state.firmware)
        assertEquals("1101F68EEDC90934", state.serial)
        assertEquals(Equalizer.CUSTOM, state.equalizer?.presetId)
        assertEquals(4.0, state.equalizer?.left?.first())
        val controls = state.controls!!
        assertEquals(8, controls.actions.size)
        assertEquals(TouchAction.PlayPause, controls.actions[Ear.Left to Press.Single])
        assertEquals(TouchAction.PreviousSong, controls.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.None, controls.actions[Ear.Left to Press.Triple])
        assertEquals(TouchAction.VolumeDown, controls.actions[Ear.Left to Press.Long])
        assertEquals(TouchAction.PlayPause, controls.actions[Ear.Right to Press.Single])
        assertEquals(TouchAction.NextSong, controls.actions[Ear.Right to Press.Double])
        assertEquals(TouchAction.None, controls.actions[Ear.Right to Press.Triple])
        assertEquals(TouchAction.VolumeUp, controls.actions[Ear.Right to Press.Long])
        assertEquals(listOf(TouchAction.VolumeUp, TouchAction.VolumeDown, TouchAction.VoiceAssistant, TouchAction.None), controls.choices(Press.Long))
        assertEquals(true, state.multipoint?.enabled)
        assertEquals(mapOf(Toggle.LowBatteryPrompt to true, Toggle.TouchLock to false, Toggle.Ldac to false), state.toggles)
        assertEquals(true, state.autoPowerOff?.enabled)
        assertEquals(30, state.autoPowerOff?.minutes)
    }

    @Test
    fun c50iTurnsTouchesOffAndSendsItsEqualizerForEachSide() {
        // OpenSCQ30's "buttons enabled is inverted": turning the buttons off sends 1.
        val p = sample("D1101")
        val lock = D1101.write(p, Change.Switch(Toggle.TouchLock, true)).single()
        assertEquals("10 94" to "01", lock.command to lock.packet.body.toHex())

        val preset = D1101.write(p, Change.Preset(30)).single()
        assertEquals("02 81", preset.command)
        val boost = bytes(140, 150, 160, 170, 180, 180, 170, 160, 120, 0)
        assertEquals("1e 00 $boost $boost", preset.packet.body.toHex())

        val custom = D1101.write(p, Change.Bands(List(8) { 1.0 })).single()
        val ones = bytes(130, 130, 130, 130, 130, 130, 130, 130, 120, 120)
        assertEquals("fe fe $ones $ones", custom.packet.body.toHex())
        custom.apply(p)
        assertEquals(Equalizer.CUSTOM, D1101.read(p).equalizer?.presetId)
    }

    private val realAeroClip = decimal(
        0, 1, 8, 8, 48, 49, 46, 52, 57, 48, 49, 46, 52, 57, 51, 51, 56, 56, 56, 56, 48, 69,
        56, 53, 53, 48, 66, 68, 69, 52, 196, 14, 56, 249, 212, 7, 7, 102, 102, 50, 51, 85,
        85, 49, 0, 1, 1, 1, 0, 1, 5, 0, 90, 140, 160, 160, 150, 140, 120, 100, 0, 0, 1, 0,
        0, 255,
    )
}
