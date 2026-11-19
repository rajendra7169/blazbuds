package com.blazify.buds.core

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Any earbuds, whatever their brand: what the phone or computer itself knows, which is whether they are connected
 * and one battery level. Asked again every half minute.
 */
class BasicBuds(
    name: String,
    private val scope: CoroutineScope,
    private val look: suspend () -> Pair<Boolean, Int?>,
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    override val state: StateFlow<BudsState> = flow
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(30_000)
            }
        }
    }

    override suspend fun refresh() {
        val (connected, percent) = withContext(Dispatchers.IO) { runCatching { look() }.getOrDefault(false to null) }
        flow.value = flow.value.copy(
            link = if (connected) Link.Connected else Link.Lost,
            overall = percent?.let { Battery(it) },
        )
    }

    override suspend fun apply(change: Change) {}

    override fun close() {
        job?.cancel()
    }
}
