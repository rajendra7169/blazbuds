package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf

/** The Liberty 4 Pro's taps and presses; its slides only change the volume and have no place in the app. */
private val LIBERTY_4_PRO_TOUCHES = NewerParts.fourTouches()

/**
 * The Liberty 4 Pro's listening mode: the mode (or airplane mode, 3, shown as normal), one slider for both strengths
 * (1 to 5 cancelling, strongest at 1; 6 normal; 7 to 11 transparency), how airplane mode updates, and wind.
 */
private object Liberty4ProSound : NewerParts.FieldSound(4) {
    override fun fields(bytes: IntArray) = intArrayOf(NewerParts.oneOf(bytes[0], 0, 1, 2, 3), bytes[1], NewerParts.oneOf(bytes[2], 0, 1), NewerParts.wind(bytes[3]))

    override fun bytes(fields: IntArray) = fields.copyOf()

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = listOf(CancelStyle.Manual),
        transparencyStyles = false,
        wind = true,
        environment = false,
        cycle = cycle,
        modes = listOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency, ListeningMode.Normal),
        manualLevels = 5,
    )

    override fun show(f: IntArray) = Sound(
        mode = modeOf(f[0]),
        cancelStyle = CancelStyle.Manual,
        manualLevel = if (f[1] in 1..5) 6 - f[1] else 1,
        windReduction = f[3] and 1 != 0,
        options = options(false),
    )

    override fun change(f: IntArray, wanted: Sound) {
        if (wanted.mode != modeOf(f[0])) {
            // As Soundcore's app does, a new mode starts its slider at the middle.
            f[0] = byteOf(wanted.mode)
            f[1] = when (wanted.mode) {
                ListeningMode.NoiseCancelling -> 5
                ListeningMode.Normal -> 6
                ListeningMode.Transparency -> 7
            }
        } else if (f[0] == 0 && wanted.cancelStyle == CancelStyle.Manual) {
            f[1] = 6 - wanted.manualLevel.coerceIn(1, 5)
        }
        f[3] = (f[3] and 0x02) or (if (wanted.windReduction) 1 else 0)
    }
}

/** Liberty 4 Pro (A3954). */
val A3954 = SoundcoreModel(
    code = "A3954",
    name = "Liberty 4 Pro",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3954",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // the case's firmware
        Piece.CaseLevel,
        Piece.Skip(6), // the case's serial number
        Piece.Eq(2, 10),
        Piece.Skip(1), // unknown
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(LIBERTY_4_PRO_TOUCHES),
        Piece.Cycle,
        Piece.Sound(4),
        Piece.Skip(3), // unknown
        Piece.Skip(4), // the case's screen features
        Piece.Skip(1), // air pressure
        Piece.Skip(3), // unknown
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Limit,
        Piece.Skip(3), // spatial audio
        Piece.Skip(1), // easy chat
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.SoundLeakCompensation),
        Piece.Skip(3), // unknown
        Piece.Skip(1), // the case's language
        Piece.Skip(1), // easy chat's wait
        Piece.Switch(Toggle.WearDetection),
        // Then an unknown byte and the slides.
    ),
    battery = Scale(100),
    case = Scale(10, 1),
    sound = Liberty4ProSound,
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.HearIdAlways, bothNeeded = true, own = NewerParts.liberty4ProEqualizer()),
    buttons = listOf(LIBERTY_4_PRO_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.SoundLeakCompensation to SoundcoreModel.SOUND_LEAK,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    volumeLimit = true,
    twoDevices = true,
)
