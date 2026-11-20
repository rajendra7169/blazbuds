package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.ClassicSound
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

/**
 * The Life Note 3 and the Life P3 run the same firmware: ten equalizer bands of which the earbuds use eight, a hearing
 * profile the app leaves alone, and, on newer firmware, the switches and case level after the listening mode.
 */
private fun lifeNote3(code: String, name: String, service: String) = SoundcoreModel(
    code = code,
    name = name,
    service = service,
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(2, 10),
        Piece.Age,
        Piece.Skip(48), // the hearing profile, or zeros on earbuds without one; only the plain equalizer is sent
        Piece.Buttons(Actions.COMMON_LAYOUT),
        Piece.Cycle,
        Piece.Sound(4),
        Piece.Skip(2), // not known
        Piece.Optional(
            Piece.Switch(Toggle.TouchTone),
            Piece.Switch(Toggle.WearDetection),
            Piece.Switch(Toggle.GamingMode),
            Piece.CaseLevel,
            Piece.Skip(1), // not known
            Piece.Skip(1), // the earbuds' colour
            Piece.Skip(1), // wind noise detection, which OpenSCQ30 reads but doesn't offer
            Piece.Skip(3), // not known
        ),
    ),
    case = Scale(5),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor),
        voiceTransparency = true,
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain, bothNeeded = true),
    buttons = listOf(Actions.COMMON_LAYOUT),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
    ),
)

/** Life Note 3 (A3933). */
val A3933 = lifeNote3("A3933", "Life Note 3", "0cf12d31-fac3-4553-bd80-d6832e7b3933")

/** Life P3 (A3939), on the Life Note 3's firmware but with a control service of its own. */
val A3939 = lifeNote3("A3939", "Life P3", "0cf12d31-fac3-4553-bd80-d6832e7b3939")

/** The Life Note 3S's touches: no listening-mode action, and a single tap turned off on one side is off on both. */
private val LIFE_NOTE_3S_BUTTONS = ButtonLayout(
    Actions.COMMON_ORDER,
    listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, Actions.WITHOUT_SOUND_MODES),
        PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.Single, DisableMode.BothSides, Actions.WITHOUT_SOUND_MODES),
    ),
)

/** Life Note 3S (A3945). */
val A3945 = SoundcoreModel(
    code = "A3945",
    name = "Life Note 3S",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3945",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(2, 10),
        Piece.Buttons(LIFE_NOTE_3S_BUTTONS),
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.WearDetection),
        Piece.Switch(Toggle.GamingMode),
        Piece.CaseLevel,
        Piece.Skip(1), // a bass-up switch, which OpenSCQ30 reads but doesn't offer
        Piece.Skip(1), // the earbuds' colour
    ),
    case = Scale(5),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain, bothNeeded = true),
    buttons = listOf(LIFE_NOTE_3S_BUTTONS),
    resetButtons = true,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
    ),
)
