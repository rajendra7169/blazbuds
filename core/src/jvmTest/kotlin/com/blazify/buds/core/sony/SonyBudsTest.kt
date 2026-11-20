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
                val said = SonyFrame.hex(frame.payload)
                heard += said
                val command = frame.payload[0].toInt() and 0xFF
                val type = said.substring(3, 5)
                val rest = said.substring(2)
                // Set commands: 68 noise, 58 equalizer, F8 system, 28 power, E8 audio; each one's question is two less.
                val answer = if (command in listOf(0x68, 0x58, 0xF8, 0x28, 0xE8)) {
                    answers["%02x %s".format(command - 2, type)] = "%02x".format(command - 1) + rest
                    "%02x".format(command + 1) + rest
                } else {
                    answers[said]
                }
                if (answer != null) {
                    out.add(SonyFrame.encode(SonyFrame.TABLE1, seq, answer.split(" ").map { it.toInt(16).toByte() }.toByteArray()))
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
}
