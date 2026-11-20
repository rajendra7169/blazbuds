package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.Piece
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Scale
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundScheme
import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/**
 * The Sleep A30's noise cancelling, which is simply on or off: on shows as cancelling, off as normal. It is one byte
 * in the state and has its own switch command.
 */
class SleepA30Sound : SoundScheme {
    override val size = 1

    override val command: Int = Packet.command(0x06, 0x87)

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = emptyList(),
        transparencyStyles = false,
        wind = false,
        environment = false,
        cycle = false,
        modes = listOf(ListeningMode.NoiseCancelling, ListeningMode.Normal),
    )

    override fun read(bytes: IntArray): Sound = Sound(
        mode = if (bytes[0] == 1) ListeningMode.NoiseCancelling else ListeningMode.Normal,
        cancelStyle = CancelStyle.Manual,
        options = options(false),
    )

    override fun target(now: IntArray, wanted: Sound): IntArray =
        intArrayOf(if (wanted.mode == ListeningMode.NoiseCancelling) 1 else 0)
}

/**
 * What the Sleep A30's touches can do. Its own "noise cancelling" action (8) switches cancelling on and off, which is
 * what changing the listening mode means on these; its "change mode" action (13, between the phone and the sounds
 * kept on the earbuds) has no place in the app.
 */
private val SLEEP_A30_ACTIONS = Actions.COMMON.filter { it.first in listOf(0, 1, 2, 3, 6) } + (8 to TouchAction.ListeningMode)

/** The Sleep A30's four touches, a double and a triple tap on each side, with no "on" byte of their own. */
val SLEEP_A30_LAYOUT = ButtonLayout(
    order = listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
    ),
    presses = listOf(
        PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, SLEEP_A30_ACTIONS),
        PressSetup(PressKind.Triple, 5, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, SLEEP_A30_ACTIONS),
    ),
    setAll = false,
)

/** Sleep A30 (D1301). */
val D1301 = SoundcoreModel(
    code = "D1301",
    name = "Sleep A30",
    service = "0cf12d31-fac3-4553-bd80-d6832e7d1301",
    layout = listOf(
        Piece.Tws,
        Piece.DualLevel,
        Piece.DualFirmware,
        Piece.Serial,
        Piece.Skip(6), // unknown
        Piece.Skip(5), // the case's firmware version
        Piece.Skip(4), // unknown
        Piece.Skip(1), // unknown, always 6 so far
        Piece.Buttons(SLEEP_A30_LAYOUT),
        Piece.Skip(15), // unknown
        Piece.Skip(1), // where the sound comes from: the phone or the sounds kept on the earbuds
        Piece.Skip(1), // where it comes from when they start
        Piece.Skip(2), // unknown
        Piece.Switch(Toggle.LowBatteryPrompt),
        Piece.Skip(67), // unknown
        Piece.Skip(1), // a tone when they turn themselves off
        Piece.Skip(1), // a prompt when the sound's source changes
        Piece.Skip(1), // unknown
        Piece.Sound(1), // noise cancelling
        Piece.Skip(1), // calls coming through while playing from the phone
        Piece.Skip(2), // unknown
        Piece.Skip(1), // what plays after falling asleep
        Piece.Skip(1), // unknown
        Piece.Switch(Toggle.TouchLock), // touches turned off
        Piece.Skip(1), // a prompt when cancelling changes
        Piece.Skip(1), // switching to the earbuds' own sounds once asleep
    ),
    battery = Scale(10, 1),
    sound = SleepA30Sound(),
    buttons = listOf(SLEEP_A30_LAYOUT),
    resetButtons = true,
    switches = mapOf(
        Toggle.LowBatteryPrompt to SoundcoreModel.LOW_BATTERY,
        Toggle.TouchLock to SoundcoreModel.DISABLE_BUTTONS,
    ),
)
