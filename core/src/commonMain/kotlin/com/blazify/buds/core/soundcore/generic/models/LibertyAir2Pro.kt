package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Liberty Air 2 Pro (A3951). */
val A3951 = SoundcoreModel(
    code = "A3951",
    name = "Liberty Air 2 Pro",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Eq(2, 8),
        Piece.Gender,
        Piece.Age,
        Piece.CustomHearId(2, 8),
        Piece.Buttons(Actions.COMMON_LAYOUT),
        Piece.Sound(4),
        Piece.Skip(1), // side tone
        Piece.Switch(Toggle.WearDetection),
        Piece.Switch(Toggle.TouchTone),
        Piece.Optional(Piece.Skip(2)), // the hearing profile's preset, on newer firmware
        Piece.Optional(Piece.Skip(2)), // a finer battery level, on newer firmware
    ),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor, CancelStyle.Manual),
        voiceTransparency = true,
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.HearId, bothNeeded = true),
    buttons = listOf(Actions.COMMON_LAYOUT),
    resetButtons = true,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION,
    ),
    askFirmware = true,
    askLdac = true,
)
