package com.blazify.buds.android

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.blazify.buds.core.model.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A quick-settings tile that shows the listening mode and moves to the next one on a tap. */
class ModeTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        show(Remote.live?.state?.value?.takeIf { it.isReady }?.sound?.mode ?: Remote.snapshot()?.mode)
    }

    override fun onClick() {
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Changing…"
            updateTile()
        }
        scope.launch {
            val next = withContext(Dispatchers.Default) { Remote.cycle(applicationContext) }
            show(next ?: Remote.snapshot()?.mode, failed = next == null)
            BudsWidget.refresh(applicationContext)
        }
    }

    private fun show(mode: ListeningMode?, failed: Boolean = false) {
        val tile = qsTile ?: return
        tile.label = when (mode) {
            ListeningMode.NoiseCancelling -> "Noise cancelling"
            ListeningMode.Transparency -> "Transparency"
            ListeningMode.Normal -> "Normal"
            null -> "Earbuds"
        }
        // Android 10 and later show a second line under the name.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (failed) "Not connected" else "Blazbuds"
        tile.state = if (mode == ListeningMode.NoiseCancelling || mode == ListeningMode.Transparency) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
