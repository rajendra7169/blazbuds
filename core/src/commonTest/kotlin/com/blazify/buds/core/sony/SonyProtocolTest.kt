package com.blazify.buds.core.sony

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The command set, against real messages: the WF-1000XM5's answers from a user's device report in PairFree
 * (data/sony/reports/WF-1000XM5_6.1.0.txt), first answers from Gadgetbridge's notes, and the set commands Gadgetbridge's
 * tests check for set one and set two.
 */
class SonyProtocolTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray?.text() = this?.let { SonyFrame.hex(it) }

    private val xm5Functions = hex(
        "07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 50 11 6b 08 70 01 90 ff " +
            "a1 12 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b 94 ff 40 2e 42 3a 44 ff ff 2d 19 ff f3 26 e7 1a d2 20 " +
            "fd 2b b6 1d b0 42 b2 43 b8 1f b7 15 34 3f f4 23 f5 24",
    )

    @Test
    fun theFirstAnswerSaysWhichSet() {
        assertEquals(SonySet.One, Sony.setOf(hex("01 00 40 10"))) // WH-1000XM3
        assertEquals(SonySet.One, Sony.setOf(hex("01 00 70 00"))) // WF-SP800N
        assertEquals(SonySet.Two, Sony.setOf(hex("01 00 03 00 00 00 00 00"))) // WH-1000XM5
        assertEquals(SonySet.Two, Sony.setOf(hex("01 00 03 00 30 18 00 00"))) // WF-1000XM5
        assertNull(Sony.setOf(hex("05 01 00")))
    }

    @Test
    fun modelAndFirmware() {
        assertEquals(1 to "WF-1000XM5", Sony.text(hex("05 01 0a 57 46 2d 31 30 30 30 58 4d 35")))
        assertEquals(2 to "6.1.0", Sony.text(hex("05 02 05 36 2e 31 2e 30")))
    }

    @Test
    fun theXm5sFunctionsGiveWhatToAskFor() {
        val functions = Sony.functions(SonySet.Two, xm5Functions)
        assertEquals(46, functions.size)
        val f = SonyFeatures.from(SonySet.Two, functions)
        assertEquals(listOf(0x09, 0x0A), f.batteries)
        assertEquals(0x17, f.noise)
        assertEquals(0x00, f.eq)
        assertTrue(f.eqCustom)
        assertTrue(f.upscaling)
        assertTrue(f.wearPause)
        assertEquals(0x0C, f.speakToChat)
        assertEquals(0x05, f.autoOff)
        assertEquals(0x03, f.keys)
    }

    @Test
    fun setOneListsOneByteFunctions() {
        val f = SonyFeatures.from(SonySet.One, Sony.functions(SonySet.One, hex("07 00 08 11 51 62 e2 f3 f4 f5 f6")))
        assertEquals(listOf(0x00), f.batteries)
        assertEquals(0x02, f.noise)
        assertEquals(0x01, f.eq)
        assertEquals(0x05, f.speakToChat)
        assertEquals(0x04, f.autoOff)
        assertEquals(0x06, f.keys)
    }

    @Test
    fun batteries() {
        // The left earbud away (0), the right full.
        val pair = Sony.batteries(hex("23 09 00 00 64 00 64 64"))!!
        assertNull(pair.left)
        assertEquals(Battery(100), pair.right)
        assertEquals(Battery(40), Sony.batteries(hex("23 0a 28 00 1e"))!!.case)
        assertEquals(Battery(70, charging = true), Sony.batteries(hex("11 00 46 01"))!!.overall)
        assertEquals(Battery(55, charging = true), Sony.batteries(hex("13 01 50 00 37 01"))!!.right)
        assertTrue(Sony.isBattery(SonySet.One, 0x13) && Sony.isBattery(SonySet.Two, 0x25) && !Sony.isBattery(SonySet.Two, 0x13))
    }

    @Test
    fun setTwoNoiseControl() {
        val ambient = SonyNoise(SonySet.Two, hex("67 17 01 01 01 00 14"))
        assertEquals(ListeningMode.Transparency, ambient.mode)
        assertEquals(false, ambient.voice)
        val news = SonyNoise(SonySet.Two, hex("69 17 01 01 00 01 0a"))
        assertEquals(ListeningMode.NoiseCancelling, news.mode)
        assertEquals(true, news.voice)
        // Gadgetbridge's layout for 17: 68 17, changed, on, ambient, voice, level.
        assertEquals("68 17 01 01 00 00 14", ambient.set(ListeningMode.NoiseCancelling).text())
        assertEquals("68 17 01 00 01 00 14", ambient.set(ListeningMode.Normal).text())
        assertEquals("68 17 01 01 01 01 14", ambient.set(voice = true).text())
        // The WF-1000XM4's longer 17, with the noise cancelling value written as dual.
        assertEquals("68 17 01 01 00 02 00 14", SonyNoise(SonySet.Two, hex("67 17 01 00 00 00 00 14")).set(ListeningMode.NoiseCancelling).text())
        // Ambient sound only.
        val only = SonyNoise(SonySet.Two, hex("67 22 01 01 00 0a"))
        assertEquals(listOf(ListeningMode.Transparency, ListeningMode.Normal), only.modes)
        assertNull(only.set(ListeningMode.NoiseCancelling))
        assertEquals("68 22 01 00 00 0a", only.set(ListeningMode.Normal).text())
    }

    @Test
    fun setTwoAmbientLevelSitsAfterTheVoiceByteInEveryLayout() {
        // 17 as Gadgetbridge writes it: changed, on, ambient, voice, level.
        val short = SonyNoise(SonySet.Two, hex("67 17 01 01 01 00 14"))
        assertTrue(short.hasLevel)
        assertEquals(20, short.level)
        assertEquals("68 17 01 01 01 00 05", short.set(level = 5).text())
        assertEquals("68 17 01 01 01 01 08", short.set(voice = true, level = 8).text())
        // The longer 17 with the noise cancelling value before the voice byte.
        val long = SonyNoise(SonySet.Two, hex("67 17 01 01 01 02 00 0a"))
        assertEquals(10, long.level)
        assertEquals("68 17 01 01 01 02 00 03", long.set(level = 3).text())
        // 15: mode, noise cancelling value, voice, level (libmdr's ModeNcModeSwitchAsmSeamless).
        assertEquals(7, SonyNoise(SonySet.Two, hex("67 15 01 01 01 00 00 07")).level)
        // 19 adds automatic ambient sound and its sensitivity after the level.
        val adaptive = SonyNoise(SonySet.Two, hex("67 19 01 01 01 00 0c 01 00"))
        assertEquals(12, adaptive.level)
        assertEquals("68 19 01 01 01 00 04 01 00", adaptive.set(level = 4).text())
        // Ambient sound only.
        val only = SonyNoise(SonySet.Two, hex("67 22 01 01 00 0a"))
        assertEquals(10, only.level)
        assertEquals("68 22 01 01 00 14", only.set(level = 20).text())
        // A level is never written below 1.
        assertEquals("68 22 01 01 00 01", only.set(level = 0).text())
    }

    @Test
    fun setOneAmbientLevelFollowsLibmdrAndGadgetbridge() {
        // Noise cancelling and ambient sound: ambient on, level adjustment, normal, level 10.
        val ambient = SonyNoise(SonySet.One, hex("67 02 01 02 00 01 00 0a"))
        assertEquals(ListeningMode.Transparency, ambient.mode)
        assertTrue(ambient.hasLevel)
        assertEquals(10, ambient.level)
        assertEquals("68 02 11 02 00 01 00 0f", ambient.set(level = 15).text())
        // Cancelling sends level 0, as both do.
        assertEquals("68 02 11 02 02 01 00 00", ambient.set(ListeningMode.NoiseCancelling).text())
        // Back into ambient sound with the level the driver kept.
        val cancelling = SonyNoise(SonySet.One, hex("67 02 01 02 02 01 00 00"))
        assertEquals(0, cancelling.level)
        assertEquals("68 02 11 02 00 01 00 14", cancelling.set(ListeningMode.Transparency, level = 20).text())
        // An ambient sound that is only on or off has no level, and writing one marks it as level adjustment.
        val plain = SonyNoise(SonySet.One, hex("67 03 01 00 00 01"))
        assertFalse(plain.hasLevel)
        assertEquals("68 03 11 01 00 05", plain.set(level = 5).text())
        // Ambient sound only, focus on voice, level 10.
        val only = SonyNoise(SonySet.One, hex("67 03 01 01 01 0a"))
        assertEquals(true, only.voice)
        assertEquals(10, only.level)
        assertEquals("68 03 11 01 00 02", only.set(voice = false, level = 2).text())
        // Only noise cancelling: no voice byte and no level.
        assertNull(SonyNoise(SonySet.One, hex("67 01 00 01")).level)
    }

    @Test
    fun setOneTellsTheAmbientLevelSteps() {
        // The WH-1000XM3's layout in libmdr: dual and single cancelling, then level adjustment with 20 steps for
        // normal and for voice.
        assertEquals(20, Sony.ambientSteps(hex("61 02 02 01 01 02 00 14 01 14")))
        assertEquals(10, Sony.ambientSteps(hex("61 03 01 02 01 0a 00 0a")))
        assertEquals(0, Sony.ambientSteps(hex("61 03 00 00")))
        assertEquals(20, Sony.ambientSteps(hex("61 03 01")))
        assertNull(Sony.ambientSteps(hex("51 00 06")))
        assertEquals("60 02", Sony.noiseCapability(0x02).text())
    }

    @Test
    fun priorityOnSoundQuality() {
        // Set one: function E1, E7 01 00 value, as Gadgetbridge writes it; 0 is sound quality.
        assertEquals(0x01, SonyFeatures.from(SonySet.One, setOf(0xE1)).soundQuality)
        assertEquals("e6 01", Sony.audio(0x01).text())
        assertEquals(true, Sony.soundQualityOn(SonySet.One, 0x01, hex("e7 01 00 00")))
        assertEquals(false, Sony.soundQualityOn(SonySet.One, 0x01, hex("e9 01 00 01")))
        assertNull(Sony.soundQualityOn(SonySet.One, 0x01, hex("e7 02 00 01")))
        assertEquals("e8 01 00 00", Sony.setSoundQuality(SonySet.One, 0x01, true).text())
        // Set two: E3 (type 2, with the LDAC status) over E1 (type 0); low latency (2) is not sound quality.
        assertEquals(0x02, SonyFeatures.from(SonySet.Two, setOf(0xE1, 0xE3)).soundQuality)
        assertEquals(0x00, SonyFeatures.from(SonySet.Two, setOf(0xE1)).soundQuality)
        assertNull(SonyFeatures.from(SonySet.Two, setOf(0xE2, 0xE7)).soundQuality)
        assertEquals(true, Sony.soundQualityOn(SonySet.Two, 0x02, hex("e7 02 00")))
        assertEquals(false, Sony.soundQualityOn(SonySet.Two, 0x02, hex("e7 02 02")))
        // Upscaling's answer (type 1) is not taken for it.
        assertNull(Sony.soundQualityOn(SonySet.Two, 0x02, hex("e7 01 00")))
        assertEquals("e8 02 01", Sony.setSoundQuality(SonySet.Two, 0x02, false).text())
    }

    @Test
    fun voiceGuidance() {
        // The XM5's first answer ends with both tables enabled (0); a set one answer has no such byte.
        assertTrue(Sony.hasTable2(hex("01 00 03 00 30 18 00 00")))
        assertFalse(Sony.hasTable2(hex("01 00 03 00 30 18 00 01")))
        assertFalse(Sony.hasTable2(hex("01 00 40 10")))
        // Table two's functions from the XM5's report: 42 is voice guidance with languages and volume, type 1.
        val table2 = Sony.functions(SonySet.Two, hex("07 00 0b 42 3d 53 ff 54 ff f1 ff 60 ff 61 ff f2 ff 63 ff f8 30 32 04 31 ff"))
        assertEquals(0x01, Sony.voiceGuidanceType(table2))
        assertEquals(0x00, Sony.voiceGuidanceType(setOf(0x40)))
        assertEquals(0x02, Sony.voiceGuidanceType(setOf(0x44)))
        assertEquals(0x03, Sony.voiceGuidanceType(setOf(0x45)))
        assertNull(Sony.voiceGuidanceType(setOf(0x53, 0x54)))
        // Set two: 0 is on; the XM5 answered 47 01 00 01, on, in English.
        assertEquals("46 01", Sony.voiceGuidance(SonySet.Two, 0x01).text())
        assertEquals(true, Sony.voiceGuidanceOn(SonySet.Two, 0x01, hex("47 01 00 01")))
        assertEquals(false, Sony.voiceGuidanceOn(SonySet.Two, 0x01, hex("49 01 01 01")))
        assertNull(Sony.voiceGuidanceOn(SonySet.Two, 0x00, hex("47 01 00 01")))
        assertEquals(0x01, Sony.voiceLanguage(0x01, hex("47 01 00 01")))
        assertNull(Sony.voiceLanguage(0x03, hex("47 03 00")))
        assertEquals("48 01 01", Sony.setVoiceGuidance(SonySet.Two, 0x01, false, 0x01).text())
        assertEquals("48 02 00 03", Sony.setVoiceGuidance(SonySet.Two, 0x02, true, 0x03).text())
        assertEquals("48 03 00", Sony.setVoiceGuidance(SonySet.Two, 0x03, true).text())
        // Set one, as Gadgetbridge writes it: 46 01 01, 1 on.
        assertEquals("46 01 01", Sony.voiceGuidance(SonySet.One, 0x01).text())
        assertEquals(true, Sony.voiceGuidanceOn(SonySet.One, 0x01, hex("47 01 01 01")))
        assertEquals("48 01 01 00", Sony.setVoiceGuidance(SonySet.One, 0x01, false).text())
        assertTrue(SonyFeatures.from(SonySet.One, setOf(0x62, 0x39)).voiceGuidance)
        assertFalse(SonyFeatures.from(SonySet.Two, setOf(0x39)).voiceGuidance)
    }

    @Test
    fun setOneNoiseControlMatchesGadgetbridge() {
        val cancelling = SonyNoise(SonySet.One, hex("67 02 01 00 01 01 00 00"))
        assertEquals(ListeningMode.NoiseCancelling, cancelling.mode)
        assertEquals("68 02 11 00 00 01 00 00", cancelling.set(ListeningMode.Transparency).text())
        assertEquals("68 02 00 00 00 01 00 00", cancelling.set(ListeningMode.Normal).text())
        val ambient = SonyNoise(SonySet.One, hex("67 02 01 00 00 01 00 00"))
        assertEquals(ListeningMode.Transparency, ambient.mode)
        assertEquals("68 02 11 00 01 01 00 00", ambient.set(ListeningMode.NoiseCancelling).text())
        // With the dual and single setting, noise cancelling is written as dual (2).
        assertEquals("68 02 11 02 02 01 00 00", SonyNoise(SonySet.One, hex("67 02 00 02 00 01 00 00")).set(ListeningMode.NoiseCancelling).text())
        assertEquals("68 02 11 02 00 01 01 0a", SonyNoise(SonySet.One, hex("67 02 01 02 02 01 00 0a")).set(ListeningMode.Transparency, voice = true).text())
    }

    @Test
    fun theXm5sEqualizer() {
        val eq = SonyEq(SonySet.Two, 0x00, customizable = true)
        assertTrue(eq.read(hex("51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00")))
        assertTrue(eq.read(hex("5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80")))
        assertTrue(eq.read(hex("57 00 a2 06 0e 0a 0a 0a 0a 0a")))
        val shown = eq.equalizer()!!
        assertEquals(Equalizer.CUSTOM, shown.presetId)
        assertEquals(listOf("Off", "Bright", "Excited", "Mellow", "Relaxed", "Vocal", "Treble boost", "Bass boost", "Speech"), shown.presets.map { it.name })
        assertEquals(listOf(400, 1000, 2500, 6300, 16000), shown.frequencies)
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0), shown.left)
        assertTrue(shown.custom)
        // The curve keeps the user setting it was in and Clear Bass as it was.
        assertEquals("58 00 a2 06 0e 0f 0b 0a 0e 0a", eq.curve(listOf(5.0, 1.0, 0.0, 4.0, 0.0)).text())
        assertEquals("58 00 10 00", eq.choose(0x10).text())
        assertTrue(!eq.read(hex("57 02 10 00")))
    }

    @Test
    fun setOneEqualizerMatchesGadgetbridge() {
        val eq = SonyEq(SonySet.One, 0x01, customizable = true)
        eq.read(hex("57 01 10 06 0a 0a 0a 0a 0a 0a"))
        assertEquals(0x10, eq.equalizer()!!.presetId)
        assertEquals("58 01 10 00", eq.choose(0x10).text())
        assertEquals("58 01 ff 06 0a 0f 0b 0a 0e 0a", eq.curve(listOf(5.0, 1.0, 0.0, 4.0, 0.0)).text())
        assertEquals("58 01 ff 06 0a 07 03 0a 0c 10", eq.curve(listOf(-3.0, -7.0, 0.0, 2.0, 6.0)).text())
    }

    @Test
    fun switches() {
        assertEquals("f8 03 00 01", Sony.setWearPause(SonySet.One, true).text())
        assertEquals("e8 02 00 01", Sony.setUpscaling(SonySet.One, true).text())
        assertEquals("f8 05 01 01", Sony.setSpeakToChat(SonySet.One, 0x05, true).text())
        assertEquals("f8 01 00", Sony.setWearPause(SonySet.Two, true).text())
        assertEquals("e8 01 01", Sony.setUpscaling(SonySet.Two, true).text())
        assertEquals("f8 0c 00 01", Sony.setSpeakToChat(SonySet.Two, 0x0C, true).text())
        // The WF-1000XM5 with all three off; set two writes on as 0.
        assertEquals(false, Sony.wearPauseOn(SonySet.Two, hex("f7 01 01")))
        assertEquals(false, Sony.speakToChatOn(SonySet.Two, 0x0C, hex("f7 0c 01 01")))
        assertEquals(false, Sony.upscalingOn(SonySet.Two, hex("e7 01 00")))
        assertEquals(true, Sony.wearPauseOn(SonySet.One, hex("f7 03 00 01")))
        assertNull(Sony.upscalingOn(SonySet.Two, hex("e7 00 00")))
    }

    @Test
    fun automaticPowerOff() {
        assertEquals("f8 04 01 01 01", Sony.setAutoOff(SonySet.One, 0x04, 0x01, 0x00).text())
        assertEquals("f8 04 01 11 00", Sony.setAutoOff(SonySet.One, 0x04, Sony.OFF_NEVER, 0x00).text())
        assertEquals("f8 04 01 10 00", Sony.setAutoOff(SonySet.One, 0x04, Sony.OFF_WHEN_TAKEN_OFF, 0x00).text())
        assertEquals(listOf(0x10, 0x11), Sony.autoOffChoices(SonySet.Two, 0x05, hex("21 05 02 10 11")))
        assertEquals(0x10 to 0x00, Sony.autoOffSetting(SonySet.Two, 0x05, hex("27 05 10 00")))
        assertEquals("28 05 11 00", Sony.setAutoOff(SonySet.Two, 0x05, Sony.OFF_NEVER, 0x00).text())
        assertEquals(30, Sony.minutes(0x01))
        assertEquals(0x04, Sony.idFor(15))
    }

    @Test
    fun touchKeys() {
        assertEquals(listOf(0x35, 0x20), Sony.keys(0x03, hex("f7 03 02 35 20")))
        assertEquals("f8 06 02 ff 20", Sony.setKeys(0x06, listOf(Sony.KEY_NONE, Sony.KEY_PLAYBACK)).text())
        assertEquals("f8 03 02 00 20", Sony.setKeys(0x03, listOf(Sony.KEY_NOISE, Sony.KEY_PLAYBACK)).text())
    }
}
