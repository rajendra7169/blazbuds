package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.ModelExtras
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundMigration
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.Step
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf

/**
 * The Space 2's six bytes: the mode, the cancelling strength in the high half of the next byte, then a low half and
 * four bytes nobody has worked out yet, which are sent back as they were. The strength only takes while cancelling,
 * so changing it from another mode goes by way of cancelling.
 */
class Space2Sound : SpaceFieldSound(
    listOf(
        null, // mode
        SoundMigration.Requirement(0, listOf(0)), // cancelling strength, while cancelling
    ),
) {
    override val size = 6

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = listOf(CancelStyle.Manual),
        transparencyStyles = false,
        wind = false,
        environment = false,
        cycle = cycle,
        modes = ListeningMode.entries,
        manualLevels = 5,
    )

    override fun fields(bytes: IntArray): List<Int> = listOf(bytes[0], (bytes[1] shr 4).coerceIn(1, 5))

    override fun bytes(fields: List<Int>, now: IntArray): IntArray =
        intArrayOf(fields[0], (fields[1] shl 4) or (now[1] and 0x0F), now[2], now[3], now[4], now[5])

    override fun read(bytes: IntArray): Sound {
        val f = fields(bytes)
        return Sound(mode = modeOf(f[0]), cancelStyle = CancelStyle.Manual, manualLevel = f[1], options = options(false))
    }

    override fun wantedFields(now: List<Int>, wanted: Sound): List<Int> =
        listOf(byteOf(wanted.mode), if (wanted.cancelStyle == CancelStyle.Manual) wanted.manualLevel.coerceIn(1, 5) else now[1])
}

/**
 * On the Space 2, LDAC and 3D sound can't both be on: turning one on turns the other off on the headphones, which
 * don't say so, so the app notes it itself, as OpenSCQ30 does.
 */
private object Space2Extras : ModelExtras {
    override fun steps(p: Parsed, change: Change, wanted: BudsState): List<Step>? {
        if (change !is Change.Switch || !change.on) return null
        val other = when (change.toggle) {
            Toggle.Ldac -> Toggle.Surround
            Toggle.Surround -> Toggle.Ldac
            else -> return null
        }
        val command = if (change.toggle == Toggle.Ldac) SoundcoreModel.LDAC_SET else SoundcoreModel.SURROUND
        return listOf(
            Step(Packet(command, byteArrayOf(1))) {
                it.switches[change.toggle] = true
                it.switches[other] = false
            },
        )
    }
}

/** Space 2 (D1402). */
val D1402 = SoundcoreModel(
    code = "D1402",
    name = "Space 2",
    service = SoundcoreModel.service("D1402"),
    layout = listOf(
        Piece.SingleBattery,
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Own("eqExtra", 2), // an unknown byte and the age range, sent back with the equalizer
        Piece.Own("hearId", 26), // the hearing profile: on, its curve, when it was made, its kind, its custom curve
        Piece.Own("genre", 2), // the hearing profile's music genre
        Piece.Skip(2), // likely two more touches the official app doesn't offer
        Piece.Skip(1), // what a double press does: Bass up or nothing, which has no place in the app
        Piece.Skip(1), // unknown
        Piece.Cycle,
        Piece.Skip(1), // what a long press of the main button does
        Piece.Sound(6),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.Surround), // 3D sound
        Piece.Skip(2), // unknown
        Piece.Switch(Toggle.VoicePrompt),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.PowerOff,
        Piece.Limit,
        Piece.Skip(3), // whether audio is playing, and unknown
        Piece.Switch(Toggle.WearDetection), // pause when taken off
        Piece.Skip(1), // the "Hey Anka" wake word
        Piece.Skip(8), // unknown
    ),
    battery = Scale(10, 1),
    sound = Space2Sound(),
    equalizer = EqSetup(
        SPACE_PRESETS,
        EqWrite.HearId,
        hidden = intArrayOf(0, 0),
        own = spaceEqualizer(SPACE_ABSENT_LAST_ZERO, afterBands = { p -> p.extra["eqExtra"]?.toList() ?: listOf(0, 0) }, tail = ::spaceDrcBytes),
    ),
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.Surround to SoundcoreModel.SURROUND,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.VoicePrompt to SPACE_VOICE_PROMPT,
        Toggle.WearDetection to SoundcoreModel.AUTO_PLAY_PAUSE,
    ),
    powerOff = SoundcoreModel.LONG_WAITS,
    volumeLimit = true,
    twoDevices = true,
    extras = Space2Extras,
)
