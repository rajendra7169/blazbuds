package com.blazify.buds.core.nothing

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The driver against pretend earbuds that answer as the Nothing Ear (B171) in PairFree's device report did. */
class NothingBudsTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private inner class FakeEar(private val answers: Map<Int, String>, private val needsWaking: Boolean = false) : Connection {
        private val out = LinkedBlockingQueue<ByteArray>()
        private val reader = NothingFrame.Reader()
        val writes = CopyOnWriteArrayList<Pair<Int, ByteArray>>()
        private var awake = !needsWaking
        @Volatile private var closed = false

        override fun read(into: ByteArray): Int {
            while (!closed) {
                val next = out.poll(50, TimeUnit.MILLISECONDS) ?: continue
                next.copyInto(into)
                return next.size
            }
            return -1
        }

        override fun write(bytes: ByteArray) {
            for (frame in reader.feed(bytes)) {
                if (frame.command == NothingCommands.ACTIVATE) awake = true
                if (frame.command and 0xF000 == 0xF000) writes += frame.command to frame.payload
                val result = when {
                    !awake -> NothingFrame.NOT_ACTIVATED
                    frame.command and 0xF000 == 0xF000 || frame.command in answers -> NothingFrame.OK
                    else -> NothingFrame.UNSUPPORTED
                }
                out.add(reply(frame.command and 0x7FFF, result, hex(answers[frame.command].takeIf { result == NothingFrame.OK } ?: ""), frame.number))
            }
        }

        override fun close() { closed = true }

        private fun reply(command: Int, result: Int, payload: ByteArray, number: Int): ByteArray {
            val head = byteArrayOf(0x55, (0x20 or result).toByte(), 0x01, command.toByte(), (command shr 8).toByte(), payload.size.toByte(), 0, number.toByte()) + payload
            val crc = NothingFrame.crc16(head)
            return head + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
        }
    }

    /** What the Nothing Ear answered (PairFree's B171 report, firmware 1.0.1.56). */
    private val nothingEar = mapOf(
        0xC001 to "31 2e 30 2e 30 0a",
        0xC01C to "71 b1",
        0xC042 to "31 2e 30 2e 31 2e 35 36",
        0xC007 to "02 02 5f 03 64",
        0xC01E to "01 07 00 02 01 00",
        0xC01F to "00",
        0xC044 to "00".repeat(53),
        0xC00E to "09 01 01 02 01 07 01 09 01 0a 01 0b 00 0e 01 12 01 15 01",
        0xC041 to "02",
        0xC04E to "01 06",
        0xC018 to "08 02 01 02 08 03 01 02 09 02 01 03 0b 03 01 03 0b 02 01 07 16 03 01 07 16 02 01 09 01 03 01 09 01",
    )

    private fun <T> withEar(ear: FakeEar, block: suspend (NothingBuds) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val buds = NothingBuds("Nothing Ear", scope, { ear })
        buds.start()
        try {
            withTimeout(15_000) { buds.state.first { it.link == Link.Connected && it.controls != null } }
            block(buds)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    @Test
    fun showsWhatTheNothingEarAnswered() = withEar(FakeEar(nothingEar)) { buds ->
        val state = buds.state.value
        assertEquals("Nothing Ear", state.model)
        assertEquals("1.0.1.56", state.firmware)
        assertEquals(Battery(95), state.left)
        assertEquals(Battery(100), state.right)
        assertNull(state.case)
        val sound = state.sound!!
        assertEquals(ListeningMode.Transparency, sound.mode)
        assertEquals(5, sound.manualLevel)
        assertEquals(listOf(CancelStyle.Adaptive, CancelStyle.Manual), sound.options.cancelStyles)
        assertEquals(ListeningMode.entries.toSet(), sound.options.modes.toSet())
        val eq = state.equalizer!!
        assertEquals("Balanced", eq.preset?.name)
        assertTrue(eq.custom)
        assertEquals(listOf(140, 980, 3400), eq.frequencies)
        assertEquals(mapOf(Toggle.WearDetection to true, Toggle.GamingMode to false, Toggle.BassBoost to true), state.toggles)
        val controls = state.controls!!
        assertEquals(TouchAction.PreviousSong, controls.actions[Ear.Left to Press.Double])
        assertEquals(TouchAction.VoiceAssistant, controls.actions[Ear.Right to Press.Triple])
        assertEquals(TouchAction.ListeningMode, controls.actions[Ear.Left to Press.Long])
        assertTrue(state.canRing)
    }

    @Test
    fun changesGoOutAsNothingsAppSendsThem() {
        val ear = FakeEar(nothingEar)
        withEar(ear) { buds ->
            buds.apply(Change.Mode(ListeningMode.NoiseCancelling))
            buds.apply(Change.Cancelling(CancelStyle.Manual, level = 3))
            buds.apply(Change.Switch(Toggle.GamingMode, true))
            buds.apply(Change.Switch(Toggle.BassBoost, false))
            buds.apply(Change.Touch(Ear.Right, Press.Double, TouchAction.VoiceAssistant))
            buds.apply(Change.Bands(listOf(2.0, 0.0, -1.0)))
            buds.ring(left = true, right = false)
        }
        val sent = ear.writes.map { (command, payload) -> NothingFrame.hex4(command) + " " + NothingFrame.hex(payload) }
        assertEquals(
            listOf(
                "f00f 01 01 00", // back to high, the strength it had
                "f00f 01 02 00", // step 3 is mid
                "f040 01",
                "f051 00 06", // the strength stays
                "f003 01 03 01 02 0b",
                "f010 05 00", // the own curve is preset 5
                NothingFrame.hex4(NothingCommands.SET_CURVE) + " " + NothingFrame.hex(NothingCommands.curve(2.0, 0.0, -1.0, NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 1.0f))),
                "f002 02 01",
                "f002 03 00",
            ),
            sent,
        )
    }

    @Test
    fun anEightBandSavedSoundGoesOutAsBassMidTreble() {
        val ear = FakeEar(nothingEar)
        withEar(ear) { buds ->
            buds.apply(Change.Bands(listOf(5.0, 3.0, 0.0, 1.0, 2.0, -1.0, -2.0, -3.0)))
            assertEquals(listOf(4.0, 1.0, -2.0), buds.state.value.equalizer?.left)
        }
        val curve = ear.writes.first { it.first == NothingCommands.SET_CURVE }.second
        assertContentEquals(NothingCommands.curve(4.0, 1.0, -2.0, NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 1.0f)), curve)
    }

    /** The Ear with its eight-band sound on, the first band moved to 60 Hz in Nothing's app. */
    private val advancedBands = NothingCommands.ADVANCED_BANDS.mapIndexed { i, band ->
        band.copy(hz = if (i == 0) 60f else band.hz, gain = listOf(1f, 2f, 3f, 4f, 5f, 6f, -1f, -2f)[i])
    }

    private fun withAdvanced(on: Boolean) = nothingEar + mapOf(
        0xC04C to if (on) "01" else "00",
        0xC04D to NothingFrame.hex(NothingCommands.advancedCurve(advancedBands)),
    )

    @Test
    fun theEightBandSoundIsThePersonsCurve() = withEar(FakeEar(withAdvanced(on = true))) { buds ->
        val eq = buds.state.value.equalizer!!
        assertEquals(Equalizer.CUSTOM, eq.presetId)
        assertEquals(listOf(60, 110, 220, 440, 1320, 3300, 6600, 13200), eq.frequencies)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, -1.0, -2.0), eq.left)
        assertEquals(listOf("Balanced", "More bass", "More treble", "Voice", "Simple custom"), eq.presets.map { it.name })
        assertTrue(eq.custom)
    }

    @Test
    fun aNewCurveKeepsTheBandsWhereTheyWere() {
        val ear = FakeEar(withAdvanced(on = true))
        val gains = listOf(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
        withEar(ear) { buds -> buds.apply(Change.Bands(gains)) }
        // Already on, so only the curve goes out, the 60 Hz band kept.
        val curve = NothingCommands.advancedCurve(advancedBands.mapIndexed { i, band -> band.copy(gain = gains[i].toFloat()) })
        assertEquals(listOf(NothingFrame.hex4(NothingCommands.SET_ADVANCED_CURVE) + " " + NothingFrame.hex(curve)), sentNow(ear))
    }

    @Test
    fun aCurveTurnsTheEightBandSoundOnFirst() {
        val ear = FakeEar(withAdvanced(on = false))
        withEar(ear) { buds ->
            assertEquals(0, buds.state.value.equalizer?.presetId)
            buds.apply(Change.Bands(List(8) { 1.0 }))
        }
        val sent = sentNow(ear)
        assertEquals("f04f 01 00", sent[0])
        assertTrue(sent[1].startsWith("f050 00 08 00 00 80 bf"))
    }

    @Test
    fun aPresetTurnsTheEightBandSoundOff() {
        val ear = FakeEar(withAdvanced(on = true))
        withEar(ear) { buds -> buds.apply(Change.Preset(3)) }
        assertEquals(listOf("f04f 00 00", "f010 03 00"), sentNow(ear))
    }

    private fun sentNow(ear: FakeEar) = ear.writes.map { (command, payload) -> NothingFrame.hex4(command) + " " + NothingFrame.hex(payload) }

    @Test
    fun wakesEarbudsThatAskForIt() {
        val ear = FakeEar(nothingEar, needsWaking = true)
        withEar(ear) { buds -> assertEquals("Nothing Ear", buds.state.value.model) }
        assertContentEquals(ByteArray(0), ear.writes.first { it.first == NothingCommands.ACTIVATE }.second)
    }

    @Test
    fun aModelItDoesntKnowGetsWhatItAnswers() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val answers = nothingEar + (0xC01C to "99 b1") - 0xC041
        val buds = NothingBuds("Nothing Ear (9)", scope, { FakeEar(answers) })
        buds.start()
        val state = withTimeout(15_000) { buds.state.first { it.link == Link.Connected && it.equalizer != null && Toggle.BassBoost in it.toggles } }
        buds.close()
        scope.cancel()
        assertEquals("Nothing B199", state.model)
        assertEquals(ListeningMode.Transparency, state.sound?.mode)
        assertEquals(false, state.equalizer?.custom)
        assertNull(state.controls)
        assertTrue(Toggle.GamingMode !in state.toggles)
        assertEquals(false, state.canRing)
    }
}
