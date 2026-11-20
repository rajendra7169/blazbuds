package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Life Q30 headphones (A3028); the Life Tune (A3029) works the same way. */
val A3028 = SoundcoreModel(
    code = "A3028",
    name = "Life Q30",
    aliases = listOf("Q30", "Life Tune"),
    layout = listOf(
        Piece.SingleBattery,
        Piece.Eq(1, 8),
        Piece.Skip(2), // gender and age range for the hearing profile
        Piece.Skip(21), // the hearing profile, which this model's equalizer leaves alone
        Piece.Sound(4),
        Piece.Firmware,
        Piece.Serial,
        // Newer firmware adds a few settings at the end; only the auto power-off among them can be changed.
        Piece.Optional(
            Piece.Skip(1), // not known
            Piece.Skip(1), // touch controls on or off
            Piece.Skip(1), // two devices at once
            Piece.PowerOff,
            Piece.Skip(1), // a tone when the listening mode changes
            Piece.Skip(1), // a low battery tone
        ),
    ),
    battery = Scale(5),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor),
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain),
    powerOff = SoundcoreModel.HALF_HOURS,
)
