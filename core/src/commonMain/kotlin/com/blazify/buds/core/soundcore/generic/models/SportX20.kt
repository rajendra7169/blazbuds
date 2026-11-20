package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** The Sport X20's touches: single, double and long presses, one byte each; a touch that does nothing has action 0xF. */
private val SPORT_X20_TOUCHES = ButtonLayout(
    order = NewerParts.SINGLE_DOUBLE_LONG,
    presses = listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, NewerParts.MINIMAL_ACTIONS),
        PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
        PressSetup(PressKind.Long, 1, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/** The equalizer with the hearing profile, the usual way. */
private val HEAR_ID_EQUALIZER = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.HearId, bothNeeded = true)

/** Sport X20 (A3968). */
val A3968 = SoundcoreModel(
    code = "A3968",
    name = "Sport X20",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3968",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // unknown
        Piece.CaseLevel,
        Piece.Eq(2, 10),
        Piece.Skip(1), // unknown
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(SPORT_X20_TOUCHES),
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.Surround),
        Piece.Skip(1), // unknown
        Piece.TwoDevices,
        Piece.Switch(Toggle.TouchTone),
        Piece.PowerOff,
    ),
    battery = Scale(5),
    case = Scale(5),
    // The Space A40's scheme, with its last byte always sent as 0xFF.
    sound = NewerParts.spaceA40Sound(migrate = false, lastByte = 0xFF),
    equalizer = EqSetup(
        EqSetup.PRESETS_TYPE_2,
        EqWrite.HearId,
        bothNeeded = true,
        own = { p, presetId, tenths ->
            // The state has no age range; OpenSCQ30 keeps it at 0, so the hearing profile always goes along (03 87).
            p.age = 0
            HEAR_ID_EQUALIZER.steps(p, presetId, tenths)
        },
    ),
    buttons = listOf(SPORT_X20_TOUCHES),
    cycle = CycleKind.Tws,
    switches = mapOf(
        Toggle.Surround to SoundcoreModel.SURROUND,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    twoDevices = true,
)
