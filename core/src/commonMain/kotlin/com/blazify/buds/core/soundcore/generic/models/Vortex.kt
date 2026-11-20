package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.ClassicSound
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** Vortex earbuds (A3031). */
val A3031 = SoundcoreModel(
    code = "A3031",
    name = "Vortex",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Eq(2, 8),
        // The six touches are sent, but OpenSCQ30 offers no way to change them on this model, so neither does the app.
        Piece.Skip(12),
        Piece.Sound(4),
        Piece.Skip(1), // side tone
        Piece.Switch(Toggle.TouchTone),
        Piece.PowerOff,
    ),
    battery = Scale(5),
    sound = ClassicSound(
        ClassicSound.ALL_MODES,
        cancelling = listOf(CancelStyle.Transport, CancelStyle.Indoor, CancelStyle.Outdoor),
    ),
    equalizer = EqSetup(EqSetup.PRESETS, EqWrite.Plain, bothNeeded = true),
    switches = mapOf(Toggle.TouchTone to SoundcoreModel.TOUCH_TONE),
    powerOff = SoundcoreModel.HALF_HOURS,
    askFirmware = true,
)
