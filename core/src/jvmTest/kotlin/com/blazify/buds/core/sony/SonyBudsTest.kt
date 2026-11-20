package com.blazify.buds.core.sony

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TransparencyStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The driver against pretend WF-1000XM5 earbuds that answer as the real ones did in a user's device report. */
class SonyBudsTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    /** Answers each question from [start]; a change is told back as news and then answered from. */
    private class FakeXm5(start: Map<String, String>) : Connection {
        private val answers = java.util.concurrent.ConcurrentHashMap(start)
        private val out = LinkedBlockingQueue<ByteArray>()
        private val reader = SonyFrame.Reader()
        private var seq = 0
        val heard = CopyOnWriteArrayList<String>()
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
            for (frame in reader.feed(bytes, bytes.size)) {
                if (frame.isAck) continue
                out.add(SonyFrame.ack(frame.seq))
                // Table two's messages are written and looked up with "2 " in front.
                val table = if (frame.kind == SonyFrame.TABLE2) "2 " else ""
                val said = SonyFrame.hex(frame.payload)
                heard += table + said
                val command = frame.payload[0].toInt() and 0xFF
                val type = said.substring(3, 5)
                val rest = said.substring(2)
                // Set commands: 68 noise, 58 equalizer, F8 system, 28 power, E8 audio, and table two's 48 voice
                // guidance; each one's question is two less.
                val sets = if (table.isEmpty()) listOf(0x68, 0x58, 0xF8, 0x28, 0xE8) else listOf(0x48)
                val answer = if (command in sets) {
                    answers[table + "%02x %s".format(command - 2, type)] = "%02x".format(command - 1) + rest
                    "%02x".format(command + 1) + rest
                } else {
                    answers[table + said]
                }
                if (answer != null) {
                    out.add(SonyFrame.encode(frame.kind, seq, answer.split(" ").map { it.toInt(16).toByte() }.toByteArray()))
                    seq = 1 - seq
                }
            }
        }

        override fun close() { closed = true }
    }

    private val xm5 = mapOf(
        "00 00" to "01 00 03 00 30 18 00 00",
        "04 01" to "05 01 0a 57 46 2d 31 30 30 30 58 4d 35",
        "04 02" to "05 02 05 36 2e 31 2e 30",
        "06 00" to "07 00 2e 10 ff 12 ff 13 ff 14 ff 15 ff 16 ff 17 ff 23 ff 25 36 11 ff 27 ff 29 ff 2a ff 92 ff 50 11 6b 08 70 01 " +
            "90 ff a1 12 c1 ff c2 ff e2 1b f1 38 fc 0d 93 ff f6 33 f9 41 d1 3b 94 ff 40 2e 42 3a 44 ff ff 2d 19 ff f3 26 e7 1a d2 20 " +
            "fd 2b b6 1d b0 42 b2 43 b8 1f b7 15 34 3f f4 23 f5 24",
        "22 09" to "23 09 00 00 64 00 64 64",
        "22 0a" to "23 0a 28 00 1e",
        "66 17" to "67 17 01 01 01 00 14",
        "50 00" to "51 00 06 15 0c 00 00 10 00 11 00 12 00 13 00 14 00 15 00 16 00 17 00 a0 00 a1 00 a2 00",
        "5a 00" to "5b 00 06 10 00 01 01 01 90 01 03 e8 01 09 c4 01 18 9c 01 3e 80",
        "56 00" to "57 00 a2 06 0e 0a 0a 0a 0a 0a",
        "e6 01" to "e7 01 00",
        "f6 01" to "f7 01 01",
        "f6 0c" to "f7 0c 01 01",
        "20 05" to "21 05 02 10 11",
        "26 05" to "27 05 10 00",
        "f6 03" to "f7 03 02 35 20",
    )

    @Test
    fun readsEverythingTheXm5HasAndWritesChangesItsWay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val earbuds = FakeXm5(xm5)
        // Set two's service missing, as on older models, then set one's way answering.
        val buds = SonyBuds("WF-1000XM5", scope, listOf({ throw LinkException("no such service") }, { earbuds }))
        buds.start()
        try {
            val ready = withTimeout(15_000) { buds.state.first { it.isReady && it.controls != null && it.autoPowerOff != null && it.equalizer != null && it.case != null } }
            assertEquals("WF-1000XM5", ready.model)
            assertEquals("6.1.0", ready.firmware)
            assertEquals(Link.Connected, ready.link)
            assertEquals(null, ready.left)
            assertEquals(Battery(100), ready.right)
            assertEquals(Battery(40), ready.case)
            assertEquals(ListeningMode.Transparency, ready.sound!!.mode)
            assertEquals(Equalizer.CUSTOM, ready.equalizer!!.presetId)
            assertEquals(mapOf(Toggle.Upscaling to false, Toggle.WearDetection to false, Toggle.SpeakToChat to false), ready.toggles)
            assertEquals(true, ready.autoPowerOff!!.enabled)
            // The left key holds a function this app doesn't offer, so only the right shows.
            assertEquals(mapOf((Ear.Right to Press.Single) to TouchAction.PlayPause), ready.controls!!.actions)

            buds.apply(Change.Mode(ListeningMode.NoiseCancelling))
            buds.apply(Change.Switch(Toggle.SpeakToChat, true))
            buds.apply(Change.Preset(0x16))
            buds.apply(Change.PowerOff(false, 0))
            buds.apply(Change.Touch(Ear.Left, Press.Single, TouchAction.ListeningMode))
            delay(300)
            listOf("68 17 01 01 00 00 14", "f8 0c 00 01", "58 00 16 00", "28 05 11 00", "f8 03 02 00 20").forEach {
                assertTrue(it in earbuds.heard, "sent $it")
            }
            assertEquals(ListeningMode.NoiseCancelling, buds.state.value.sound!!.mode)
            assertEquals(0x16, buds.state.value.equalizer!!.presetId)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    @Test
    fun theXm5sAmbientLevelIsShownAndSet() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val earbuds = FakeXm5(xm5)
        val buds = SonyBuds("WF-1000XM5", scope, listOf({ earbuds }))
        buds.start()
        try {
            val ready = withTimeout(15_000) { buds.state.first { it.sound != null } }
            // The XM5 reports ambient sound at level 20 (0x14) without focus on voice.
            assertEquals(20, ready.sound!!.options.ambientLevels)
            assertEquals(20, ready.sound!!.ambientLevel)
            assertEquals(TransparencyStyle.Full, ready.sound!!.transparencyStyle)
            assertTrue(ready.sound!!.options.transparencyStyles)

            buds.apply(Change.AmbientLevel(7))
            buds.apply(Change.TransparencyKind(TransparencyStyle.Voice))
            delay(300)
            assertTrue("68 17 01 01 01 00 07" in earbuds.heard)
            assertTrue("68 17 01 01 01 01 07" in earbuds.heard)
            assertEquals(7, buds.state.value.sound!!.ambientLevel)
            assertEquals(TransparencyStyle.Voice, buds.state.value.sound!!.transparencyStyle)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    @Test
    fun theXm5sVoiceGuidanceIsOnTableTwo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Table two's functions and voice guidance as the XM5 in PairFree's device report answered them.
        val earbuds = FakeXm5(
            xm5 + mapOf(
                "2 06 00" to "07 00 0b 42 3d 53 ff 54 ff f1 ff 60 ff 61 ff f2 ff 63 ff f8 30 32 04 31 ff",
                "2 46 01" to "47 01 00 01",
            ),
        )
        val buds = SonyBuds("WF-1000XM5", scope, listOf({ earbuds }))
        buds.start()
        try {
            val ready = withTimeout(15_000) { buds.state.first { Toggle.VoicePrompt in it.toggles } }
            assertEquals(true, ready.toggles[Toggle.VoicePrompt])
            assertTrue("2 06 00" in earbuds.heard)
            // The equalizer's own 06 00 on table one is not mistaken for table two's.
            assertTrue("06 00" in earbuds.heard)

            buds.apply(Change.Switch(Toggle.VoicePrompt, false))
            delay(300)
            // Function 42 is type 1, and set two writes off as 1, without the language.
            assertTrue("2 48 01 01" in earbuds.heard, "${earbuds.heard}")
            assertEquals(false, buds.state.value.toggles[Toggle.VoicePrompt])
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    /**
     * A set one pair laid out as libmdr describes the WH-1000XM3 (noise cancelling and ambient sound, dual and
     * single), saying 15 steps for its level so the count it gives is seen to be used.
     */
    private val setOne = mapOf(
        "00 00" to "01 00 02 00",
        "04 01" to "05 01 0a 57 48 2d 31 30 30 30 58 4d 33",
        "06 00" to "07 00 02 62 e1",
        "60 02" to "61 02 02 01 01 02 00 0f 01 0f",
        "66 02" to "67 02 01 02 02 01 00 00",
        // Connection mode: a stable connection (1).
        "e6 01" to "e7 01 00 01",
    )

    @Test
    fun setOneKeepsTheAmbientLevelThroughCancelling() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val earbuds = FakeXm5(setOne)
        val buds = SonyBuds("WH-1000XM3", scope, listOf({ earbuds }))
        buds.start()
        try {
            val ready = withTimeout(15_000) { buds.state.first { it.sound?.options?.ambientLevels == 15 } }
            assertEquals(ListeningMode.NoiseCancelling, ready.sound!!.mode)
            // Cancelling reports level 0, so ambient sound starts from the top until one is heard.
            buds.apply(Change.Mode(ListeningMode.Transparency))
            delay(200)
            assertTrue("68 02 11 02 00 01 00 0f" in earbuds.heard, "${earbuds.heard}")
            buds.apply(Change.AmbientLevel(5))
            buds.apply(Change.Mode(ListeningMode.NoiseCancelling))
            buds.apply(Change.Mode(ListeningMode.Transparency))
            delay(300)
            listOf("68 02 11 02 00 01 00 05", "68 02 11 02 02 01 00 00").forEach { assertTrue(it in earbuds.heard, "sent $it") }
            // The second time into ambient sound brings back level 5, not 0.
            assertEquals("68 02 11 02 00 01 00 05", earbuds.heard.last { it.startsWith("68") })
            assertEquals(5, buds.state.value.sound!!.ambientLevel)

            // Priority on sound quality shows on the high-quality switch.
            assertEquals(false, buds.state.value.toggles[Toggle.Ldac])
            buds.apply(Change.Switch(Toggle.Ldac, true))
            delay(200)
            assertTrue("e8 01 00 00" in earbuds.heard)
            assertEquals(true, buds.state.value.toggles[Toggle.Ldac])
        } finally {
            buds.close()
            scope.cancel()
        }
    }
}
