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
import com.blazify.buds.core.soundcore.generic.SoundcorePreset

/** The Liberty 3 Pro's touches: single, double and long presses with their own "on" bits; turning one side off turns both off. */
private val LIBERTY_3_PRO_TOUCHES = ButtonLayout(
    order = NewerParts.SINGLE_DOUBLE_LONG,
    presses = listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, Actions.COMMON),
        PressSetup(PressKind.Double, 0, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, Actions.COMMON),
        PressSetup(PressKind.Long, 1, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.BothSides, Actions.COMMON),
    ),
    setAll = false,
)

/** The Liberty 3 Pro's own tuning of Soundcore's presets. */
private val LIBERTY_3_PRO_PRESETS = listOf(
    SoundcorePreset(0, "Signature", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(1, "Acoustic", intArrayOf(30, -10, 0, 0, 20, 10, 10, -10, 0, -120)),
    SoundcorePreset(2, "Bass booster", intArrayOf(50, 30, 10, 0, 0, -10, -10, -10, 0, -120)),
    SoundcorePreset(3, "Bass reducer", intArrayOf(-40, -30, -10, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(4, "Classical", intArrayOf(30, 20, -30, -30, -10, 0, 10, 20, 0, -120)),
    SoundcorePreset(5, "Podcast", intArrayOf(-40, 10, 30, 0, -40, -40, -30, -60, 0, -120)),
    SoundcorePreset(6, "Dance", intArrayOf(20, -30, -10, 10, 20, 20, 10, -30, 0, -120)),
    SoundcorePreset(7, "Deep", intArrayOf(20, 10, 30, 30, 20, -20, -40, -50, 0, -120)),
    SoundcorePreset(8, "Electronic", intArrayOf(30, 10, -30, 10, 0, 0, 10, 10, 0, -120)),
    SoundcorePreset(9, "Flat", intArrayOf(-20, -20, -10, 0, 0, 0, -20, -20, 0, -120)),
    SoundcorePreset(10, "Hip-hop", intArrayOf(20, 30, -10, -10, 20, -10, 20, 30, 0, -120)),
    SoundcorePreset(11, "Jazz", intArrayOf(10, 20, -20, -20, 0, 0, 10, 20, 0, -120)),
    SoundcorePreset(12, "Latin", intArrayOf(-10, 0, -20, -20, -20, -20, 10, 30, 0, -120)),
    SoundcorePreset(13, "Lounge", intArrayOf(-20, 20, 40, 30, 0, -30, 10, 0, 0, -120)),
    SoundcorePreset(14, "Piano", intArrayOf(0, 0, 0, -30, -10, -40, -30, -60, 0, -120)),
    SoundcorePreset(15, "Pop", intArrayOf(-10, 10, 30, 30, 10, -10, -20, -30, 0, -120)),
    SoundcorePreset(16, "R&B", intArrayOf(40, 0, -40, -40, 0, 0, 0, 10, 0, -120)),
    SoundcorePreset(17, "Rock", intArrayOf(20, 0, -30, -30, -10, 0, 10, 20, 0, -120)),
    SoundcorePreset(18, "Small speakers", intArrayOf(40, 30, 10, 0, -20, -30, -40, -40, 0, -120)),
    SoundcorePreset(19, "Spoken word", intArrayOf(-10, -20, 10, 20, -40, -30, -50, -60, 0, -120)),
    SoundcorePreset(20, "Treble booster", intArrayOf(-20, -20, -20, -10, 10, 20, 20, 40, 0, -120)),
    SoundcorePreset(21, "Treble reducer", intArrayOf(0, 0, 0, -20, -30, -60, -40, -60, 0, -120)),
)

/** Liberty 3 Pro (A3952). */
val A3952 = SoundcoreModel(
    code = "A3952",
    name = "Liberty 3 Pro",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3952",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(2, 10),
        Piece.Age,
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(LIBERTY_3_PRO_TOUCHES),
        Piece.Skip(4), // unknown
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.WearDetection),
        Piece.Skip(1), // unknown
        Piece.CaseLevel,
        Piece.Skip(2), // unknown
        Piece.Switch(Toggle.Ldac),
        Piece.Skip(2), // unknown
        Piece.Switch(Toggle.WearingTone),
        Piece.Skip(1), // unknown
        Piece.PowerOff,
    ),
    battery = Scale(5),
    case = Scale(5),
    // These earbuds take a listening-mode change only in the order Soundcore's app makes it.
    sound = NewerParts.spaceA40Sound(migrate = true),
    equalizer = EqSetup(LIBERTY_3_PRO_PRESETS, EqWrite.HearId, bothNeeded = true),
    buttons = listOf(LIBERTY_3_PRO_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
        Toggle.WearingTone to SoundcoreModel.WEARING_TONE,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
)
