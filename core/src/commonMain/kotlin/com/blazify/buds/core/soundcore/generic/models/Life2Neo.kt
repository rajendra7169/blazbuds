package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Life 2 Neo headphones (A3033). */
val A3033 = SoundcoreModel(
    code = "A3033",
    name = "Life 2 Neo",
    layout = listOf(
        Piece.SingleBattery,
        Piece.Eq(1, 8),
        Piece.Firmware,
        Piece.Serial,
        Piece.Switch(Toggle.WearDetection),
    ),
    battery = Scale(5),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain),
    switches = mapOf(Toggle.WearDetection to SoundcoreModel.WEARING_DETECTION),
)
