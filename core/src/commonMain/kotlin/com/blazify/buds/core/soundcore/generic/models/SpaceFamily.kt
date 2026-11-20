package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.SoundMigration
import com.blazify.buds.core.soundcore.generic.SoundScheme
import com.blazify.buds.core.soundcore.generic.SoundcorePreset
import com.blazify.buds.core.soundcore.generic.Step
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf

/*
 * What the Space headphones (Space One, Space One Pro, Space Q45, Space 2) share: listening modes kept as a handful of
 * fields that only change the way Soundcore's app changes them, and an equalizer message that carries the hearing
 * profile along with the bands.
 */

/**
 * A listening mode kept as separate fields, which the headphones only take one at a time and only while the field
 * each belongs to has the right value (the cancelling strength only while cancelling, say). Each change goes through
 * [SoundMigration], as OpenSCQ30's migration planner does for these models.
 */
abstract class SpaceFieldSound(requirements: List<SoundMigration.Requirement?>) : SoundScheme {
    private val migration = SoundMigration(requirements)

    /** The fields in the state's bytes, each held to the range the headphones use. */
    abstract fun fields(bytes: IntArray): List<Int>

    /** The bytes for [fields]; bytes that aren't fields are kept from [now]. */
    abstract fun bytes(fields: List<Int>, now: IntArray): IntArray

    /** The fields for [wanted], starting from [now]'s. */
    abstract fun wantedFields(now: List<Int>, wanted: Sound): List<Int>

    override fun target(now: IntArray, wanted: Sound): IntArray = bytes(wantedFields(fields(now), wanted), now)

    override fun steps(now: IntArray, target: IntArray): List<IntArray> {
        val from = fields(now)
        val to = fields(target)
        if (from == to) return emptyList()
        return migration.path(from, to).map { bytes(it, now) }
    }
}

/**
 * The Space One and Space One Pro's six bytes: the mode, the manual and adaptive cancelling strengths (high and low
 * half), a byte the Space One repeats the mode in and the Pro keeps at 1, whether cancelling is manual (0) or adaptive
 * (1), wind noise reduction, and the transparency strength. The adaptive strength is only shown by the headphones and
 * the transparency strength has no place in the app; both are sent back as they were.
 */
class SpaceOneSound(private val repeatsMode: Boolean) : SpaceFieldSound(
    listOf(
        null, // mode
        SoundMigration.Requirement(MODE, listOf(NOISE_CANCELLING)), // manual or adaptive
        SoundMigration.Requirement(KIND, listOf(ADAPTIVE)), // adaptive strength
        SoundMigration.Requirement(KIND, listOf(MANUAL)), // manual strength
        SoundMigration.Requirement(MODE, listOf(TRANSPARENCY)), // transparency strength
        SoundMigration.Requirement(MODE, listOf(NOISE_CANCELLING, TRANSPARENCY)), // wind noise reduction
    ),
) {
    override val size = 6

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = listOf(CancelStyle.Adaptive, CancelStyle.Manual),
        transparencyStyles = false,
        wind = true,
        environment = false,
        cycle = cycle,
        modes = ListeningMode.entries,
        manualLevels = 5,
    )

    override fun fields(bytes: IntArray): List<Int> = listOf(
        bytes[0],
        bytes[3],
        (bytes[1] and 0x0F).coerceIn(1, 5),
        (bytes[1] shr 4).coerceIn(1, 5),
        bytes[5].coerceIn(1, 5),
        if (bytes[4] == 1) 1 else 0,
    )

    override fun bytes(fields: List<Int>, now: IntArray): IntArray = intArrayOf(
        fields[0],
        (fields[3] shl 4) or fields[2],
        if (repeatsMode) fields[0] else 1,
        fields[1],
        fields[5],
        fields[4],
    )

    override fun read(bytes: IntArray): Sound {
        val f = fields(bytes)
        return Sound(
            mode = modeOf(f[0]),
            cancelStyle = if (f[1] == ADAPTIVE) CancelStyle.Adaptive else CancelStyle.Manual,
            manualLevel = f[3],
            windReduction = f[5] == 1,
            options = options(false),
        )
    }

    override fun wantedFields(now: List<Int>, wanted: Sound): List<Int> = now.toMutableList().also {
        it[0] = byteOf(wanted.mode)
        when (wanted.cancelStyle) {
            CancelStyle.Adaptive -> it[1] = ADAPTIVE
            CancelStyle.Manual -> {
                it[1] = MANUAL
                it[3] = wanted.manualLevel.coerceIn(1, 5)
            }
            else -> {}
        }
        it[5] = if (wanted.windReduction) 1 else 0
    }

    private companion object {
        const val MODE = 0
        const val KIND = 1
        const val NOISE_CANCELLING = 0
        const val TRANSPARENCY = 1
        const val MANUAL = 0
        const val ADAPTIVE = 1
    }
}

/** Soundcore's presets as the Space headphones number them, without Bass booster. */
internal val SPACE_PRESETS_WITHOUT_BASS_BOOSTER: List<SoundcorePreset> = EqSetup.PRESETS_TYPE_2.filter { it.id != 2 }

/** The Space One, One Pro and Space 2's presets: the same, with Bass booster under its own id, 0x7E7E, at the end. */
internal val SPACE_PRESETS: List<SoundcorePreset> =
    SPACE_PRESETS_WITHOUT_BASS_BOOSTER + SoundcorePreset(0x7E7E, "Bass booster", intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120))

/** Where each part of the hearing profile sits in the bytes a one-channel Space model keeps under "hearId". */
private const val HEAR_ID_CURVE = 1
private const val HEAR_ID_TIME = 11
private const val HEAR_ID_TYPE = 15
private const val HEAR_ID_CUSTOM = 16

/** The bytes of a hearing profile curve to send back: as they were, or [absent] when the headphones have none. */
internal fun spaceCurveBytes(raw: IntArray, from: Int, absent: List<Int>): List<Int> =
    if (raw[from] == 0xFF) absent else (from until from + absent.size).map { raw[it].coerceAtMost(0xFE) }

/** A curve that isn't there, as the Space One and Space 2 keep it: 0xFF in every band but the last, which is 0. */
internal val SPACE_ABSENT_LAST_ZERO: List<Int> = List(9) { 0xFF } + 0

/** A curve that isn't there, as the Space One Pro and Space Q45 send it: 0xFF in every band. */
internal val SPACE_ABSENT_ALL_FF: List<Int> = List(10) { 0xFF }

internal fun spaceBandBytes(tenths: IntArray): List<Int> = tenths.map { it.coerceIn(-120, 134) + 120 }

internal fun spaceDrcBytes(tenths: IntArray): List<Int> = spaceBandBytes(EqSetup.drc(tenths))

/** The id the headphones keep for [presetId], the app's own id for bands set by hand being 0xFEFE on the headphones. */
internal fun spacePresetId(presetId: Int): Int = if (presetId == Equalizer.CUSTOM) EqSetup.CUSTOM_ID else presetId

internal fun spaceHearIdOff(p: Parsed) {
    p.extra["hearId"]?.let { it[0] = 0 }
}

/**
 * The equalizer message of the one-channel Space models (03 87), as OpenSCQ30 builds it for each: the preset and
 * genre, the bands, two bytes that differ by model ([afterBands]), the hearing profile sent back switched off (curves
 * that aren't there written as [absent]), then [tail] (the limiter's curve, or the bands again) and a closing 0.
 * Nothing is sent when the equalizer is already as wanted.
 */
internal fun spaceEqualizer(
    absent: List<Int>,
    afterBands: (Parsed) -> List<Int>,
    tail: (IntArray) -> List<Int>,
): (Parsed, Int, IntArray) -> List<Step> = write@{ p, presetId, tenths ->
    val id = spacePresetId(presetId)
    val now = p.eq
    if (now != null && now.presetId == id && now.first?.contentEquals(tenths) == true) return@write emptyList()
    val hear = p.extra["hearId"] ?: return@write emptyList()
    val genre = p.extra["genre"]?.let { g -> g[0] or (g.getOrElse(1) { 0 } shl 8) } ?: 0
    val body = buildList {
        add(id and 0xFF); add(id shr 8)
        add(genre and 0xFF); add(genre shr 8)
        addAll(spaceBandBytes(tenths))
        addAll(afterBands(p))
        add(0) // the hearing profile, switched off
        addAll(spaceCurveBytes(hear, HEAR_ID_CURVE, absent))
        for (i in HEAR_ID_TIME..HEAR_ID_TYPE) add(hear[i]) // when it was made, and its kind
        addAll(spaceCurveBytes(hear, HEAR_ID_CUSTOM, absent))
        addAll(tail(tenths))
        add(0)
    }
    listOf(
        Step(Packet(EqSetup.SET_HEAR_ID, ByteArray(body.size) { body[it].toByte() })) {
            it.eq = EqState(id, listOf(tenths.copyOf()))
            spaceHearIdOff(it)
        },
    )
}
