package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

private val LIBERTY_5_TOUCHES = NewerParts.fourTouches()

/**
 * The Liberty 5's listening mode, in the app's order of changes. Fields: mode, transparency, kind of cancelling
 * (manual, adaptive or travel), adaptive strength, manual strength (1 to 5), travel (plane 0 or car 3), wind, adaptive
 * sensitivity. Soundcore's app offers manual and travel; adaptive is shown when the earbuds are in it.
 */
private val LIBERTY_5_SOUND = NewerParts.AncSound(
    size = 7,
    at = NewerParts.Places(mode = 0, transparency = 1, cancelling = 2, manual = 4, third = 5, wind = 6),
    styles = listOf(CancelStyle.Manual, CancelStyle.Travel),
    third = NewerParts.Third.Travel,
    manualLevels = 5,
    decode = { b ->
        intArrayOf(
            NewerParts.mode(b[0]),
            NewerParts.oneOf(b[2], 0, 1),
            NewerParts.oneOf(b[3], 0, 1, 2),
            NewerParts.oneOf(b[1] and 0x0F, 0, 1, 2),
            (b[1] shr 4).coerceIn(1, 5),
            NewerParts.oneOf(b[6], 0, 3),
            NewerParts.wind(b[4]),
            b[5],
        )
    },
    encode = { f -> intArrayOf(f[0], (f[4] shl 4) or f[3], f[1], f[2], f[6], f[7], f[5]) },
    migration = NewerParts.P40I_MIGRATION,
)

/** Liberty 5 (A3957). */
val A3957 = SoundcoreModel(
    code = "A3957",
    name = "Liberty 5",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3957",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // "00.00"
        Piece.CaseLevel,
        Piece.Eq(2, 10),
        Piece.Age,
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(LIBERTY_5_TOUCHES),
        Piece.Cycle,
        Piece.Sound(7),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.WearingTone),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Limit,
        Piece.Skip(1), // immersive experience
        Piece.Skip(6), // unknown
        Piece.Switch(Toggle.SoundLeakCompensation),
        Piece.Switch(Toggle.WearDetection),
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.GamingMode),
        // Then how firmly a press must be made, and six unknown bytes.
    ),
    battery = Scale(10, 1),
    case = Scale(10, 1),
    sound = LIBERTY_5_SOUND,
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.HearIdAlways, bothNeeded = true, own = NewerParts.liberty4ProEqualizer()),
    buttons = listOf(LIBERTY_5_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.WearingTone to SoundcoreModel.WEARING_TONE,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
        Toggle.SoundLeakCompensation to SoundcoreModel.SOUND_LEAK,
        // The Liberty 5 has its own command for gaming mode.
        Toggle.GamingMode to Packet.command(0x10, 0x85),
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    volumeLimit = true,
    twoDevices = true,
)
