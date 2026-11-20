package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
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
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.SoundcorePreset

/** Single, double and long press, left then right: the order the Life P2 Mini, A20i and P20i keep their touches in. */
internal val SINGLE_DOUBLE_LONG_ORDER = listOf(
    Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
    Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
    Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
)

/**
 * The Life P2 Mini's touches: each can be turned off on its own side, which these earbuds keep as action 0xF rather
 * than in the "on" byte, and each is set one at a time.
 */
private val LIFE_P2_MINI_BUTTONS = ButtonLayout(
    SINGLE_DOUBLE_LONG_ORDER,
    listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.Single, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/** The usual presets, but with the Life P2 Mini's own, stronger Bass booster. */
private val LIFE_P2_MINI_PRESETS = EqSetup.PRESETS_TYPE_2.map { preset ->
    if (preset.id == 2) SoundcorePreset(2, preset.name, intArrayOf(50, 50, 30, 10, 10, 20, -10, -20, 0, -120)) else preset
}

/** Life P2 Mini (A3944). */
val A3944 = SoundcoreModel(
    code = "A3944",
    name = "Life P2 Mini",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3944",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(11), // not known
        Piece.Buttons(LIFE_P2_MINI_BUTTONS),
        Piece.Skip(5), // not known
        Piece.Switch(Toggle.TouchTone),
    ),
    // Presets only: the earbuds have no bands set by hand.
    equalizer = EqSetup(LIFE_P2_MINI_PRESETS, EqWrite.Drc, custom = false),
    buttons = listOf(LIFE_P2_MINI_BUTTONS),
    switches = mapOf(Toggle.TouchTone to SoundcoreModel.TOUCH_TONE),
)
