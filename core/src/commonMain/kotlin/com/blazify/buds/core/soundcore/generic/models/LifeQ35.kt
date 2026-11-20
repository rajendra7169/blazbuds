package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Life Q35 headphones (A3027); the Life Tune Pro (A3030) works the same way. */
val A3027 = SoundcoreModel(
    code = "A3027",
    name = "Life Q35",
    aliases = listOf("Life Tune Pro"),
    layout = listOf(
        Piece.SingleBattery,
        Piece.Eq(1, 8),
        Piece.Skip(2), // gender and age range for the hearing profile
        Piece.Skip(21), // the hearing profile, which this model's equalizer leaves alone
        Piece.Sound(4),
        Piece.Firmware,
        Piece.Serial,
        Piece.Switch(Toggle.WearDetection),
        Piece.Optional(Piece.Skip(1)), // touch controls on or off, on newer firmware; not offered
    ),
    battery = Scale(5),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor),
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain),
    switches = mapOf(Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION),
)
