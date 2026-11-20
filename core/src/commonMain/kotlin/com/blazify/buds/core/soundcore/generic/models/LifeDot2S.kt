package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Life Dot 2S earbuds (A3926). */
val A3926 = SoundcoreModel(
    code = "A3926",
    name = "Life Dot 2S",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Eq(2, 8),
        Piece.Gender,
        Piece.Age,
        Piece.BasicHearId(8),
        Piece.Buttons(Actions.COMMON_LAYOUT),
    ),
    battery = Scale(5),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.BasicHearId, bothNeeded = true),
    buttons = listOf(Actions.COMMON_LAYOUT),
    resetButtons = true,
    askFirmware = true,
)
