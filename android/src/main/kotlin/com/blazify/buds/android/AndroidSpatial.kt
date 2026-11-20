package com.blazify.buds.android

import android.content.Context
import android.content.Intent
import android.os.Build
import com.blazify.buds.core.spatial.SpatialMode
import com.blazify.buds.ui.Spatial
import com.blazify.buds.ui.SpatialState
import kotlinx.coroutines.flow.StateFlow

/**
 * The 3D switch on Android. Turning it on asks Android, through the activity, to let the app capture what's
 * playing; the answer starts [SpatialService].
 */
class AndroidSpatial(private val context: Context, private val ask: (onAllowed: (code: Int, data: Intent) -> Unit) -> Unit) : Spatial {
    override val state: StateFlow<SpatialState> = SpatialService.state

    override fun turnOn(mode: SpatialMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            SpatialService.state.value = SpatialState.Blocked("Needs Android 10 or later")
            return
        }
        SpatialService.mode = mode
        SpatialService.state.value = SpatialState.Starting
        ask { code, data ->
            val start = Intent(context, SpatialService::class.java)
                .putExtra(SpatialService.CODE, code)
                .putExtra(SpatialService.DATA, data)
                .putExtra(SpatialService.MODE, mode.name)
            context.startForegroundService(start)
        }
    }

    override fun turnOff() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        context.startService(Intent(context, SpatialService::class.java).setAction(SpatialService.STOP))
        SpatialService.state.value = SpatialState.Off
    }

    override fun mode(mode: SpatialMode) {
        SpatialService.mode = mode
        val now = SpatialService.state.value
        if (now is SpatialState.On) SpatialService.state.value = now.copy(mode = mode)
    }
}
