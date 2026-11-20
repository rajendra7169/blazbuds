package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.Packet
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
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf

/**
 * The Liberty 4's touches: single, double and triple taps. Its long presses, which can't be changed, sit between the
 * double and triple taps in the state. A touch that does nothing has action 0xF.
 */
private val LIBERTY_4_TOUCHES = ButtonLayout(
    order = listOf(
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
    ),
    presses = listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
        PressSetup(PressKind.Double, 0, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
        PressSetup(PressKind.Triple, 5, EnabledKind.TwsLowBits, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
    ),
    setAll = false,
    ignoreEnabled = true,
    gaps = mapOf(4 to 4),
)

/**
 * The Liberty 4's listening mode: Soundcore's app shows only the three modes and wind reduction here, so the
 * cancelling strengths and kind of transparency are kept as they are. Fields: mode, manual strength (1 to 6), adaptive
 * strength, transparency, automation, wind, sensitivity.
 */
private object Liberty4Sound : NewerParts.FieldSound(6) {
    override fun fields(bytes: IntArray) = intArrayOf(
        NewerParts.mode(bytes[0]),
        (bytes[1] shr 4).coerceIn(1, 6),
        bytes[1] and 0x0F,
        bytes[2],
        bytes[3],
        NewerParts.wind(bytes[4]),
        bytes[5],
    )

    // The earbuds never take back the "wind detected" bit.
    override fun bytes(fields: IntArray) =
        intArrayOf(fields[0], (fields[1].coerceIn(1, 6) shl 4) or (fields[2] and 0x0F), fields[3].coerceIn(0, 2), fields[4], fields[5] and 1, fields[6])

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = emptyList(),
        transparencyStyles = false,
        wind = true,
        environment = false,
        cycle = cycle,
        modes = listOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency, ListeningMode.Normal),
    )

    override fun show(f: IntArray) = Sound(mode = modeOf(f[0]), windReduction = f[5] and 1 != 0, options = options(false))

    override fun change(f: IntArray, wanted: Sound) {
        f[0] = byteOf(wanted.mode)
        f[5] = (f[5] and 0x02) or (if (wanted.windReduction) 1 else 0)
    }
}

/** Liberty 4 (A3953). */
val A3953 = SoundcoreModel(
    code = "A3953",
    name = "Liberty 4",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3953",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(2, 10),
        Piece.Own("hearId", 1), // 255 or 254: no hearing profile was ever made
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        // The length of the touch block; always 18 so far, which leaves no extra bytes after the touches.
        Piece.Skip(1),
        Piece.Buttons(LIBERTY_4_TOUCHES),
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Skip(6), // the personal cancelling test's results
        Piece.Switch(Toggle.WearDetection),
        Piece.Skip(2), // whether each earbud is in an ear
        Piece.CaseLevel,
        Piece.Skip(1), // bass up
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Skip(1), // the hearing profile's volume
        Piece.Switch(Toggle.WearingTone),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.VoicePrompt), // says the listening mode's name when it changes
    ),
    battery = Scale(5),
    case = Scale(5),
    sound = Liberty4Sound,
    equalizer = EqSetup(
        EqSetup.PRESETS_TYPE_2,
        EqWrite.HearId,
        bothNeeded = true,
        own = NewerParts.p40iEqualizer(made = { p -> p.extra["hearId"]?.first()?.let { it != 255 && it != 254 } ?: false }, blankWhenNotMade = false),
    ),
    buttons = listOf(LIBERTY_4_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
        Toggle.WearingTone to SoundcoreModel.WEARING_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.VoicePrompt to Packet.command(0x10, 0x83),
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    twoDevices = true,
)
