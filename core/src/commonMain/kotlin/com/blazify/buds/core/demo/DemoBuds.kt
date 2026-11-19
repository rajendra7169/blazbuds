package com.blazify.buds.core.demo

import com.blazify.buds.core.model.AutoPowerOff
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Multipoint
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Earbuds that exist only in the app: for trying it without a pair, and for the screenshots. */
class DemoBuds(connected: Boolean = true) : Buds {
    private val flow = MutableStateFlow(sample().let { if (connected) it else it.copy(link = com.blazify.buds.core.model.Link.Lost) })
    override val state: StateFlow<BudsState> = flow

    override suspend fun apply(change: Change) {
        flow.update { it.with(change) }
    }

    override suspend fun refresh() {}

    override fun close() {}

    override suspend fun fitTest(): com.blazify.buds.core.model.FitResult {
        kotlinx.coroutines.delay(2500)
        return com.blazify.buds.core.model.FitResult(left = true, right = false)
    }

    companion object {
        val FREQUENCIES = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)

        fun sample(): BudsState {
            val presets = listOf(
                EqPreset(0, "Signature", List(8) { 0.0 }),
                EqPreset(1, "Acoustic", listOf(4.0, 3.0, 2.0, 1.0, 1.0, 2.0, 3.0, 2.0)),
                EqPreset(2, "Bass booster", listOf(5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
                EqPreset(3, "Podcast", listOf(-2.0, -1.0, 1.0, 3.0, 3.0, 2.0, 0.0, -1.0)),
                EqPreset(4, "Treble booster", listOf(0.0, 0.0, 0.0, 0.0, 1.0, 3.0, 4.0, 5.0)),
                EqPreset(5, "Vocal", listOf(-2.0, -1.0, 0.0, 2.0, 4.0, 3.0, 1.0, 0.0)),
            )
            return BudsState(
                name = "Liberty 4 NC",
                model = "A3947",
                link = Link.Connected,
                left = Battery(80),
                right = Battery(60),
                case = Battery(100, charging = true),
                sound = Sound(ListeningMode.NoiseCancelling),
                equalizer = Equalizer(1, presets, FREQUENCIES, presets[1].gains),
                controls = TouchControls(
                    actions = mapOf(
                        (Ear.Left to Press.Single) to TouchAction.VolumeDown,
                        (Ear.Right to Press.Single) to TouchAction.VolumeUp,
                        (Ear.Left to Press.Double) to TouchAction.PlayPause,
                        (Ear.Right to Press.Double) to TouchAction.NextSong,
                        (Ear.Left to Press.Long) to TouchAction.ListeningMode,
                        (Ear.Right to Press.Long) to TouchAction.ListeningMode,
                    ),
                    available = TouchAction.entries,
                ),
                toggles = mapOf(
                    Toggle.Ldac to false,
                    Toggle.GamingMode to false,
                    Toggle.Surround to false,
                    Toggle.WearDetection to true,
                    Toggle.TouchTone to true,
                    Toggle.WearingTone to true,
                    Toggle.TouchLock to false,
                    Toggle.LowBatteryPrompt to true,
                ),
                multipoint = Multipoint(
                    enabled = true,
                    devices = listOf(
                        Host("Pixel 9", "AA:BB:CC:00:11:22", connected = true, playing = true),
                        Host("ThinkPad", "AA:BB:CC:33:44:55", connected = true),
                    ),
                ),
                autoPowerOff = AutoPowerOff(true, 30, listOf(10, 20, 30, 60)),
                volumeLimit = com.blazify.buds.core.model.VolumeLimit(false, 85),
                firmware = "07.61",
                canRing = true,
                canFitTest = true,
                earPrint = com.blazify.buds.core.model.EarPrintState(false, List(8) { 0.0 }, List(8) { 0.0 }, 0),
                serial = "3947DEMO00000000",
            )
        }
    }
}

/** What a change does to the state, shared by every driver for showing it before the earbuds confirm. */
fun BudsState.with(change: Change): BudsState = when (change) {
    is Change.Mode -> copy(sound = sound?.copy(mode = change.mode))
    is Change.Cancelling -> copy(
        sound = sound?.copy(
            mode = ListeningMode.NoiseCancelling,
            cancelStyle = change.style,
            manualLevel = change.level ?: sound.manualLevel,
            travel = change.travel ?: sound.travel,
        ),
    )
    is Change.TransparencyKind -> copy(sound = sound?.copy(mode = ListeningMode.Transparency, transparencyStyle = change.style))
    is Change.Wind -> copy(sound = sound?.copy(windReduction = change.on))
    is Change.Environment -> copy(sound = sound?.copy(environmentDetection = change.on))
    is Change.ModeCycle -> copy(sound = sound?.copy(cycle = change.modes))
    is Change.Preset -> copy(
        equalizer = equalizer?.let { eq ->
            val preset = eq.presets.firstOrNull { it.id == change.id }
            eq.copy(presetId = change.id, left = preset?.gains ?: eq.left, right = preset?.gains ?: eq.right)
        },
    )
    is Change.Bands -> copy(equalizer = equalizer?.copy(presetId = Equalizer.CUSTOM, left = change.left, right = change.right))
    is Change.Switch -> {
        val next = toggles + (change.toggle to change.on)
        // High quality and two devices at once can't both be on.
        val points = if (change.toggle == Toggle.Ldac && change.on) multipoint?.copy(enabled = false) else multipoint
        copy(toggles = next, multipoint = points)
    }
    is Change.Touch -> copy(controls = controls?.copy(actions = controls.actions + ((change.ear to change.press) to change.action)))
    is Change.PowerOff -> copy(autoPowerOff = autoPowerOff?.copy(enabled = change.enabled, minutes = change.minutes))
    is Change.TwoDevices -> copy(
        multipoint = multipoint?.copy(enabled = change.on),
        toggles = if (change.on) toggles + (Toggle.Ldac to false) else toggles,
    )
    is Change.HostConnection -> copy(
        multipoint = multipoint?.copy(
            devices = multipoint.devices.map { if (it.address == change.address) it.copy(connected = change.connect) else it },
        ),
    )
    is Change.Forget -> copy(multipoint = multipoint?.copy(devices = multipoint.devices.filterNot { it.address == change.address }))
    is Change.SafeVolume -> copy(volumeLimit = volumeLimit?.copy(enabled = change.enabled, decibels = change.decibels))
    is Change.EarPrintTry -> this
    is Change.EarPrintSave -> copy(earPrint = com.blazify.buds.core.model.EarPrintState(true, change.left, change.right, change.madeAt))
    is Change.EarPrintOn -> copy(earPrint = earPrint?.copy(on = change.on))
    is Change.HearingTestMode -> this
    Change.ResetControls -> this
}
