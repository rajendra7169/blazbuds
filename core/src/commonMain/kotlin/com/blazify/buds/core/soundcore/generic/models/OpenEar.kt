package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.SoundcorePreset
import com.blazify.buds.core.soundcore.generic.Step

/**
 * For earbuds that keep one set of bands but take the equalizer as two: the same bands go out for the left and the
 * right, under [command].
 */
internal fun sameBandsOnBothSides(command: Int): (Parsed, Int, IntArray) -> List<Step> = { _, presetId, tenths ->
    val id = if (presetId == Equalizer.CUSTOM) EqSetup.CUSTOM_ID else presetId
    val sent = EqState(id, listOf(tenths, tenths.copyOf()))
    listOf(Step(Packet(command, EqSetup.eqBytes(sent))) { it.eq = EqState(id, listOf(tenths.copyOf())) })
}

/** Each touch here can be turned off on its own side, as action 0xF, and is set one at a time. */
private fun openEarPress(kind: PressKind, id: Int, actions: List<Pair<Int, TouchAction>>) =
    PressSetup(kind, id, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, actions)

private val C30I_BUTTONS = ButtonLayout(
    listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
        Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
    ),
    listOf(
        openEarPress(PressKind.Double, 0, Actions.WITHOUT_SOUND_MODES),
        openEarPress(PressKind.Triple, 5, Actions.WITHOUT_SOUND_MODES),
        openEarPress(PressKind.Long, 1, Actions.WITHOUT_SOUND_MODES),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/**
 * What double and long press do during a call: answer (1) or decline (0). The app has no place for these, so the
 * actions are left out; the layout is here so the state reads right.
 */
private val C30I_CALL_BUTTONS = ButtonLayout(
    listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
    ),
    listOf(
        openEarPress(PressKind.Double, 0, emptyList()),
        openEarPress(PressKind.Long, 1, emptyList()),
    ),
    setAll = false,
    ignoreEnabled = true,
    actionCommand = Packet.command(0x04, 0x87),
)

/** C30i (A3330). */
val A3330 = SoundcoreModel(
    code = "A3330",
    name = "C30i",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3330",
    layout = listOf(
        Piece.Tws,
        Piece.DualLevel,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // not known, perhaps the case's firmware
        Piece.CaseLevel,
        Piece.Skip(1), // not known
        Piece.Buttons(C30I_BUTTONS),
        Piece.Skip(1), // not known
        Piece.Skip(1), // surround sound, which OpenSCQ30 reads but doesn't offer on this model
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.TwoDevices,
        Piece.Switch(Toggle.TouchLock),
        Piece.Skip(1), // a bass mode, not worked out yet
        Piece.Eq(1, 10),
        Piece.Skip(1), // not known
        Piece.Buttons(C30I_CALL_BUTTONS),
    ),
    case = Scale(5),
    equalizer = EqSetup(
        listOf(SoundcorePreset(0, "Signature", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120))),
        EqWrite.Drc,
    ),
    buttons = listOf(C30I_BUTTONS, C30I_CALL_BUTTONS),
    resetButtons = true,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.TouchLock to SoundcoreModel.DISABLE_BUTTONS,
    ),
    twoDevices = true,
)

private val AEROCLIP_BUTTONS = ButtonLayout(
    listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
    ),
    listOf(
        openEarPress(PressKind.Double, 0, Actions.WITHOUT_SOUND_MODES),
        openEarPress(PressKind.Triple, 5, Actions.WITHOUT_SOUND_MODES),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/** AeroClip (A3388). Its levels count 0 to 9 for 10% to 100%. */
val A3388 = SoundcoreModel(
    code = "A3388",
    name = "AeroClip",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3388",
    layout = listOf(
        Piece.Tws,
        Piece.DualLevel,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // not known, changes between reads
        Piece.CaseLevel,
        Piece.Skip(1), // not known
        Piece.Buttons(AEROCLIP_BUTTONS),
        Piece.Skip(2), // long press, not used
        Piece.Skip(1), // not known
        Piece.Skip(1), // surround sound, which OpenSCQ30 reads but doesn't offer on this model
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.TwoDevices,
        Piece.Switch(Toggle.TouchLock),
        Piece.Skip(1), // a bass mode, not worked out yet
        Piece.Eq(1, 10),
    ),
    battery = Scale(10, 1),
    case = Scale(10, 1),
    // Soundcore's app sends the AeroClip's equalizer with the command others use for the limiter's curve, but with
    // the right earbud's bands in its place; the earbuds don't answer the usual one.
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.Drc, own = sameBandsOnBothSides(EqSetup.SET_DRC)),
    buttons = listOf(AEROCLIP_BUTTONS),
    resetButtons = true,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.TouchLock to SoundcoreModel.DISABLE_BUTTONS,
    ),
    twoDevices = true,
)
