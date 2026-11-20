package com.blazify.buds.android

import android.content.Context
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The earbuds from outside the app's screens: the widget and the quick-settings tile. While the app has them open it
 * uses that link; otherwise it opens one for a moment, makes the change, and lets go.
 */
object Remote {
    /** The pair the app is talking to now, when it is running. */
    @Volatile
    var live: Buds? = null

    private val store by lazy { Settings() }

    fun chosen(): Device? {
        val address = store.getStringOrNull("earbuds") ?: return null
        val name = store.getStringOrNull("earbuds_name") ?: return null
        return Device(name, address, connected = false)
    }

    /** Last state seen, kept so the widget can draw without connecting. */
    fun remember(state: BudsState) {
        if (!state.isReady) return
        store.putString("snap_name", state.name)
        store.putInt("snap_left", state.left?.percent ?: -1)
        store.putInt("snap_right", state.right?.percent ?: -1)
        // With both earbuds out the case can't be heard; the widget keeps its last level instead of going blank.
        state.case?.let { store.putInt("snap_case", it.percent) }
        store.putString("snap_mode", state.sound?.mode?.name ?: "")
    }

    data class Snapshot(val name: String, val left: Int, val right: Int, val case: Int, val mode: ListeningMode?)

    fun snapshot(): Snapshot? {
        val name = store.getStringOrNull("snap_name") ?: return null
        return Snapshot(
            name,
            store.getInt("snap_left", -1),
            store.getInt("snap_right", -1),
            store.getInt("snap_case", -1),
            runCatching { ListeningMode.valueOf(store.getString("snap_mode", "")) }.getOrNull(),
        )
    }

    /** Sets the listening mode, connecting for a moment if the app isn't holding the earbuds. */
    suspend fun setMode(context: Context, mode: ListeningMode): Boolean {
        live?.let { buds ->
            if (buds.state.value.isReady) {
                buds.apply(Change.Mode(mode))
                remember(buds.state.value)
                return true
            }
        }
        return withBuds(context) { buds ->
            buds.apply(Change.Mode(mode))
            remember(buds.state.value)
        }
    }

    /** The next mode after the current one, for the tile. */
    suspend fun cycle(context: Context): ListeningMode? {
        val now = live?.state?.value?.takeIf { it.isReady }?.sound?.mode ?: snapshot()?.mode ?: ListeningMode.Normal
        val next = ListeningMode.entries[(now.ordinal + 1) % ListeningMode.entries.size]
        return if (setMode(context, next)) next else null
    }

    /** The earbuds' state with their batteries, connecting for a moment; null if they can't be reached. */
    suspend fun battery(context: Context): BudsState? {
        val device = chosen() ?: return null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val buds = AndroidPlatform(context.applicationContext).open(device, scope)
        return try {
            withTimeoutOrNull(12_000) {
                buds.state.first { it.link == Link.Connected && (it.left != null || it.right != null || it.overall != null) }
            }?.also { if (it.isReady) remember(it) }
        } finally {
            buds.close()
            scope.cancel()
        }
    }

    private suspend fun withBuds(context: Context, action: suspend (Buds) -> Unit): Boolean {
        val device = chosen() ?: return false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val buds = AndroidPlatform(context.applicationContext).open(device, scope)
        return try {
            val ready = withTimeoutOrNull(9_000) { buds.state.first { it.link == Link.Connected } } ?: return false
            if (ready.sound == null) return false
            action(buds)
            true
        } finally {
            buds.close()
            scope.cancel()
        }
    }
}
