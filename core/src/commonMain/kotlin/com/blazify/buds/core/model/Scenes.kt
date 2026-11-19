package com.blazify.buds.core.model

/** A named set of settings put on with one tap: the mode, how it cancels, and optionally a sound. */
data class Scene(
    val id: String,
    val name: String,
    val mode: ListeningMode,
    val cancelStyle: CancelStyle? = null,
    val travel: Travel? = null,
    val transparency: TransparencyStyle? = null,
    val presetId: Int? = null,
) {
    fun changes(): List<Change> = buildList {
        when (mode) {
            ListeningMode.NoiseCancelling -> add(Change.Cancelling(cancelStyle ?: CancelStyle.Adaptive, travel = travel))
            ListeningMode.Transparency -> add(Change.TransparencyKind(transparency ?: TransparencyStyle.Full))
            ListeningMode.Normal -> add(Change.Mode(ListeningMode.Normal))
        }
        if (presetId != null) add(Change.Preset(presetId))
    }

    /** Whether the earbuds are set this way now. */
    fun matches(state: BudsState): Boolean {
        val sound = state.sound ?: return false
        if (sound.mode != mode) return false
        if (mode == ListeningMode.NoiseCancelling && cancelStyle != null && sound.cancelStyle != cancelStyle) return false
        if (cancelStyle == CancelStyle.Travel && travel != null && sound.travel != travel) return false
        if (mode == ListeningMode.Transparency && transparency != null && sound.transparencyStyle != transparency) return false
        if (presetId != null && state.equalizer?.presetId != presetId) return false
        return true
    }

    companion object {
        val DEFAULTS = listOf(
            Scene("focus", "Focus", ListeningMode.NoiseCancelling, CancelStyle.Adaptive),
            Scene("commute", "Commute", ListeningMode.NoiseCancelling, CancelStyle.Travel, travel = Travel.Bus),
            Scene("flight", "Flight", ListeningMode.NoiseCancelling, CancelStyle.Travel, travel = Travel.Plane),
            Scene("talk", "Talk", ListeningMode.Transparency, transparency = TransparencyStyle.Voice),
            Scene("relax", "Relax", ListeningMode.Normal),
        )
    }
}
