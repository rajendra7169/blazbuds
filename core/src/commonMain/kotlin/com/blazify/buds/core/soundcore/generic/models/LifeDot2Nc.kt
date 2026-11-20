package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** The Life Dot 2 NC's touches: the usual six, none of which can be turned off, each set one at a time. */
private val LIFE_DOT_2_NC_BUTTONS = ButtonLayout(
    Actions.COMMON_ORDER,
    listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.COMMON),
        PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.COMMON),
        PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.Single, DisableMode.Never, Actions.COMMON),
    ),
    // OpenSCQ30 doesn't know whether it takes every touch in one message, so it never tries.
    setAll = false,
)

/** Life Dot 2 NC (A3931), which the Life A2 NC (A3935) shares. */
val A3931 = SoundcoreModel(
    code = "A3931",
    name = "Life Dot 2 NC",
    aliases = listOf("Life A2 NC"),
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Eq(2, 8),
        Piece.Buttons(LIFE_DOT_2_NC_BUTTONS),
        Piece.Sound(4),
        Piece.Skip(1), // side tone
        Piece.Switch(Toggle.TouchTone),
        Piece.PowerOff,
    ),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Outdoor, CancelStyle.Indoor),
        voiceTransparency = true,
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Drc, bothNeeded = true),
    buttons = listOf(LIFE_DOT_2_NC_BUTTONS),
    resetButtons = true,
    switches = mapOf(Toggle.TouchTone to SoundcoreModel.TOUCH_TONE),
    powerOff = listOf(5, 10, 30, 60),
    askFirmware = true,
)
