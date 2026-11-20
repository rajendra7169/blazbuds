package com.blazify.buds.core.samsung

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The driver against pretend Buds2 that say what the recorded ones said, and keep what is sent to them. */
class SamsungBudsTest {
    private class FakeBuds(first: ByteArray) : Connection {
        val incoming = LinkedBlockingQueue<ByteArray>().apply { add(first) }
        val sent = LinkedBlockingQueue<SamsungMessage>()
        private val reader = SamsungMessage.Reader(legacy = false)
        @Volatile private var closed = false

        override fun read(into: ByteArray): Int {
            while (!closed) {
                val next = incoming.poll(50, TimeUnit.MILLISECONDS) ?: continue
                next.copyInto(into)
                return next.size
            }
            return -1
        }

        override fun write(bytes: ByteArray) {
            reader.feed(bytes).forEach { sent.add(it) }
        }

        override fun close() {
            closed = true
        }

        fun next(id: Int): SamsungMessage {
            while (true) {
                val message = sent.poll(3, TimeUnit.SECONDS) ?: error("nothing sent with id %02x".format(id))
                if (message.id == id) return message
            }
        }
    }

    @Test
    fun showsTheRecordedBuds2AndSendsChangesTheWaySamsungDoes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fake = FakeBuds(SamsungCaptures.BUDS2_REV10)
        val buds = SamsungBuds("Galaxy Buds2 (1234)", SamsungModel.Buds2, scope, { fake }).also { it.start() }
        try {
            val state = withTimeout(5_000) { buds.state.first { it.sound != null } }
            assertEquals(100, state.left?.percent)
            assertEquals(51, state.case?.percent)
            assertEquals(ListeningMode.NoiseCancelling, state.sound!!.mode)
            assertEquals(ListeningMode.entries, state.sound!!.options.modes)
            assertEquals("Dynamic", state.equalizer?.preset?.name)
            assertEquals(TouchAction.ListeningMode, state.controls?.actions?.get(Ear.Left to Press.Long))
            assertEquals(true, state.toggles[Toggle.TouchLock])
            assertEquals(true, state.canRing)

            // On the first status, the app introduces itself and asks for the firmware.
            assertContentEquals(byteArrayOf(1, 1, 34), fake.next(SamsungBuds.MANAGER_INFO).payload)
            assertNotNull(fake.next(SamsungBuds.DEBUG_GET_ALL_DATA))

            buds.apply(Change.Mode(ListeningMode.Transparency))
            assertContentEquals(byteArrayOf(2), fake.next(SamsungBuds.NOISE_CONTROLS).payload)
            assertEquals(ListeningMode.Transparency, buds.state.value.sound!!.mode)

            // Unlocking keeps every gesture as it was, the two for calls included.
            buds.apply(Change.Switch(Toggle.TouchLock, false))
            assertContentEquals(byteArrayOf(1, 1, 1, 1, 1, 1, 1), fake.next(SamsungBuds.LOCK_TOUCHPAD).payload)
            assertEquals(false, buds.state.value.toggles[Toggle.TouchLock])

            buds.apply(Change.Touch(Ear.Left, Press.Long, TouchAction.VoiceAssistant))
            assertContentEquals(byteArrayOf(1, 2), fake.next(SamsungBuds.SET_TOUCHPAD_OPTION).payload)

            buds.apply(Change.Preset(1))
            assertContentEquals(byteArrayOf(1), fake.next(SamsungBuds.EQUALIZER).payload)

            // The earbuds' acknowledgement has the last word: Treble boost.
            fake.incoming.add(SamsungMessage.encode(SamsungMessage(SamsungBuds.ACKNOWLEDGEMENT, byteArrayOf(0x86.toByte(), 5)), legacy = false))
            withTimeout(3_000) { buds.state.first { it.equalizer?.presetId == 5 } }

            // A change made on the earbuds themselves.
            fake.incoming.add(SamsungMessage.encode(SamsungMessage(SamsungBuds.NOISE_CONTROLS_UPDATE, byteArrayOf(0)), legacy = false))
            withTimeout(3_000) { buds.state.first { it.sound?.mode == ListeningMode.Normal } }

            buds.ring(left = true, right = false)
            assertNotNull(fake.next(SamsungBuds.FIND_MY_EARBUDS_START))
            assertContentEquals(byteArrayOf(0, 1), fake.next(SamsungBuds.MUTE_EARBUD).payload)
            buds.ring(left = false, right = false)
            assertNotNull(fake.next(SamsungBuds.FIND_MY_EARBUDS_STOP))
            delay(50)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    @Test
    fun the2019BudsSetAmbientSoundAndPresetsTheirOwnWay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fake = object : Connection {
            val incoming = LinkedBlockingQueue<ByteArray>().apply { add(SamsungCaptures.BUDS_REV3) }
            val sent = LinkedBlockingQueue<SamsungMessage>()
            val reader = SamsungMessage.Reader(legacy = true)
            @Volatile var closed = false
            override fun read(into: ByteArray): Int {
                while (!closed) {
                    val next = incoming.poll(50, TimeUnit.MILLISECONDS) ?: continue
                    next.copyInto(into)
                    return next.size
                }
                return -1
            }
            override fun write(bytes: ByteArray) = reader.feed(bytes).forEach { sent.add(it) }
            override fun close() { closed = true }
        }
        fun next(id: Int): SamsungMessage {
            while (true) {
                val message = fake.sent.poll(3, TimeUnit.SECONDS) ?: error("nothing sent")
                if (message.id == id) return message
            }
        }
        val buds = SamsungBuds("Galaxy Buds (1234)", SamsungModel.Buds, scope, { fake }).also { it.start() }
        try {
            val state = withTimeout(5_000) { buds.state.first { it.sound != null } }
            assertEquals(listOf(ListeningMode.Transparency, ListeningMode.Normal), state.sound!!.options.modes)
            assertEquals(true, state.sound!!.options.transparencyStyles)
            assertEquals(TouchAction.VolumeDown, state.controls?.actions?.get(Ear.Left to Press.Long))
            assertEquals(TouchAction.VolumeUp, state.controls?.actions?.get(Ear.Right to Press.Long))

            buds.apply(Change.Mode(ListeningMode.Transparency))
            assertContentEquals(byteArrayOf(1), next(SamsungBuds.SET_AMBIENT_MODE).payload)
            // Clear is preset 3 counted from 0, sent as 3 + 5.
            buds.apply(Change.Preset(4))
            assertContentEquals(byteArrayOf(1, 8), next(SamsungBuds.EQUALIZER).payload)
            buds.apply(Change.Preset(0))
            assertContentEquals(byteArrayOf(0, 7), next(SamsungBuds.EQUALIZER).payload)
        } finally {
            buds.close()
            scope.cancel()
        }
    }
}
