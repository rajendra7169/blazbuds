package com.blazify.buds.core.link

import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.SoundcoreBuds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs only with BUDS_ADDRESS set and BUDS_WRITE=1: connects to a real Liberty 4 NC, changes two settings and puts
 * each back as it was.
 */
class RealDriverTest {
    @Test
    fun changesAndRestoresSettings() = runBlocking {
        val address = System.getenv("BUDS_ADDRESS") ?: return@runBlocking
        if (System.getenv("BUDS_WRITE") != "1") return@runBlocking
        val buds = SoundcoreBuds("Liberty 4 NC", this, { LinuxBluetooth.open(address, A3947.SERVICE) }) { println(it) }
        buds.start()
        try {
        val first = withTimeout(15_000) { buds.state.first { it.link == Link.Connected } }
        delay(3_000)
        println("connected: battery ${first.left?.percent}/${first.right?.percent}/${first.case?.percent}, mode ${first.sound?.mode}, hosts ${first.multipoint}")
        val tone = first.toggles[Toggle.TouchTone] == true
        buds.apply(Change.Switch(Toggle.TouchTone, !tone))
        assertEquals(!tone, buds.state.value.toggles[Toggle.TouchTone])
        buds.refresh()
        assertEquals(!tone, buds.state.value.toggles[Toggle.TouchTone], "the earbuds kept the change")
        buds.apply(Change.Switch(Toggle.TouchTone, tone))
        val mode = first.sound!!.mode
        val other = if (mode == ListeningMode.Transparency) ListeningMode.Normal else ListeningMode.Transparency
        buds.apply(Change.Mode(other))
        buds.refresh()
        assertEquals(other, buds.state.value.sound?.mode, "a report right after the change keeps the new mode")
        delay(3_500)
        buds.refresh()
        println("mode now ${buds.state.value.sound?.mode}")
        assertEquals(other, buds.state.value.sound?.mode)
        delay(2_000)
        buds.apply(Change.Mode(mode))
        delay(3_500)
        buds.refresh()
        assertEquals(mode, buds.state.value.sound?.mode)
        assertEquals(tone, buds.state.value.toggles[Toggle.TouchTone])
        println("restored: mode ${buds.state.value.sound?.mode}, touch tone ${buds.state.value.toggles[Toggle.TouchTone]}")
        } finally {
            println("final: ${buds.state.value.link}, mode ${buds.state.value.sound?.mode}, touch tone ${buds.state.value.toggles[Toggle.TouchTone]}")
            buds.close()
        }
    }
}
