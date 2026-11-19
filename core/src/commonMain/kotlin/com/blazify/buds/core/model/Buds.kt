package com.blazify.buds.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * A pair of earbuds the app is talking to, whatever their brand.
 *
 * Each brand has its own driver behind this. What a pair can't do is simply null in its [BudsState], so screens show
 * only what these earbuds really have.
 */
interface Buds {
    val state: StateFlow<BudsState>

    /** Changes a setting. The state shows the change at once and goes back if the earbuds don't take it. */
    suspend fun apply(change: Change)

    /** Asks the earbuds for everything again. */
    suspend fun refresh()

    fun close()
}

data class BudsState(
    val name: String,
    val model: String,
    val link: Link = Link.Connecting,
    val left: Battery? = null,
    val right: Battery? = null,
    val case: Battery? = null,
    val sound: Sound? = null,
    val equalizer: Equalizer? = null,
    val controls: TouchControls? = null,
    val toggles: Map<Toggle, Boolean> = emptyMap(),
    val multipoint: Multipoint? = null,
    val autoPowerOff: AutoPowerOff? = null,
    val firmware: String? = null,
    val serial: String? = null,
    /** True when both earbuds are out and talking to each other, not only one. */
    val bothConnected: Boolean = true,
) {
    val isReady: Boolean get() = link == Link.Connected
}

enum class Link {
    Connecting,
    Connected,

    /** Another app, often the brand's own one, holds the earbuds' control channel. */
    Busy,
    Lost,
}

/** [percent] is rounded to the earbuds' own steps: Soundcore reports in fifths, so 20, 40, 60, 80 or 100. */
data class Battery(val percent: Int, val charging: Boolean = false)

enum class ListeningMode { NoiseCancelling, Transparency, Normal }

enum class CancelStyle { Adaptive, Manual, Travel }

enum class Travel { Plane, Train, Bus, Car }

enum class TransparencyStyle { Full, Voice }

data class Sound(
    val mode: ListeningMode,
    val cancelStyle: CancelStyle = CancelStyle.Adaptive,
    /** 1 (light) to 5 (strongest). */
    val manualLevel: Int = 5,
    val travel: Travel = Travel.Plane,
    val transparencyStyle: TransparencyStyle = TransparencyStyle.Full,
    val windReduction: Boolean = false,
    /** Adaptive cancelling also adjusts to the surroundings, not only to the fit. */
    val environmentDetection: Boolean = true,
    /** Which of the three modes a touch on the earbuds cycles through. */
    val cycle: Set<ListeningMode> = ListeningMode.entries.toSet(),
)

data class Equalizer(
    val presetId: Int,
    val presets: List<EqPreset>,
    /** Centre of each band, in hertz. */
    val frequencies: List<Int>,
    /** Gain of each band in decibels, for the left ear, and for the right when the two differ. */
    val left: List<Double>,
    val right: List<Double> = left,
) {
    val preset: EqPreset? get() = presets.firstOrNull { it.id == presetId }

    companion object {
        const val CUSTOM = 0xFE
        const val MIN_DB = -6.0
        const val MAX_DB = 6.0
    }
}

data class EqPreset(val id: Int, val name: String, val gains: List<Double>)

enum class Ear { Left, Right }

enum class Press { Single, Double, Long }

/** What a touch does. Not every pair offers every action. */
enum class TouchAction {
    VolumeUp,
    VolumeDown,
    PreviousSong,
    NextSong,
    PlayPause,
    VoiceAssistant,
    ListeningMode,
    None,
}

data class TouchControls(
    val actions: Map<Pair<Ear, Press>, TouchAction>,
    val available: List<TouchAction>,
)

/** Switches that are simply on or off. */
enum class Toggle {
    /** High-quality sound over LDAC. Most pairs then connect to one device only. */
    Ldac,
    GamingMode,
    Surround,
    WearDetection,
    TouchTone,
    WearingTone,
    TouchLock,
    LowBatteryPrompt,
    SoundLeakCompensation,
}

data class Multipoint(val enabled: Boolean, val devices: List<Host> = emptyList())

data class Host(val name: String, val address: String, val connected: Boolean, val playing: Boolean = false)

data class AutoPowerOff(val enabled: Boolean, val minutes: Int, val choices: List<Int>)

/** Everything a screen can ask the earbuds to change. */
sealed interface Change {
    data class Mode(val mode: ListeningMode) : Change
    data class Cancelling(val style: CancelStyle, val level: Int? = null, val travel: Travel? = null) : Change
    data class TransparencyKind(val style: TransparencyStyle) : Change
    data class Wind(val on: Boolean) : Change
    data class Environment(val on: Boolean) : Change
    data class ModeCycle(val modes: Set<ListeningMode>) : Change
    data class Preset(val id: Int) : Change
    data class Bands(val left: List<Double>, val right: List<Double> = left) : Change
    data class Switch(val toggle: Toggle, val on: Boolean) : Change
    data class Touch(val ear: Ear, val press: Press, val action: TouchAction) : Change
    data class PowerOff(val enabled: Boolean, val minutes: Int) : Change
    data class TwoDevices(val on: Boolean) : Change
    data class HostConnection(val address: String, val connect: Boolean) : Change
    data class Forget(val address: String) : Change
    data object ResetControls : Change
}
