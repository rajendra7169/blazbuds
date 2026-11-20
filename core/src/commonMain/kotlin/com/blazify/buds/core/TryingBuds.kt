package com.blazify.buds.core

import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Earbuds the app doesn't know by name: tries a driver that might fit (made with a way to say "not these"), and
 * falls back to the basics if it doesn't. Whatever is learnt is passed to [remember], so the next time goes straight
 * to the right one.
 */
class TryingBuds(
    private val scope: CoroutineScope,
    tryFirst: (verdict: (Boolean) -> Unit) -> Buds,
    private val fallback: () -> Buds,
    private val remember: (Boolean) -> Unit,
) : Buds {
    private val flow = MutableStateFlow(BudsState("", ""))
    override val state: StateFlow<BudsState> = flow
    private var current: Buds
    private var following: Job? = null
    private var watching = true

    init {
        current = tryFirst { works ->
            remember(works)
            if (!works) scope.launch { switchTo(fallback()) }
        }
        follow()
    }

    private fun follow() {
        following?.cancel()
        val source = current
        following = scope.launch { source.state.collect { flow.value = it } }
    }

    private fun switchTo(next: Buds) {
        current.close()
        current = next
        current.watch(watching)
        follow()
    }

    override suspend fun refresh() = current.refresh()
    override suspend fun apply(change: Change) = current.apply(change)
    override fun watch(on: Boolean) {
        watching = on
        current.watch(on)
    }
    override suspend fun fitTest() = current.fitTest()
    override suspend fun ring(left: Boolean, right: Boolean) = current.ring(left, right)
    override fun close() {
        following?.cancel()
        current.close()
    }
}
