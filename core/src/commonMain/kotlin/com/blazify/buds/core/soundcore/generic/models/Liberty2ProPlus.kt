package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.ListeningMode
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
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/**
 * The Liberty 2 Pro+'s touches: the usual six, none of which can be turned off, none of which switch the listening
 * mode, and changed one at a time (whether they take all at once isn't known).
 */
private val LIBERTY_2_PRO_PLUS_BUTTONS = ButtonLayout(
    order = listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Left, PressKind.Long),
        Slot(Ear.Right, PressKind.Double), Slot(Ear.Right, PressKind.Long),
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
    ),
    presses = listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.Single, DisableMode.Never, Actions.WITHOUT_SOUND_MODES),
    ),
    setAll = false,
)

/** Liberty 2 Pro+ earbuds (A3930). */
val A3930 = SoundcoreModel(
    code = "A3930",
    name = "Liberty 2 Pro+",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Eq(2, 8),
        Piece.Gender,
        Piece.Age,
        Piece.CustomHearId(2, 8),
        Piece.Buttons(LIBERTY_2_PRO_PLUS_BUTTONS),
        Piece.Sound(4),
        Piece.Skip(1), // side tone
        Piece.Optional(Piece.Skip(2)), // the hearing profile's preset, on newer firmware
    ),
    battery = Scale(5),
    // No cancelling on these: only normal and transparency, each of one kind.
    sound = ClassicSound(listOf(ListeningMode.Normal, ListeningMode.Transparency)),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.HearId, bothNeeded = true),
    buttons = listOf(LIBERTY_2_PRO_PLUS_BUTTONS),
    resetButtons = true,
    askFirmware = true,
    // OpenSCQ30 asks for LDAC when connecting, though it offers no LDAC switch for this model.
    askLdac = true,
)
