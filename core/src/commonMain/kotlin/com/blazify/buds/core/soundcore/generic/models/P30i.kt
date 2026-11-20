package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.soundcore.generic.CycleKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.ModelExtras
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.SoundMigration
import com.blazify.buds.core.soundcore.generic.SoundMigration.Requirement
import com.blazify.buds.core.soundcore.generic.SoundcoreModel
import com.blazify.buds.core.soundcore.generic.Step

private val P30I_TOUCHES = NewerParts.fourTouches(ignoreEnabled = true)

/**
 * The P30i's listening mode, in the app's order of changes. Fields: mode, kind of cancelling (manual, adaptive or
 * scene), adaptive strength (1 to 5, read only), manual strength (1 to 5), scene, wind, adaptive sensitivity. The mode
 * is sent twice, where other models keep the kind of transparency.
 */
private val P30I_SOUND = NewerParts.AncSound(
    size = 7,
    at = NewerParts.Places(mode = 0, cancelling = 1, manual = 3, third = 4, wind = 5),
    styles = listOf(CancelStyle.Manual, CancelStyle.Adaptive) + NewerParts.AncSound.SCENES,
    third = NewerParts.Third.Scenes,
    manualLevels = 5,
    decode = { b ->
        intArrayOf(
            NewerParts.mode(b[0]),
            NewerParts.oneOf(b[3], 0, 1, 2),
            (b[1] and 0x0F).coerceIn(1, 5),
            (b[1] shr 4).coerceIn(1, 5),
            NewerParts.oneOf(b[6], 0, 1, 2, 3),
            NewerParts.wind(b[4]),
            b[5],
        )
    },
    encode = { f -> intArrayOf(f[0], (f[3] shl 4) or f[2], f[0], f[1], f[5], f[6], f[4]) },
    migration = SoundMigration(
        listOf(
            null,
            Requirement(0, listOf(0)),
            Requirement(1, listOf(1)),
            Requirement(1, listOf(0)),
            Requirement(1, listOf(2)),
            Requirement(0, listOf(0, 1)),
            null,
        ),
    ),
)

/** Gaming mode came with firmware 01.60; older earbuds send the byte but don't have it. */
private object P30iExtras : ModelExtras {
    private fun hasGaming(p: Parsed): Boolean {
        val versions = listOfNotNull(p.firmwareLeft, p.firmwareRight)
        if (versions.isEmpty()) return false
        return versions.all { version ->
            val parts = version.split('.').mapNotNull { it.toIntOrNull() }
            parts.size == 2 && (parts[0] > 1 || (parts[0] == 1 && parts[1] >= 60))
        }
    }

    override fun read(p: Parsed, state: BudsState): BudsState =
        if (hasGaming(p)) state else state.copy(toggles = state.toggles - Toggle.GamingMode)

    override fun steps(p: Parsed, change: Change, wanted: BudsState): List<Step>? =
        if (change is Change.Switch && change.toggle == Toggle.GamingMode && !hasGaming(p)) emptyList() else null
}

/** P30i and R50i NC (A3959), which share one model number. */
val A3959 = SoundcoreModel(
    code = "A3959",
    name = "P30i",
    aliases = listOf("R50i NC"),
    // The R50i NC's control service; the P30i's may differ in its last digits.
    service = "0cf12d31-fac3-4553-bd80-d6832e7b395b",
    layout = listOf(
        Piece.Tws,
        Piece.DualBattery,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Eq(1, 10),
        Piece.Skip(10), // unknown
        Piece.Skip(1), // unknown
        Piece.Buttons(P30I_TOUCHES),
        Piece.Cycle,
        Piece.Sound(7),
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.TouchTone),
        Piece.TwoDevices,
        Piece.Switch(Toggle.Surround),
        Piece.PowerOff,
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Switch(Toggle.GamingMode),
        // Then twelve unknown bytes.
    ),
    battery = Scale(10),
    sound = P30I_SOUND,
    equalizer = EqSetup(EqSetup.PRESETS_TYPE_2, EqWrite.Drc, bothNeeded = true),
    buttons = listOf(P30I_TOUCHES),
    resetButtons = true,
    cycle = CycleKind.Plain,
    switches = mapOf(
        Toggle.TouchTone to SoundcoreModel.TOUCH_TONE,
        Toggle.Surround to SoundcoreModel.SURROUND,
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.GamingMode to SoundcoreModel.GAMING_MODE,
    ),
    powerOff = SoundcoreModel.TEN_TO_SIXTY,
    twoDevices = true,
    extras = P30iExtras,
)
