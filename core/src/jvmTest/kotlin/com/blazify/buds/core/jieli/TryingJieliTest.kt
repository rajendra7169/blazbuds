package com.blazify.buds.core.jieli

import com.blazify.buds.core.TryingBuds
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class TryingJieliTest {
    /** Pretend earbuds: answer the JieLi login when [jieli], else say nothing. */
    private class FakeEarbuds(private val jieli: Boolean) : Connection {
        private val out = LinkedBlockingQueue<ByteArray>()
        @Volatile private var closed = false
        override fun read(into: ByteArray): Int {
            while (!closed) {
                val next = out.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                next.copyInto(into)
                return next.size
            }
            return -1
        }
        override fun write(bytes: ByteArray) {
            if (!jieli) return
            when {
                bytes.size == 17 && bytes[0].toInt() == 0 -> out.add(JieliAuth.respond(bytes.copyOfRange(1, 17)))
                bytes.contentEquals(JieliAuth.PASS) -> out.add(byteArrayOf(0) + ByteArray(16) { it.toByte() })
                bytes.size == 17 && bytes[0].toInt() == 1 -> out.add(JieliAuth.PASS)
            }
        }
        override fun close() { closed = true }
    }

    private class Basic : Buds {
        override val state = MutableStateFlow(BudsState("basic", "", Link.Connected))
        override suspend fun apply(change: Change) {}
        override suspend fun refresh() {}
        override fun close() {}
    }

    private fun attempt(jieli: Boolean): Pair<String, Boolean?> = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var remembered: Boolean? = null
        val buds = TryingBuds(
            scope,
            { verdict -> JieliBuds("pair", scope, { FakeEarbuds(jieli) }, verdict = verdict).also { it.start() } },
            { Basic() },
            { remembered = it },
        )
        val state = withTimeout(15_000) { buds.state.first { it.name == "basic" || (it.name == "pair" && it.link == Link.Connected) } }
        buds.close()
        scope.cancel()
        state.name to remembered
    }

    @Test
    fun earbudsThatLogInStayOnJieli() = assertEquals("pair" to true, attempt(jieli = true))

    @Test
    fun earbudsThatDontFallBackToTheBasics() = assertEquals("basic" to false, attempt(jieli = false))
}
