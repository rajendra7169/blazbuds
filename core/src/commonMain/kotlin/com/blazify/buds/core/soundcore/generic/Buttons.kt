package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.soundcore.Packet

/** The kinds of touch Soundcore models have; the slides have no place in the app and are kept as they are. */
enum class PressKind(val press: Press?) {
    Single(Press.Single),
    Double(Press.Double),
    Triple(Press.Triple),
    Long(Press.Long),
    SlideUp(null),
    SlideDown(null),
}

/** Whether a touch has its own "on" byte, and how it is packed. */
enum class EnabledKind {
    None,
    Single,

    /** The low half for when both earbuds are out, the high half for one alone. */
    TwsLowBits,
}

/** How the action byte is packed. */
enum class ActionKind {
    Single,

    /** The low half for when both earbuds are out, the high half for one alone. */
    TwsLowBits,
}

/** Whether a touch can be set to do nothing. */
enum class DisableMode {
    Never,

    /** Turning one side off turns the other off too, as Soundcore's app does. */
    BothSides,
    EachSide,
}

/** One kind of touch on a model: the id the set command uses, how its bytes are packed, and what it can do. */
data class PressSetup(
    val kind: PressKind,
    val id: Int,
    val enabled: EnabledKind,
    val action: ActionKind,
    val disable: DisableMode,
    /** The action ids this touch accepts, with what each does in the app. */
    val actions: List<Pair<Int, TouchAction>>,
)

data class Slot(val ear: Ear, val kind: PressKind)

/**
 * How a model keeps its touch settings: the order they come in, what each kind of touch can do, and which messages
 * change them.
 */
data class ButtonLayout(
    val order: List<Slot>,
    val presses: List<PressSetup>,
    /** The model takes all touches in one message when more than one changes. */
    val setAll: Boolean = true,
    /** The "on" byte is there but not used: a touch that does nothing has action 0xF instead. */
    val ignoreEnabled: Boolean = false,
    val actionCommand: Int = SET_ACTION,
) {
    fun setup(kind: PressKind): PressSetup = presses.first { it.kind == kind }

    companion object {
        val SET_ACTION = Packet.command(0x04, 0x81)
        val RESET = Packet.command(0x04, 0x82)
        val SET_ENABLED = Packet.command(0x04, 0x83)
        val SET_ALL = Packet.command(0x04, 0x84)
    }
}

/** What each action id means. */
object Actions {
    const val NOTHING = 0xF

    val COMMON: List<Pair<Int, TouchAction>> = listOf(
        0 to TouchAction.VolumeUp,
        1 to TouchAction.VolumeDown,
        2 to TouchAction.PreviousSong,
        3 to TouchAction.NextSong,
        4 to TouchAction.ListeningMode,
        5 to TouchAction.VoiceAssistant,
        6 to TouchAction.PlayPause,
    )

    val WITH_GAMING: List<Pair<Int, TouchAction>> = COMMON + (9 to TouchAction.GamingMode)

    val WITHOUT_SOUND_MODES: List<Pair<Int, TouchAction>> = COMMON.filter { it.second != TouchAction.ListeningMode }

    /** The six touches most Soundcore earbuds have, in their usual order. */
    val COMMON_ORDER = listOf(
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Left, PressKind.Long),
        Slot(Ear.Right, PressKind.Double), Slot(Ear.Right, PressKind.Long),
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
    )

    /** OpenSCQ30's common settings: double and long can't be turned off, a single tap can (both sides together). */
    val COMMON_LAYOUT = ButtonLayout(
        COMMON_ORDER,
        listOf(
            PressSetup(PressKind.Double, 0, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, COMMON),
            PressSetup(PressKind.Long, 1, EnabledKind.Single, ActionKind.TwsLowBits, DisableMode.Never, COMMON),
            PressSetup(PressKind.Single, 2, EnabledKind.Single, ActionKind.Single, DisableMode.BothSides, COMMON),
        ),
    )
}

private fun nibble(byte: Int, both: Boolean) = if (both) byte and 0x0F else byte shr 4

private fun withNibble(byte: Int, both: Boolean, value: Int) =
    if (both) (byte and 0xF0) or (value and 0x0F) else (byte and 0x0F) or ((value and 0x0F) shl 4)

internal fun ButtonStatus.isEnabled(setup: PressSetup, both: Boolean): Boolean {
    val on = enabled
    return when {
        on == null -> currentAction(setup, both) != Actions.NOTHING
        setup.enabled == EnabledKind.TwsLowBits -> nibble(on, both) == 1
        else -> on == 1
    }
}

internal fun ButtonStatus.currentAction(setup: PressSetup, both: Boolean): Int =
    if (setup.action == ActionKind.TwsLowBits) nibble(action, both) else action

/** Sets the action for the current way of wearing them, or turns the touch off with null, as OpenSCQ30 does. */
internal fun ButtonStatus.withAction(setup: PressSetup, both: Boolean, id: Int?): ButtonStatus {
    fun setAction(value: Int) = if (setup.action == ActionKind.TwsLowBits) withNibble(action, both, value) else value
    fun setEnabled(on: Boolean): Int? = enabled?.let {
        if (setup.enabled == EnabledKind.TwsLowBits) withNibble(it, both, if (on) 1 else 0) else if (on) 1 else 0
    }
    return if (id != null) ButtonStatus(setEnabled(true), setAction(id))
    else ButtonStatus(setEnabled(false), if (enabled != null) action else setAction(Actions.NOTHING))
}

/** The touch settings in the app's terms; slides and unknown actions are left out. */
fun ButtonLayout.controls(statuses: List<ButtonStatus>, both: Boolean): TouchControls {
    val actions = mutableMapOf<Pair<Ear, Press>, TouchAction>()
    order.forEachIndexed { i, slot ->
        val press = slot.kind.press ?: return@forEachIndexed
        val setup = setup(slot.kind)
        val status = statuses.getOrNull(i) ?: return@forEachIndexed
        val enabled = if (ignoreEnabled) status.currentAction(setup, both) != Actions.NOTHING else status.isEnabled(setup, both)
        val id = status.currentAction(setup, both)
        val action = if (!enabled) TouchAction.None else setup.actions.firstOrNull { it.first == id }?.second
        actions[slot.ear to press] = action ?: return@forEachIndexed
    }
    val shown = presses.filter { it.kind.press != null }
    fun choices(setup: PressSetup) = setup.actions.map { it.second } + if (setup.disable != DisableMode.Never || ignoreEnabled) listOf(TouchAction.None) else emptyList()
    val all = shown.flatMap { choices(it) }.distinct()
    val per = shown.associate { it.kind.press!! to choices(it) }.filterValues { it != all }
    return TouchControls(actions, all, per)
}

/**
 * The messages that set one touch, worked out as OpenSCQ30 does: the new settings for every touch (turning a single
 * tap off on one side turns the other off too), then one message for all when more than one changed, or one for each.
 */
fun ButtonLayout.touchSteps(group: Int, statuses: List<ButtonStatus>, both: Boolean, ear: Ear, press: Press, action: TouchAction): List<Step>? {
    val index = order.indexOfFirst { it.ear == ear && it.kind.press == press }
    if (index < 0) return null
    val slot = order[index]
    val setup = setup(slot.kind)
    val chosen = setup.actions.firstOrNull { it.second == action }?.first
    if (chosen == null && action != TouchAction.None) return null
    val target = statuses.map { it.copy() }.toMutableList()
    val id: Int? = if (ignoreEnabled) chosen ?: Actions.NOTHING else chosen
    when (setup.disable) {
        DisableMode.Never -> if (chosen != null) target[index] = target[index].withAction(setup, both, chosen) else return null
        DisableMode.EachSide -> target[index] = target[index].withAction(setup, both, id)
        DisableMode.BothSides -> {
            target[index] = target[index].withAction(setup, both, id)
            val other = order.indexOfFirst { it.kind == slot.kind && it.ear != slot.ear }
            if (other >= 0) {
                val otherStatus = target[other]
                if (!otherStatus.isEnabled(setup, both)) {
                    if (chosen != null) {
                        val current = otherStatus.currentAction(setup, both)
                        target[other] = otherStatus.withAction(setup, both, if (current != Actions.NOTHING) current else chosen)
                    }
                } else if (chosen == null) {
                    target[other] = otherStatus.withAction(setup, both, null)
                }
            }
        }
    }
    val changed = target.indices.filter { target[it].enabled != statuses[it].enabled || target[it].action != statuses[it].action }
    if (changed.isEmpty()) return emptyList()
    val keep: (Parsed) -> Unit = { p -> p.buttons.getOrNull(group)?.let { list -> target.forEachIndexed { i, s -> if (i < list.size) list[i] = s } } }
    if (setAll && changed.size > 1) {
        val body = target.flatMap { status -> listOfNotNull(status.enabled, status.action) }
        return listOf(Step(Packet(ButtonLayout.SET_ALL, bytesOf(body)), keep))
    }
    return changed.flatMap { i ->
        val s = order[i]
        val sideSetup = setup(s.kind)
        val side = if (s.ear == Ear.Left) 0 else 1
        val now = statuses[i]
        val next = target[i]
        val update: (Parsed) -> Unit = { p -> p.buttons.getOrNull(group)?.let { if (i < it.size) it[i] = next.copy() } }
        buildList {
            if (now.action != next.action) add(Step(Packet(actionCommand, bytesOf(listOf(side, sideSetup.id, next.action))), update))
            val enabled = next.enabled
            if (now.enabled != enabled && enabled != null) add(Step(Packet(ButtonLayout.SET_ENABLED, bytesOf(listOf(side, sideSetup.id, enabled))), update))
        }
    }
}

internal fun bytesOf(values: List<Int>): ByteArray = ByteArray(values.size) { values[it].toByte() }
