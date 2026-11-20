package com.blazify.buds.core.samsung

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
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
import kotlin.test.assertNotEquals
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

    /** Runs [test] against pretend earbuds of [model] that first say [first]. */
    private fun withBuds(model: SamsungModel, first: ByteArray, test: suspend (SamsungBuds, FakeBuds) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fake = FakeBuds(first)
        val buds = SamsungBuds(model.label, model, scope, { fake }).also { it.start() }
        try {
            withTimeout(5_000) { buds.state.first { it.sound != null } }
            test(buds, fake)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    private fun ack(id: Int, vararg value: Int) =
        SamsungMessage.encode(SamsungMessage(SamsungBuds.ACKNOWLEDGEMENT, byteArrayOf(id.toByte(), *value.map { it.toByte() }.toByteArray())), legacy = false)

    /** A made-up full status for the models nothing was recorded from: a revision, batteries, then [set] by place. */
    private fun status(vararg set: Pair<Int, Int>): ByteArray {
        val payload = ByteArray(48).also { it[0] = 1; it[2] = 80; it[3] = 80; it[7] = 60 }
        set.forEach { (at, value) -> payload[at] = value.toByte() }
        return SamsungMessage.encode(SamsungMessage(SamsungStatus.EXTENDED_STATUS, payload), legacy = false)
    }

    @Test
    fun budsProCancelLowOrHigh() = withBuds(SamsungModel.BudsPro, SamsungCaptures.BUDS_PRO_REV10) { buds, fake ->
        // Recorded with cancelling off and the strength on high.
        val options = buds.state.value.sound!!.options
        assertEquals(listOf(CancelStyle.Manual), options.cancelStyles)
        assertEquals(true, options.levels)
        assertEquals(2, options.manualLevels)
        assertEquals(2, buds.state.value.sound!!.manualLevel)

        buds.apply(Change.Cancelling(CancelStyle.Manual, level = 1))
        assertContentEquals(byteArrayOf(0), fake.next(SamsungBuds.NOISE_REDUCTION_LEVEL).payload)
        assertEquals(1, buds.state.value.sound!!.manualLevel)

        // The earbuds put it back to high.
        fake.incoming.add(ack(SamsungBuds.NOISE_REDUCTION_LEVEL, 1))
        withTimeout(3_000) { buds.state.first { it.sound?.manualLevel == 2 } }
    }

    @Test
    fun buds3ProHaveFiveStrengthsBesideAdaptive() = withBuds(SamsungModel.Buds3Pro, status(12 to 1, 24 to 2)) { buds, fake ->
        val sound = buds.state.value.sound!!
        assertEquals(listOf(CancelStyle.Adaptive, CancelStyle.Manual), sound.options.cancelStyles)
        assertEquals(5, sound.options.manualLevels)
        assertEquals(3, sound.manualLevel)

        // Manual is on already, so only the strength goes.
        buds.apply(Change.Cancelling(CancelStyle.Manual, level = 5))
        assertContentEquals(byteArrayOf(4), fake.next(SamsungBuds.NOISE_REDUCTION_LEVEL).payload)
        buds.apply(Change.Cancelling(CancelStyle.Adaptive))
        assertContentEquals(byteArrayOf(3), fake.next(SamsungBuds.NOISE_CONTROLS).payload)
        assertEquals(CancelStyle.Adaptive, buds.state.value.sound!!.cancelStyle)
    }

    @Test
    fun buds2ProVoiceDetectIsTheTalkingSwitch() = withBuds(SamsungModel.Buds2Pro, SamsungCaptures.BUDS2_PRO_REV13) { buds, fake ->
        assertEquals(true, buds.state.value.toggles[Toggle.SpeakToChat])
        buds.apply(Change.Switch(Toggle.SpeakToChat, false))
        assertContentEquals(byteArrayOf(0), fake.next(SamsungBuds.SET_DETECT_CONVERSATIONS).payload)
        assertEquals(false, buds.state.value.toggles[Toggle.SpeakToChat])
        fake.incoming.add(ack(SamsungBuds.SET_DETECT_CONVERSATIONS, 1))
        withTimeout(3_000) { buds.state.first { it.toggles[Toggle.SpeakToChat] == true } }
    }

    @Test
    fun buds3ShowTheirOwnCurveAndSendANewOne() = withBuds(SamsungModel.Buds3, status(9 to SamsungBuds.CUSTOM_PRESET)) { buds, fake ->
        // Playing the person's own curve: the app asks the earbuds for it.
        assertEquals(0, fake.next(SamsungBuds.CUSTOM_EQUALIZE_RECV).payload.size)
        val own = byteArrayOf(3, 2, 1, 0, 0, 0, -1, -2, -8)
        fake.incoming.add(SamsungMessage.encode(SamsungMessage(SamsungBuds.CUSTOM_EQUALIZE_RECV, byteArrayOf(7, 9) + ByteArray(54) + own), legacy = false))
        val eq = withTimeout(3_000) { buds.state.first { it.equalizer?.left?.firstOrNull() == 3.0 } }.equalizer!!
        assertEquals(true, eq.custom)
        assertEquals(Equalizer.CUSTOM, eq.presetId)
        assertEquals(SamsungBuds.BANDS, eq.frequencies)
        // −8 is past what the screens draw, so it shows as −6.
        assertEquals(listOf(3.0, 2.0, 1.0, 0.0, 0.0, 0.0, -1.0, -2.0, -6.0), eq.left)

        // A curve of the app's usual eight bands goes as nine steps, then the earbuds are told to play it.
        buds.apply(Change.Bands(listOf(6.0, 3.0, 0.0, 0.0, 0.0, 0.0, -3.0, -6.0)))
        assertContentEquals(byteArrayOf(9, 6, 5, 2, 0, 0, 0, -1, -4, -6), fake.next(SamsungBuds.CUSTOM_EQUALIZE_SEND).payload)
        assertContentEquals(byteArrayOf(7), fake.next(SamsungBuds.EQUALIZER).payload)
        assertEquals(listOf(6.0, 5.0, 2.0, 0.0, 0.0, 0.0, -1.0, -4.0, -6.0), buds.state.value.equalizer!!.left)

        // A preset again; the earbuds' acknowledgement of 7 is the curve.
        buds.apply(Change.Preset(3))
        assertContentEquals(byteArrayOf(3), fake.next(SamsungBuds.EQUALIZER).payload)
        assertEquals(3, buds.state.value.equalizer!!.presetId)
        fake.incoming.add(ack(SamsungBuds.EQUALIZER, SamsungBuds.CUSTOM_PRESET))
        withTimeout(3_000) { buds.state.first { it.equalizer?.presetId == Equalizer.CUSTOM } }
    }

    @Test
    fun theCurveGoesAgainWhenTheEarbudsMayHaveForgottenIt() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Two connections one after the other; the earbuds were switched off in between and say "Normal".
        val fakes = LinkedBlockingQueue(listOf(FakeBuds(status(9 to 0)), FakeBuds(status(9 to 0))))
        val opened = LinkedBlockingQueue<FakeBuds>()
        val buds = SamsungBuds("Galaxy Buds4 Pro", SamsungModel.Buds4Pro, scope, { fakes.poll()!!.also { opened.add(it) } }).also { it.start() }
        try {
            val first = opened.poll(5, TimeUnit.SECONDS)!!
            withTimeout(5_000) { buds.state.first { it.equalizer != null } }
            assertNotNull(first.next(SamsungBuds.CUSTOM_EQUALIZE_RECV))
            buds.apply(Change.Bands(List(9) { 2.0 }))
            assertContentEquals(byteArrayOf(9, 2, 2, 2, 2, 2, 2, 2, 2, 2), first.next(SamsungBuds.CUSTOM_EQUALIZE_SEND).payload)

            // The Buds4 Pro drop it when the noise control changes on the earbuds.
            first.incoming.add(SamsungMessage.encode(SamsungMessage(SamsungBuds.NOISE_CONTROLS_UPDATE, byteArrayOf(2)), legacy = false))
            assertContentEquals(byteArrayOf(9, 2, 2, 2, 2, 2, 2, 2, 2, 2), first.next(SamsungBuds.CUSTOM_EQUALIZE_SEND).payload)

            // And every pair forgets it when switched off: on the next connection it goes again, not asked for.
            first.close()
            val second = opened.poll(10, TimeUnit.SECONDS)!!
            assertContentEquals(byteArrayOf(9, 2, 2, 2, 2, 2, 2, 2, 2, 2), second.next(SamsungBuds.CUSTOM_EQUALIZE_SEND).payload)
            assertContentEquals(byteArrayOf(7), second.next(SamsungBuds.EQUALIZER).payload)
            withTimeout(3_000) { buds.state.first { it.equalizer?.presetId == Equalizer.CUSTOM } }
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    @Test
    fun buds2HaveOneCancellingStrength() = withBuds(SamsungModel.Buds2, SamsungCaptures.BUDS2_REV10) { buds, fake ->
        assertEquals(emptyList(), buds.state.value.sound!!.options.cancelStyles)
        assertEquals(false, buds.state.value.sound!!.options.levels)
        assertEquals(null, buds.state.value.toggles[Toggle.SpeakToChat])
        // Presets only: no curve to draw, and none is sent.
        assertEquals(false, buds.state.value.equalizer!!.custom)
        buds.apply(Change.Bands(List(8) { 1.0 }))
        buds.apply(Change.Cancelling(CancelStyle.Manual, level = 1))
        buds.ring(left = true, right = true)
        // Nothing went out for the strength: the next message is the ring.
        while (true) {
            val message = fake.sent.poll(3, TimeUnit.SECONDS) ?: error("nothing sent")
            assertNotEquals(SamsungBuds.NOISE_REDUCTION_LEVEL, message.id)
            assertNotEquals(SamsungBuds.CUSTOM_EQUALIZE_SEND, message.id)
            if (message.id == SamsungBuds.FIND_MY_EARBUDS_START) break
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
