package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/**
 * The A20i's and P20i's touches: single, double and long press, each with a different action for one earbud alone,
 * each able to be turned off on its own side (as action 0xF; the "on" byte isn't used), and each set one at a time.
 */
private val A20I_P20I_BUTTONS = ButtonLayout(
    SINGLE_DOUBLE_LONG_ORDER,
    listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/** A20i (A3948). */
val A3948 = SoundcoreModel(
    code = "A3948",
    name = "A20i",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3948",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(11), // padding
        Piece.Buttons(A20I_P20I_BUTTONS),
        Piece.Skip(5), // padding
        Piece.Switch(Toggle.TouchTone),
        Piece.Skip(15), // padding
    ),
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.Drc, bothNeeded = true),
    buttons = listOf(A20I_P20I_BUTTONS),
    resetButtons = true,
    switches = mapOf(Toggle.TouchTone to SoundcoreModel.TOUCH_TONE),
)

/** P20i (A3949), which the P25i and R50i share. */
val A3949 = SoundcoreModel(
    code = "A3949",
    name = "P20i",
    aliases = listOf("P25i", "R50i"),
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3949",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(11), // not a second channel: Soundcore's app only ever sends one
        Piece.Buttons(A20I_P20I_BUTTONS),
        Piece.Skip(4), // not known
        Piece.Switch(Toggle.GamingMode),
        Piece.Switch(Toggle.TouchTone),
    ),
    // Presets only: the earbuds take no bands set by hand.
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.Drc, bothNeeded = true, custom = false),
    buttons = listOf(A20I_P20I_BUTTONS),
    resetButtons = true,
    switches = mapOf(
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
    ),
)
