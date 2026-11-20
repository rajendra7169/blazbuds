package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

private val P31I_TOUCHES = NewerParts.fourTouches(single = NewerParts.MINIMAL_ACTIONS)

/**
 * The P31i's listening mode. Fields: mode, manual strength (1 to 5), adaptive strength, transparency, kind of
 * cancelling (manual, adaptive or scene), wind, scene (transport, outdoor, indoor), and real-time adaptive cancelling,
 * which the app shows as environment detection. Its sixth byte is always sent as 0.
 */
private val P31I_SOUND = NewerParts.AncSound(
    size = 8,
    at = NewerParts.Places(mode = 0, transparency = 3, cancelling = 4, manual = 1, third = 6, wind = 5, environment = 7),
    styles = listOf(CancelStyle.Manual, CancelStyle.Adaptive) + NewerParts.AncSound.SCENES,
    third = NewerParts.Third.Scenes,
    manualLevels = 5,
    decode = { b ->
        intArrayOf(
            NewerParts.mode(b[0]),
            (b[1] shr 4).coerceIn(1, 5),
            NewerParts.oneOf(b[1] and 0x0F, 0, 1, 2),
            NewerParts.oneOf(b[2], 0, 1),
            NewerParts.oneOf(b[3], 0, 1, 2),
            NewerParts.wind(b[4]),
            NewerParts.oneOf(b[6], 0, 1, 2),
            if (b[7] == 1) 1 else 0,
        )
    },
    encode = { f -> intArrayOf(f[0], (f[1] shl 4) or f[2], f[3], f[4], f[5], 0, f[6], f[7]) },
)

/** The P31i and the R60i NC share everything but their model number. */
private fun p31i(code: String, name: String, service: String) = SoundcoreModel(
    code = code,
    name = name,
    service = service,
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(5), // the case's firmware, perhaps
        Piece.CaseLevel,
        Piece.Eq(2, 10),
        Piece.Skip(1), // unknown
        Piece.CustomHearId(2, 10, genreAtEnd = true),
        Piece.Skip(1), // unknown
        Piece.Buttons(P31I_TOUCHES),
        Piece.Skip(1), // unknown
        Piece.Sound(8),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.TouchTone),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Limit,
        // Then spatial audio.
    ),
    battery = Scale(10, 1),
    case = Scale(10, 1),
    sound = P31I_SOUND,
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.HearIdAlways, bothNeeded = true, own = NewerParts.liberty4ProEqualizer()),
    buttons = listOf(P31I_TOUCHES),
    resetButtons = true,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    volumeLimit = true,
    twoDevices = true,
)

/** P31i (D1202). */
val D1202 = p31i("D1202", "P31i", "0cf12d31-fac3-4553-bd80-d6832e7d1202")

/** R60i NC (D1202C), the P31i under another name and model number. */
val D1202C = p31i("D1202C", "R60i NC", "0cf12d31-fac3-4553-bd80-d6832e71202c")
