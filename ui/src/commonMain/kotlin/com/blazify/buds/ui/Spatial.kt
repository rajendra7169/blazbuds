package com.blazify.buds.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.blazify.buds.core.spatial.SpatialMode
import kotlinx.coroutines.flow.StateFlow

/** 3D for whatever is playing on the device, where the system lets an app reach other apps' sound. */
interface Spatial {
    val state: StateFlow<SpatialState>
    fun turnOn(mode: SpatialMode)
    fun turnOff()
    fun mode(mode: SpatialMode)
}

sealed interface SpatialState {
    data object Off : SpatialState
    data object Starting : SpatialState
    data class On(val mode: SpatialMode, val apps: Int) : SpatialState

    /** Can't run yet; [why] says what's missing, in the person's words. */
    data class Blocked(val why: String) : SpatialState
}

val LocalSpatial = staticCompositionLocalOf<Spatial?> { null }
