package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
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
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.SoundcorePreset

/** What a double or triple press can do on the C50i. */
private val C50I_MULTI_PRESS = listOf(
    2 to TouchAction.PreviousSong,
    3 to TouchAction.NextSong,
    5 to TouchAction.VoiceAssistant,
    6 to TouchAction.PlayPause,
)

/** What a long press can do on the C50i. */
private val C50I_LONG_PRESS = listOf(
    0 to TouchAction.VolumeUp,
    1 to TouchAction.VolumeDown,
    5 to TouchAction.VoiceAssistant,
)

/** Four kinds of touch on each side, each able to be turned off on its own side and set one at a time. */
private val C50I_BUTTONS = ButtonLayout(
    listOf(
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
        Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
    ),
    listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, C50I_MULTI_PRESS),
        PressSetup(PressKind.Triple, 5, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, C50I_MULTI_PRESS),
        PressSetup(PressKind.Long, 1, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, C50I_LONG_PRESS),
    ),
    setAll = false,
)

/** The C50i's own short list of presets. */
private val C50I_PRESETS = listOf(
    SoundcorePreset(0, "Signature", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(2, "Bass booster", intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(4, "Classical", intArrayOf(30, 30, -20, -20, 0, 20, 30, 40, 0, -120)),
    SoundcorePreset(5, "Podcast", intArrayOf(-30, 20, 40, 40, 30, 20, 0, -20, 0, -120)),
    SoundcorePreset(20, "Treble booster", intArrayOf(-20, -20, -20, -10, 10, 20, 20, 40, 0, -120)),
    SoundcorePreset(30, "Volume booster", intArrayOf(20, 30, 40, 50, 60, 60, 50, 40, 0, -120)),
)

/** C50i (D1101). Its levels count 0 to 9 for 10% to 100%. */
val D1101 = SoundcoreModel(
    code = "D1101",
    name = "C50i",
    service = "0cf12d31-fac3-4553-bd80-d6832e7d1101",
    layout = listOf(
        Piece.Tws,
        Piece.DualLevel,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(1), // not known
        Piece.Buttons(C50I_BUTTONS),
        Piece.Skip(1), // not known
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.TwoDevices,
        Piece.Switch(Toggle.TouchLock),
        Piece.PowerOff,
        Piece.Switch(Toggle.Ldac),
    ),
    battery = Scale(10, 1),
    // The earbuds keep one set of bands but take the plain equalizer message with the same bands for each side.
    equalizer = EqSetup(C50I_PRESETS, EqWrite.Plain, own = sameBandsOnBothSides(EqSetup.SET_PLAIN)),
    buttons = listOf(C50I_BUTTONS),
    switches = mapOf(
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.TouchLock to SoundcoreModel.DISABLE_BUTTONS,
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
    twoDevices = true,
)
