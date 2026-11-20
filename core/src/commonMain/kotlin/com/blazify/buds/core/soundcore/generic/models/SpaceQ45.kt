package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundMigration
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.Step
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf

/**
 * The Space Q45's six bytes: the mode, the manual and adaptive cancelling strengths (high and low half), the kind of
 * transparency (0 talk mode, which the app calls voices, 1 the manual one), whether cancelling is manual (0) or
 * adaptive (1), wind noise reduction, and the manual transparency strength, which has no place in the app and is sent
 * back as it was.
 */
class SpaceQ45Sound : SpaceFieldSound(
    listOf(
        null, // mode
        SoundMigration.Requirement(0, listOf(0)), // manual or adaptive, while cancelling
        SoundMigration.Requirement(1, listOf(0)), // manual strength, while manual
        SoundMigration.Requirement(1, listOf(1)), // adaptive strength, while adaptive
        SoundMigration.Requirement(0, listOf(1)), // kind of transparency, while in transparency
        SoundMigration.Requirement(4, listOf(1)), // manual transparency strength, while manual
        SoundMigration.Requirement(0, listOf(0, 1)), // wind noise reduction, while cancelling or in transparency
    ),
) {
    override val size = 6

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = listOf(CancelStyle.Adaptive, CancelStyle.Manual),
        transparencyStyles = true,
        wind = true,
        environment = false,
        cycle = cycle,
        modes = ListeningMode.entries,
        manualLevels = 5,
    )

    override fun fields(bytes: IntArray): List<Int> = listOf(
        bytes[0],
        bytes[3],
        (bytes[1] shr 4).coerceIn(1, 5),
        (bytes[1] and 0x0F).coerceIn(0, 5),
        bytes[2],
        bytes[5],
        if (bytes[4] == 1) 1 else 0,
    )

    override fun bytes(fields: List<Int>, now: IntArray): IntArray =
        intArrayOf(fields[0], (fields[2] shl 4) or fields[3], fields[4], fields[1], fields[6], fields[5])

    override fun read(bytes: IntArray): Sound {
        val f = fields(bytes)
        return Sound(
            mode = modeOf(f[0]),
            cancelStyle = if (f[1] == 1) CancelStyle.Adaptive else CancelStyle.Manual,
            manualLevel = f[2],
            transparencyStyle = if (f[4] == 0) TransparencyStyle.Voice else TransparencyStyle.Full,
            windReduction = f[6] == 1,
            options = options(false),
        )
    }

    override fun wantedFields(now: List<Int>, wanted: Sound): List<Int> = now.toMutableList().also {
        it[0] = byteOf(wanted.mode)
        when (wanted.cancelStyle) {
            CancelStyle.Adaptive -> it[1] = 1
            CancelStyle.Manual -> {
                it[1] = 0
                it[2] = wanted.manualLevel.coerceIn(1, 5)
            }
            else -> {}
        }
        it[4] = if (wanted.transparencyStyle == TransparencyStyle.Voice) 0 else 1
        it[6] = if (wanted.windReduction) 1 else 0
    }
}

/**
 * The Space Q45's equalizer message (03 87), as OpenSCQ30 builds it: the preset and genre, the bands twice, a fixed
 * gender and age range, the hearing profile sent back switched off (its custom curve's first side used for both),
 * the limiter's curve twice, and a closing 0.
 */
private fun spaceQ45Equalizer(p: Parsed, presetId: Int, tenths: IntArray): List<Step> {
    val id = spacePresetId(presetId)
    val now = p.eq
    if (now != null && now.presetId == id && now.first?.contentEquals(tenths) == true) return emptyList()
    val hear = p.extra["hearId"] ?: return emptyList()
    val genre = p.extra["genre"]?.let { it[0] or (it[1] shl 8) } ?: 0
    val body = buildList {
        add(id and 0xFF); add(id shr 8)
        add(genre and 0xFF); add(genre shr 8)
        addAll(spaceBandBytes(tenths))
        addAll(spaceBandBytes(tenths))
        add(0); add(1) // gender and age range, as OpenSCQ30 sends them
        add(0) // the hearing profile, switched off
        addAll(spaceCurveBytes(hear, 1, SPACE_ABSENT_ALL_FF))
        addAll(spaceCurveBytes(hear, 11, SPACE_ABSENT_ALL_FF))
        for (i in 21..25) add(hear[i]) // when it was made, and its kind
        repeat(2) { addAll(spaceCurveBytes(hear, 26, SPACE_ABSENT_ALL_FF)) }
        repeat(2) { addAll(spaceDrcBytes(tenths)) }
        add(0)
    }
    return listOf(
        Step(Packet(EqSetup.SET_HEAR_ID, ByteArray(body.size) { body[it].toByte() })) {
            it.eq = EqState(id, listOf(tenths.copyOf()))
            spaceHearIdOff(it)
        },
    )
}

/** Space Q45 (A3040). */
val A3040 = SoundcoreModel(
    code = "A3040",
    name = "Space Q45",
    service = SoundcoreModel.service("A3040"),
    layout = listOf(
        Piece.SingleLevel,
        Piece.Skip(1), // unknown
        Piece.Firmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(10), // the limiter's curve
        Piece.Skip(2), // unknown
        Piece.Skip(1), // what a double press does: Bass up or nothing, which has no place in the app
        Piece.Cycle,
        Piece.Sound(6),
        Piece.Skip(6), // unknown
        Piece.Switch(Toggle.Ldac),
        Piece.TwoDevices,
        Piece.Skip(2), // unknown
        Piece.PowerOff,
        Piece.Limit,
        Piece.Switch(Toggle.VoicePrompt),
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Skip(5), // unknown
        Piece.Own("hearId", 46), // the hearing profile: on, two curves, when it was made, its kind, two custom curves
        Piece.Skip(10), // the hearing profile's limiter curve
        Piece.Own("genre", 2), // the hearing profile's music genre
    ),
    battery = Scale(5),
    sound = SpaceQ45Sound(),
    equalizer = EqSetup(SPACE_PRESETS_WITHOUT_BASS_BOOSTER, EqWrite.HearId, hidden = intArrayOf(0, 0), own = ::spaceQ45Equalizer),
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.Ldac to SoundcoreModel.LDAC_SET,
        Toggle.VoicePrompt to SPACE_VOICE_PROMPT,
        Toggle.LowBatteryPrompt to SPACE_LOW_BATTERY,
    ),
    powerOff = SoundcoreModel.HALF_HOURS,
    volumeLimit = true,
    twoDevices = true,
)
