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

    /**
     * Whether someone is looking. While they are, the app asks the earbuds every few seconds, so a change made from
     * another app or on the earbuds themselves shows almost at once; while nobody is, it rests.
     */
    fun watch(on: Boolean) {}

    /** Makes the chosen earbuds play a loud sound so they can be found, or stops it. */
    suspend fun ring(left: Boolean, right: Boolean) {}

    /** Plays a short tone and listens to how well each ear tip seals; null if these earbuds can't. */
    suspend fun fitTest(): FitResult? = null

    fun close()
}

data class BudsState(
    val name: String,
    val model: String,
    val link: Link = Link.Connecting,
    val left: Battery? = null,
    val right: Battery? = null,
    val case: Battery? = null,
    /** One level for the whole pair, when that is all the phone or computer can tell. */
    val overall: Battery? = null,
    val sound: Sound? = null,
    val equalizer: Equalizer? = null,
    val controls: TouchControls? = null,
    val toggles: Map<Toggle, Boolean> = emptyMap(),
    val multipoint: Multipoint? = null,
    val autoPowerOff: AutoPowerOff? = null,
    /** A cap on how loud the earbuds play, to look after hearing. */
    val volumeLimit: VolumeLimit? = null,
    val firmware: String? = null,
    val serial: String? = null,
    /** True when both earbuds are out and talking to each other, not only one. */
    val bothConnected: Boolean = true,
    /** These earbuds can play a sound to be found. */
    val canRing: Boolean = false,
    /** These earbuds can check how well their tips seal. */
    val canFitTest: Boolean = false,
    /** A sound shaped to the person's ears, kept on the earbuds; null if these earbuds can't hold one. */
    val earPrint: EarPrintState? = null,
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
/** A battery level. [seenAt] is set (seconds since 1970) when it is the last level heard, not a live one. */
data class Battery(val percent: Int, val charging: Boolean = false, val seenAt: Long? = null)

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
    /** What these earbuds let you change beyond the mode itself. */
    val options: SoundOptions = SoundOptions(),
)

/** The finer settings a brand offers for each mode; the screens show only these. */
data class SoundOptions(
    /** The ways of cancelling to choose from (Adaptive, Manual, Travel); empty for one way only. */
    val cancelStyles: List<CancelStyle> = CancelStyle.entries,
    val transparencyStyles: Boolean = true,
    val wind: Boolean = true,
    val environment: Boolean = true,
    val cycle: Boolean = true,
    /** The modes these earbuds have at all. */
    val modes: List<ListeningMode> = ListeningMode.entries,
)

data class Equalizer(
    val presetId: Int,
    val presets: List<EqPreset>,
    /** Centre of each band, in hertz. */
    val frequencies: List<Int>,
    /** Gain of each band in decibels, for the left ear, and for the right when the two differ. */
    val left: List<Double>,
    val right: List<Double> = left,
    /** Whether the bands can be set one by one, or only a preset picked. */
    val custom: Boolean = true,
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

enum class Press { Single, Double, Long, Triple }

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
    /** Where a kind of touch has its own shorter list, that list. */
    val availableFor: Map<Press, List<TouchAction>> = emptyMap(),
) {
    fun choices(press: Press): List<TouchAction> = availableFor[press] ?: available
}

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

/** The EarPrint the earbuds hold: on or off, a curve for each ear, and when it was made (seconds since 1970). */
data class EarPrintState(val on: Boolean, val left: List<Double>, val right: List<Double>, val madeAt: Long)

/** How well each ear tip seals: true is a good seal, false a poor one, null not heard. */
data class FitResult(val left: Boolean?, val right: Boolean?)

data class VolumeLimit(val enabled: Boolean, val decibels: Int, val choices: List<Int> = listOf(75, 80, 85, 90, 95, 100))

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
    data class SafeVolume(val enabled: Boolean, val decibels: Int) : Change
    /** Plays an EarPrint for comparing, without keeping it. */
    data class EarPrintTry(val left: List<Double>, val right: List<Double>) : Change
    /** Keeps an EarPrint on the earbuds and turns it on. */
    data class EarPrintSave(val left: List<Double>, val right: List<Double>, val madeAt: Long) : Change
    data class EarPrintOn(val on: Boolean) : Change
    /** The earbuds' quiet state for the hearing test. */
    data class HearingTestMode(val on: Boolean) : Change
    data class HostConnection(val address: String, val connect: Boolean) : Change
    data class Forget(val address: String) : Change
    data object ResetControls : Change
}
