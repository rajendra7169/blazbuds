package com.blazify.buds.core.oneplus

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.FitResult
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The driver against pretend OnePlus Buds 4 that answer with the bytes real ones sent QuickBuds. */
class MelodyBudsTest {
    private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

    private class FakeBuds4 : Connection {
        private val out = LinkedBlockingQueue<ByteArray>()
        private val reader = MelodyFrame.Reader()
        @Volatile private var closed = false
        var noise = 0x0020
        val switches = linkedMapOf(5 to 0, 4 to 0, 0x0B to 1, 0x11 to 1, 0x18 to 1, 6 to 0, 0x1B to 0, 0x1D to 1)
        var taps = "00 12 01 01 01 01 01 01 02 01 01 01 03 00 01 01 04 08 01 01 06 00 01 01 05 07 02 01 01 00 02 01 02 01 02 01 03 00 " +
            "02 01 04 08 02 01 06 00 02 01 05 07 01 06 02 00 01 06 03 00 01 06 06 00 02 06 02 00 02 06 03 00 02 06 06 00"
        val written = mutableListOf<MelodyMessage>()
        /** What the fit test reports after it starts: left good, right poor, as `04 <part> <seal>...` news. */
        var fit = "04 01 01 02 06"

        override fun read(into: ByteArray): Int {
            while (!closed) {
                val next = out.poll(50, TimeUnit.MILLISECONDS) ?: continue
                next.copyInto(into)
                return next.size
            }
            return -1
        }

        private fun hex(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

        override fun write(bytes: ByteArray) {
            for (m in reader.feed(bytes)) {
                synchronized(written) { written += m }
                val answer = when (m.command) {
                    0x0100 -> hex("00 FF 77 5A EA 67 0E 20 07")
                    0x0103 -> hex("00 14 54 06")
                    0x0105 -> hex("00 04") + "1,2,138,2,2,138,3,1,01,3,2,105".encodeToByteArray()
                    0x0106 -> hex("00 03 01 5A 02 5A 03 50")
                    0x0108 -> hex(taps)
                    0x010C -> if (m.at(0) == 1) byteArrayOf(0, 1, 1) + Melody.maskBytes(noise) else hex("00 02 01 07 08")
                    0x010D -> {
                        // Only the ids these earbuds have come back.
                        val known = m.payload.drop(1).map { it.toInt() and 0xFF }.filter { it in switches }
                        byteArrayOf(0, known.size.toByte()) + known.flatMap { listOf(it.toByte(), switches.getValue(it).toByte()) }
                    }
                    0x010F -> hex("00 01")
                    0x0122 -> hex(
                        "00 02 01 FA 06 04 06 47 6F 6F 64 45 51 06 3E 00 FF FA 00 FF E8 03 FE A0 0F 01 40 1F 02 80 3E 05 " +
                            "00 FA 06 05 07 43 75 73 74 6F 6D 31 06 3E 00 00 FA 00 00 E8 03 00 A0 0F 00 40 1F 00 80 3E 00",
                    )
                    0x0200 -> hex("00 09 01 02 03 04 08 0B F1 F2 F3")
                    0x0401 -> {
                        taps = "00 " + hex(m.payload)
                        hex("00")
                    }
                    0x0403 -> {
                        switches[m.at(0)] = m.at(1)
                        hex("00")
                    }
                    0x0405 -> {
                        if (m.at(0) == 1) out.add(MelodyFrame.encode(Melody.NEWS, 0xFF, hex(fit)))
                        hex("00")
                    }
                    0x0404 -> {
                        // Off is set as bit 0 and reported as bit 3.
                        if (m.at(0) == 1) noise = if (m.at(2) == 1) 0x0008 else Melody.mask(m.payload, 2)
                        hex("00")
                    }
                    else -> null
                } ?: continue
                out.add(MelodyFrame.encode(m.command or 0x8000, m.seq, answer))
            }
        }

        override fun close() {
            closed = true
        }
    }

    private fun <T> withBuds(fake: FakeBuds4, block: suspend (MelodyBuds) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val buds = MelodyBuds("OnePlus Buds 4", scope, listOf({ fake }))
        buds.start()
        try {
            block(buds)
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    private suspend fun MelodyBuds.until(what: (BudsState) -> Boolean): BudsState = withTimeout(10_000) { state.first(what) }

    @Test
    fun showsWhatABuds4Has() {
        val fake = FakeBuds4()
        withBuds(fake) { buds ->
            val state = buds.until { it.isReady && it.controls != null && it.equalizer?.frequencies?.isNotEmpty() == true && it.firmware != null }
            assertEquals("OnePlus Buds 4", state.model)
            assertEquals(Battery(90), state.left)
            assertEquals(Battery(80), state.case)
            assertEquals("138.138.105", state.firmware)
            assertEquals(true, state.canRing)
            val sound = state.sound!!
            assertEquals(ListeningMode.NoiseCancelling, sound.mode)
            assertEquals(3, sound.manualLevel)
            assertEquals(ListeningMode.entries.toSet(), sound.cycle)
            assertEquals(1, state.equalizer!!.presetId)
            assertEquals(listOf(-1.0, -1.0, -2.0, 1.0, 2.0, 5.0), state.equalizer!!.left)
            assertEquals(TouchAction.PlayPause, state.controls!!.actions[Ear.Left to Press.Single])
            assertEquals(TouchAction.None, state.controls!!.actions[Ear.Right to Press.Single])
            assertEquals(mapOf(Toggle.WearDetection to false, Toggle.GamingMode to false, Toggle.Surround to false, Toggle.DynamicBass to true), state.toggles)
            assertEquals(true, state.multipoint?.enabled)

            buds.apply(Change.Mode(ListeningMode.Normal))
            buds.until { it.sound?.mode == ListeningMode.Normal }
            assertEquals(0x0008, fake.noise)

            buds.apply(Change.Switch(Toggle.GamingMode, true))
            buds.until { it.toggles[Toggle.GamingMode] == true }
            assertEquals(1, fake.switches[6])

            buds.apply(Change.Touch(Ear.Right, Press.Double, TouchAction.NextSong))
            buds.until { it.controls?.actions?.get(Ear.Right to Press.Double) == TouchAction.NextSong }

            buds.apply(Change.Preset(Equalizer.CUSTOM))
            val saved = synchronized(fake.written) { fake.written.last { it.command == Melody.SAVE_EQ } }
            assertEquals(4, saved.at(3))
        }
    }

    @Test
    fun theFitTestGivesEachSidesSeal() {
        val fake = FakeBuds4()
        withBuds(fake) { buds ->
            assertEquals(true, buds.until { it.isReady && it.firmware != null }.canFitTest)
            // Fit news is asked for along with battery, in-ear and noise mode.
            val subscribed = synchronized(fake.written) { fake.written.first { it.command == Melody.SUBSCRIBE } }
            assertEquals(listOf(4, 1, 2, 3, 4), subscribed.payload.map { it.toInt() })
            assertEquals(FitResult(left = true, right = false), buds.fitTest())
            // Started, then stopped once the verdict was in.
            val sent = synchronized(fake.written) { fake.written.filter { it.command == Melody.FIT_TEST }.map { it.at(0) } }
            assertEquals(listOf(1, 0), sent)
        }
    }

    @Test
    fun aRefusedChannelShowsAsBusy() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val buds = MelodyBuds("OnePlus Buds 3", scope, listOf({ throw LinkException("refused", busy = true) }))
            buds.start()
            val state = withTimeout(10_000) { buds.state.first { it.link == Link.Busy } }
            assertNull(state.sound)
            buds.close()
            scope.cancel()
        }
    }
}
