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

/** The Space A40's touches: single, double and long presses, each with its own "on" bits; turning one side off turns both off. */
private val SPACE_A40_TOUCHES = ButtonLayout(
    order = NewerParts.SINGLE_DOUBLE_LONG,
    presses = listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, NewerParts.MINIMAL_ACTIONS),
        PressSetup(PressKind.Double, 0, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, Actions.WITH_GAMING),
        PressSetup(PressKind.Long, 1, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, Actions.WITH_GAMING),
    ),
    setAll = false,
)

/** Space A40 (A3936). */
val A3936 = SoundcoreModel(
    code = "A3936",
    name = "Space A40",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3936",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(2, 10),
        Piece.Age,
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(SPACE_A40_TOUCHES),
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Switch(Toggle.TouchTone),
        Piece.CaseLevel,
        Piece.Skip(1), // colour
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Switch(Toggle.GamingMode),
    ),
    battery = Scale(5),
    case = Scale(10),
    sound = NewerParts.spaceA40Sound(migrate = false),
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.HearIdAlways, bothNeeded = true),
    buttons = listOf(SPACE_A40_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
    twoDevices = true,
)
