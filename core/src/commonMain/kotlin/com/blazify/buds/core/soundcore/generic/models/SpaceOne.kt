package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/** The switch for the spoken prompt when the listening mode changes, on the Space headphones. */
internal val SPACE_VOICE_PROMPT = Packet.command(0x01, 0xAE)

/** The low battery prompt's switch on the Space One and Space Q45. */
internal val SPACE_LOW_BATTERY = Packet.command(0x01, 0xAF)

/** Space One (A3035). */
val A3035 = SoundcoreModel(
    code = "A3035",
    name = "Space One",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3035",
    layout = listOf(
        Piece.SingleLevel,
        Piece.Skip(1), // unknown
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(2), // unknown
        Piece.Own("hearId", 26), // the hearing profile: on, its curve, when it was made, its kind, its custom curve
        Piece.Own("genre", 1), // the hearing profile's music genre
        Piece.Skip(3), // unknown
        Piece.Skip(1), // what a double press does: Bass up or nothing, which has no place in the app
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.WearDetection), // pause when taken off
        Piece.Skip(4), // unknown
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.Skip(2), // unknown
        Piece.PowerOff,
        Piece.Limit,
        Piece.Switch(Toggle.VoicePrompt),
        Piece.Switch(Toggle.LowBatteryPrompt),
    ),
    battery = Scale(5),
    sound = SpaceOneSound(repeatsMode = true),
    equalizer = EqSetup(
        SPACE_PRESETS,
        EqWrite.HearId,
        hidden = intArrayOf(0, 0),
        own = spaceEqualizer(SPACE_ABSENT_LAST_ZERO, afterBands = { listOf(0xFF, 0xFF) }, tail = ::spaceDrcBytes),
    ),
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.WearDetection to SoundcoreModel.AUTO_PLAY_PAUSE,
        Toggle.VoicePrompt to SPACE_VOICE_PROMPT,
        Toggle.LowBatteryPrompt to SPACE_LOW_BATTERY,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
    volumeLimit = true,
    twoDevices = true,
)

/** Space One Pro (A3062). */
val A3062 = SoundcoreModel(
    code = "A3062",
    name = "Space One Pro",
    service = "0cf12d31-fac3-4553-bd80-d6832e7b3062",
    layout = listOf(
        Piece.SingleBattery,
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(2), // unknown
        Piece.Own("hearId", 26), // the hearing profile: on, its curve, when it was made, its kind, its custom curve
        Piece.Own("genre", 2), // the hearing profile's music genre
        Piece.Skip(2), // unknown
        Piece.Skip(1), // what a double press does: Bass up or nothing, which has no place in the app
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Skip(1), // Dolby Audio
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Limit,
        Piece.Skip(1), // side tone
        Piece.Switch(Toggle.VoicePrompt),
    ),
    battery = Scale(10, 1),
    sound = SpaceOneSound(repeatsMode = false),
    equalizer = EqSetup(
        SPACE_PRESETS,
        EqWrite.HearId,
        hidden = intArrayOf(0, 0),
        // The One Pro sends the bands themselves where the others send the limiter's curve.
        own = spaceEqualizer(SPACE_ABSENT_ALL_FF, afterBands = { listOf(0, 0) }, tail = ::spaceBandBytes),
    ),
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.VoicePrompt to SPACE_VOICE_PROMPT,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
    volumeLimit = true,
    twoDevices = true,
)
