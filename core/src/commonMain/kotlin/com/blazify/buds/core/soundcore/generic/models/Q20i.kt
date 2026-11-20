package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Q20i headphones (A3004). */
val A3004 = SoundcoreModel(
    code = "A3004",
    name = "Q20i",
    layout = listOf(
        Piece.SingleBattery,
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Sound(4),
    ),
    battery = Scale(5),
    // Cancelling comes in one kind only on these.
    sound = ClassicSound(ClassicSound.ALL_MODES),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Drc),
)
