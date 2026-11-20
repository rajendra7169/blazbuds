package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.SoundcorePreset

/**
 * The Q11i's presets: Soundcore's usual list with the brighter Rock, except that Bass booster has an id of its own,
 * 0x7E7E, and comes last.
 */
private val Q11I_PRESETS: List<SoundcorePreset> = EqSetup.PRESETS_TYPE_2.filter { it.id != 2 } +
    SoundcorePreset(0x7E7E, "Bass booster", intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120))

/** Q11i headphones (A3005). */
val A3005 = SoundcoreModel(
    code = "A3005",
    name = "Q11i",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3005",
    layout = listOf(
        Piece.SingleBattery,
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(6), // not known
        Piece.TwoDevices,
        Piece.Skip(4), // not known
        Piece.PowerOff,
    ),
    // The level goes from 0 for 10% to 9 for full.
    battery = Scale(10, offset = 1),
    equalizer = EqSetup(Q11I_PRESETS, EqWrite.Drc),
    powerOff = SoundcoreModel.HALF_HOURS,
    twoDevices = true,
)
