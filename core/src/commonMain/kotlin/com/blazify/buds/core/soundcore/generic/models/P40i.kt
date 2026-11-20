package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

private val P40I_TOUCHES = NewerParts.fourTouches()

/**
 * The P40i's listening mode, in the app's order of changes. Fields: mode, transparency, kind of cancelling (manual,
 * adaptive or scene), adaptive strength (weak to strong), manual strength (1 to 5), scene (transport, outdoor, indoor),
 * wind, adaptive sensitivity.
 */
private val P40I_SOUND = NewerParts.AncSound(
    size = 7,
    at = NewerParts.Places(mode = 0, transparency = 1, cancelling = 2, manual = 4, third = 5, wind = 6),
    styles = listOf(CancelStyle.Manual, CancelStyle.Adaptive) + NewerParts.AncSound.SCENES,
    third = NewerParts.Third.Scenes,
    manualLevels = 5,
    decode = { b ->
        intArrayOf(
            NewerParts.mode(b[0]),
            NewerParts.oneOf(b[2], 0, 1),
            NewerParts.oneOf(b[3], 0, 1, 2),
            NewerParts.oneOf(b[1] and 0x0F, 1, 2, 3),
            (b[1] shr 4).coerceIn(1, 5),
            NewerParts.oneOf(b[6], 0, 1, 2, 3),
            NewerParts.wind(b[4]),
            b[5],
        )
    },
    encode = { f -> intArrayOf(f[0], (f[4] shl 4) or f[3], f[1], f[2], f[6], f[7], f[5]) },
    migration = NewerParts.P40I_MIGRATION,
)

/** P40i (A3955). */
val A3955 = SoundcoreModel(
    code = "A3955",
    name = "P40i",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3955",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // unknown
        Piece.CaseLevel,
        Piece.Eq(2, 10),
        Piece.Own("hearId", 1), // 255: no hearing profile was ever made
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(P40I_TOUCHES),
        Piece.Cycle,
        Piece.Sound(7),
        Piece.Skip(6), // unknown
        Piece.Skip(1), // cancelling fitted to the ear canal
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.TouchTone),
        Piece.TwoDevices,
        Piece.Limit,
        Piece.PowerOff,
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.LowBatteryPrompt),
    ),
    battery = Scale(5),
    case = Scale(5),
    sound = P40I_SOUND,
    equalizer = EqSetup(
        EqSetup.PRESETS_TYPE_2,
        EqWrite.HearIdAlways,
        bothNeeded = true,
        own = NewerParts.p40iEqualizer(made = { p -> p.extra["hearId"]?.first() != 255 }, blankWhenNotMade = true),
    ),
    buttons = listOf(P40I_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    volumeLimit = true,
    twoDevices = true,
)
