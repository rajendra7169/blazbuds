package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.ModelExtras
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.Step
import com.blazify.buds.core.soundcore.generic.roundHalfAway

/** What a Liberty 2 Pro touch can do: the usual actions but for switching the listening mode and play/pause. */
private val LIBERTY_2_PRO_ACTIONS = listOf(
    0 to TouchAction.VolumeUp,
    1 to TouchAction.VolumeDown,
    2 to TouchAction.PreviousSong,
    3 to TouchAction.NextSong,
    5 to TouchAction.VoiceAssistant,
)

/** Only a double and a long press on each side, with no "on" byte, changed one at a time. */
private val LIBERTY_2_PRO_BUTTONS = ButtonLayout(
    order = listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Left, PressKind.Long),
        Slot(Ear.Right, PressKind.Double), Slot(Ear.Right, PressKind.Long),
    ),
    presses = listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.Single, DisableMode.Never, LIBERTY_2_PRO_ACTIONS),
        PressSetup(PressKind.Long, 1, EnabledKind.None, ActionKind.Single, DisableMode.Never, LIBERTY_2_PRO_ACTIONS),
    ),
    setAll = false,
)

/**
 * The Liberty 2 Pro's equalizer works in whole decibels from -12 to +12, a byte of 12 meaning flat. Its presets are
 * Soundcore's usual ones; band values are kept in tenths here like on every other model.
 */
private val LIBERTY_2_PRO_EQ = EqSetup(
    presets = EqSetup.PRESETS,
    write = EqWrite.Plain,
    own = { p, presetId, tenths -> Liberty2ProEqualizer.steps(p, presetId, tenths) },
)

/**
 * The Liberty 2 Pro's equalizer has its own ways. Its state holds only the preset's id, not the bands, so the bands
 * shown are the preset's (as OpenSCQ30 does), or for bands set by hand, the last ones this app sent. Changes go as
 * the plain 02 81 message, with both channels in whole decibels.
 */
internal object Liberty2ProEqualizer : ModelExtras {
    /** Where the layout keeps the preset id, in [Parsed.extra]. */
    const val PRESET = "eqPreset"

    private const val CHANNELS = 2
    private const val BANDS = 8

    /** The bands last set by hand, which the earbuds keep but never send back. */
    private var lastCustom: IntArray? = null

    /** Fills in the bands from the preset when the state brought only its id. */
    fun bands(p: Parsed): EqState? {
        p.eq?.let { return it }
        val raw = p.extra[PRESET] ?: return null
        val id = raw[0] or (raw[1] shl 8)
        val gains = if (id == EqSetup.CUSTOM_ID) lastCustom ?: IntArray(BANDS)
        else EqSetup.PRESETS.firstOrNull { it.id == id }?.tenths?.copyOf(BANDS) ?: IntArray(BANDS)
        return EqState(id, List(CHANNELS) { gains.copyOf() }).also { p.eq = it }
    }

    override fun read(p: Parsed, state: BudsState): BudsState {
        val eq = bands(p) ?: return state
        return state.copy(equalizer = LIBERTY_2_PRO_EQ.read(eq))
    }

    override fun steps(p: Parsed, change: Change, wanted: BudsState): List<Step>? {
        // Leaves the change to the shared code, which needs the bands in place.
        if (change is Change.Preset || change is Change.Bands) bands(p)
        return null
    }

    fun steps(p: Parsed, presetId: Int, tenths: IntArray): List<Step> {
        val id = if (presetId == Equalizer.CUSTOM) EqSetup.CUSTOM_ID else presetId
        val decibels = IntArray(BANDS) { roundHalfAway(tenths.getOrElse(it) { 0 } / 10.0).toInt().coerceIn(-12, 12) }
        val body = byteArrayOf(id.toByte(), (id shr 8).toByte()) + ByteArray(CHANNELS * BANDS) { (decibels[it % BANDS] + 12).toByte() }
        val kept = IntArray(BANDS) { decibels[it] * 10 }
        return listOf(
            Step(Packet(EqSetup.SET_PLAIN, body)) {
                it.eq = EqState(id, List(CHANNELS) { kept.copyOf() })
                it.extra[PRESET] = intArrayOf(id and 0xFF, id shr 8)
                if (id == EqSetup.CUSTOM_ID) lastCustom = kept.copyOf()
            },
        )
    }
}

/** Liberty 2 Pro earbuds (A3909). */
val A3909 = SoundcoreModel(
    code = "A3909",
    name = "Liberty 2 Pro",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.Own(Liberty2ProEqualizer.PRESET, 2), // the equalizer's preset id; the bands aren't sent
        Piece.Skip(2), // gender and age range for the hearing profile
        Piece.Skip(21), // the hearing profile, which this model's equalizer leaves alone
        Piece.Buttons(LIBERTY_2_PRO_BUTTONS),
    ),
    battery = Scale(5),
    equalizer = LIBERTY_2_PRO_EQ,
    buttons = listOf(LIBERTY_2_PRO_BUTTONS),
    askFirmware = true,
    extras = Liberty2ProEqualizer,
)
