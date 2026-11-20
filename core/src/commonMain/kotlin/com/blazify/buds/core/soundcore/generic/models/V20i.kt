package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.SoundcorePreset
import com.blazify.buds.core.soundcore.generic.Step

/** The V20i's actions for a single tap: the common ones without changing the listening mode or the voice assistant. */
private val V20I_SINGLE_ACTIONS = Actions.COMMON.filter { it.first != 4 && it.first != 5 }

/** The V20i's actions for double, triple and long presses; its own "lights" action (14) has no place in the app. */
private val V20I_ACTIONS = Actions.COMMON.filter { it.first != 4 }

/** The V20i's eight touches, each side's single, double, triple and long press, none of which has its own "on" byte. */
val V20I_LAYOUT = ButtonLayout(
    order = listOf(
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
        Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
    ),
    presses = listOf(
        PressSetup(PressKind.Single, 2, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, V20I_SINGLE_ACTIONS),
        PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, V20I_ACTIONS),
        PressSetup(PressKind.Triple, 5, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, V20I_ACTIONS),
        PressSetup(PressKind.Long, 1, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, V20I_ACTIONS),
    ),
    setAll = false,
    ignoreEnabled = true,
)

/** The V20i's own presets, as OpenSCQ30 lists them. */
private val V20I_PRESETS = listOf(
    SoundcorePreset(0, "Signature", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(1, "Balanced", intArrayOf(53, -21, -9, -16, 12, -39, -32, 2, 0, -120)),
    SoundcorePreset(2, "Bass booster", intArrayOf(40, 30, 10, 0, 0, 0, 0, 0, 0, -120)),
    SoundcorePreset(4, "Classical", intArrayOf(30, 30, -20, -20, 0, 20, 30, 40, 0, -120)),
    SoundcorePreset(5, "Spoken word", intArrayOf(-30, 20, 40, 40, 30, 20, 0, -20, 0, -120)),
    SoundcorePreset(6, "Dance", intArrayOf(20, -30, -10, 10, 20, 20, 10, -30, 0, -120)),
    SoundcorePreset(7, "Deep", intArrayOf(20, 10, 30, 30, 20, -20, -40, -50, 0, -120)),
    SoundcorePreset(8, "Electronic", intArrayOf(30, 20, -20, 20, 10, 20, 30, 30, 0, -120)),
    SoundcorePreset(9, "Flat", intArrayOf(-20, -20, -10, 0, 0, 0, -20, -20, 0, -120)),
    SoundcorePreset(10, "Hip-hop", intArrayOf(20, 30, -10, -10, 20, -10, 20, 30, 0, -120)),
    SoundcorePreset(11, "Jazz", intArrayOf(20, 20, -20, -20, 0, 20, 30, 40, 0, -120)),
    SoundcorePreset(12, "Latin", intArrayOf(0, 0, -20, -20, -20, 0, 30, 50, 0, -120)),
    SoundcorePreset(13, "Lounge", intArrayOf(-10, 20, 40, 30, 0, -20, 20, 10, 0, -120)),
    SoundcorePreset(14, "Piano", intArrayOf(0, 30, 30, 20, 40, 50, 30, 40, 0, -120)),
    SoundcorePreset(15, "Pop", intArrayOf(-10, 10, 30, 30, 10, -10, -20, -30, 0, -120)),
    SoundcorePreset(16, "R&B", intArrayOf(60, 20, -20, -20, 20, 30, 30, 40, 0, -120)),
    SoundcorePreset(17, "Rock", intArrayOf(30, 20, -10, -10, 10, 30, 40, 50, 0, -120)),
    SoundcorePreset(18, "Small speakers", intArrayOf(40, 30, 10, 0, -20, -30, -40, -40, 0, -120)),
    SoundcorePreset(19, "Podcast", intArrayOf(-30, -20, 10, 20, 20, 10, 0, -30, 0, -120)),
    SoundcorePreset(20, "Treble booster", intArrayOf(0, 0, -20, 0, -10, -50, 50, 10, 0, -120)),
    SoundcorePreset(21, "Treble reducer", intArrayOf(0, 0, 0, -20, -30, -40, -40, -60, 0, -120)),
    SoundcorePreset(30, "Volume booster", intArrayOf(20, 30, 40, 50, 60, 60, 50, 40, 0, -120)),
)

/**
 * The V20i's equalizer message (02 83): though it keeps a curve for each side, it takes one, the preset id, the bands
 * and the limiter's curve, and plays it on both.
 */
private fun v20iEqualizer(p: Parsed, presetId: Int, tenths: IntArray): List<Step> {
    val id = if (presetId == Equalizer.CUSTOM) EqSetup.CUSTOM_ID else presetId
    val body = byteArrayOf(id.toByte(), (id shr 8).toByte()) + EqSetup.bandBytes(tenths, tenths.size) + EqSetup.drcBytes(tenths, tenths.size)
    val channels = p.eq?.channels?.size ?: 2
    return listOf(
        Step(Packet(EqSetup.SET_DRC, body)) {
            it.eq = EqState(id, List(channels) { tenths.copyOf() })
        },
    )
}

/** V20i (A3876). */
val A3876 = SoundcoreModel(
    code = "A3876",
    name = "V20i",
    service = SoundcoreModel.service("A3876"),
    layout = listOf(
        Piece.Tws,
        Piece.DualLevel,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(6), // unknown
        Piece.Eq(2, 10),
        Piece.Skip(1), // unknown
        Piece.Buttons(V20I_LAYOUT),
        Piece.Skip(7), // the lights: on, brightness, minutes until they go off, effect, colour
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.VoicePrompt),
        Piece.PowerOff,
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.GamingMode),
        Piece.Skip(1), // the balance between left and right
        Piece.TwoDevices,
    ),
    battery = Scale(10, 1),
    equalizer = EqSetup(V20I_PRESETS, EqWrite.Drc, bothNeeded = true, hidden = intArrayOf(0, 0), own = ::v20iEqualizer),
    buttons = listOf(V20I_LAYOUT),
    resetButtons = true,
    switches = mapOf(
        Toggle.VoicePrompt to SoundcoreModel.VOICE_PROMPT,
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    twoDevices = true,
)
