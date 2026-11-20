package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.soundcore.Packet

/**
 * How one model keeps its listening mode: the bytes in its state, how they read as a [Sound], and the bytes that make
 * a wanted [Sound]. Models differ a lot here, so each family of them has its own scheme.
 */
interface SoundScheme {
    /** How many bytes the scheme takes in the state, and in a sound message. */
    val size: Int

    /** What these earbuds offer, for the screens. */
    fun options(cycle: Boolean): SoundOptions

    fun read(bytes: IntArray): Sound

    /** The bytes for [wanted], starting from [now] so that whatever the app doesn't show stays as it was. */
    fun target(now: IntArray, wanted: Sound): IntArray

    /** The bodies to send one after another to get from [now] to [target]; usually just the target itself. */
    fun steps(now: IntArray, target: IntArray): List<IntArray> = if (now.contentEquals(target)) emptyList() else listOf(target)

    val command: Int get() = SET_SOUND

    companion object {
        val SET_SOUND = Packet.command(0x06, 0x81)
        val SOUND_UPDATE = Packet.command(0x06, 0x01)
    }
}

/** The byte for each mode, the same on every Soundcore model. */
internal fun modeOf(byte: Int): ListeningMode = when (byte) {
    0 -> ListeningMode.NoiseCancelling
    1 -> ListeningMode.Transparency
    else -> ListeningMode.Normal
}

internal fun byteOf(mode: ListeningMode): Int = when (mode) {
    ListeningMode.NoiseCancelling -> 0
    ListeningMode.Transparency -> 1
    ListeningMode.Normal -> 2
}

internal fun transparencyOf(byte: Int) = if (byte == 1) TransparencyStyle.Voice else TransparencyStyle.Full

internal fun byteOf(style: TransparencyStyle) = if (style == TransparencyStyle.Voice) 1 else 0

/**
 * The first and most common scheme, four bytes: the mode, the kind of cancelling (transport, outdoor, indoor or custom),
 * the kind of transparency, and the custom cancelling strength from 0 to 10.
 */
class ClassicSound(
    private val modes: List<ListeningMode>,
    /** The kinds of cancelling, in the order the model lists them; empty for one kind only. */
    private val cancelling: List<CancelStyle> = emptyList(),
    private val voiceTransparency: Boolean = false,
) : SoundScheme {
    override val size = 4

    override fun options(cycle: Boolean) = SoundOptions(
        cancelStyles = cancelling,
        transparencyStyles = voiceTransparency,
        wind = false,
        environment = false,
        cycle = cycle,
        modes = ListeningMode.entries.filter { it in modes },
    )

    override fun read(bytes: IntArray): Sound = Sound(
        mode = modeOf(bytes[0]),
        cancelStyle = styleOf(bytes[1]).takeIf { it in cancelling } ?: cancelling.firstOrNull() ?: CancelStyle.Transport,
        // The custom strength goes from 0 to 10; the app shows five steps.
        manualLevel = ((bytes[3] + 1) / 2).coerceIn(1, 5),
        transparencyStyle = transparencyOf(bytes[2]),
        options = options(false),
    )

    override fun target(now: IntArray, wanted: Sound): IntArray = now.copyOf().also {
        it[0] = byteOf(wanted.mode)
        if (wanted.cancelStyle in cancelling) it[1] = byteOf(wanted.cancelStyle)
        if (voiceTransparency) it[2] = byteOf(wanted.transparencyStyle)
        if (wanted.cancelStyle == CancelStyle.Manual && CancelStyle.Manual in cancelling) it[3] = wanted.manualLevel.coerceIn(1, 5) * 2
    }

    /**
     * Changing the kind of cancelling while not in cancelling mode makes these earbuds cancel without saying so, so,
     * as OpenSCQ30 does, they go to cancelling first, change the kind there, then go to the mode wanted.
     */
    override fun steps(now: IntArray, target: IntArray): List<IntArray> {
        if (now.contentEquals(target)) return emptyList()
        val detour = now[0] != 0 && now[1] != target[1]
        if (!detour) return listOf(target)
        return buildList {
            add(intArrayOf(0, now[1], now[2], now[3]))
            add(intArrayOf(0, target[1], target[2], target[3]))
            if (target[0] != 0) add(target.copyOf())
        }
    }

    companion object {
        fun styleOf(byte: Int): CancelStyle = when (byte) {
            0 -> CancelStyle.Transport
            1 -> CancelStyle.Outdoor
            2 -> CancelStyle.Indoor
            else -> CancelStyle.Manual
        }

        fun byteOf(style: CancelStyle): Int = when (style) {
            CancelStyle.Transport -> 0
            CancelStyle.Outdoor -> 1
            CancelStyle.Indoor -> 2
            else -> 3
        }

        val ALL_MODES = listOf(ListeningMode.Normal, ListeningMode.Transparency, ListeningMode.NoiseCancelling)
    }
}

/**
 * Some models only take a sound change the way Soundcore's app makes it: a setting can only change while the setting it
 * belongs to has certain values (the cancelling strength only in cancelling mode, say). This finds the shortest chain
 * of one-field changes from one state to another, as OpenSCQ30's migration planner does.
 */
class SoundMigration(private val requirements: List<Requirement?>) {
    /** Field [index] may change only while the field at [parent] holds one of [values]. */
    data class Requirement(val parent: Int, val values: List<Int>)

    private val children: List<List<Int>> = requirements.indices.map { i -> requirements.indices.filter { requirements[it]?.parent == i } }

    init {
        requirements.forEachIndexed { i, r -> require(r == null || r.parent < i) { "a field's parent comes before it" } }
    }

    private fun reachable(current: List<Int>, target: List<Int>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        fun visit(parent: Int) {
            for (child in children[parent]) {
                val needed = requirements[child]!!.values
                if (current[parent] in needed) {
                    if (current[child] != target[child]) out += child to target[child]
                    visit(child)
                } else {
                    for (value in needed) if (out.none { it.first == parent && it.second == value }) out += parent to value
                }
            }
        }
        requirements.indices.filter { requirements[it] == null }.forEach { root ->
            if (current[root] != target[root]) out += root to target[root]
            visit(root)
        }
        return out
    }

    /** The states to pass through, the first step first and [to] last; empty when already there. */
    fun path(from: List<Int>, to: List<Int>): List<List<Int>> {
        if (from == to) return emptyList()
        val previous = mutableMapOf<List<Int>, List<Int>>()
        val queue = ArrayDeque(listOf(from))
        val seen = mutableSetOf(from)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for ((index, value) in reachable(current, to)) {
                if (current[index] == value) continue
                val next: List<Int> = current.toMutableList().also { it[index] = value }
                if (!seen.add(next)) continue
                previous[next] = current
                if (next == to) {
                    val path = mutableListOf<List<Int>>(next)
                    var at: List<Int> = next
                    while (true) {
                        val before = previous[at] ?: break
                        if (before == from) break
                        path.add(0, before)
                        at = before
                    }
                    return path
                }
                queue.addLast(next)
            }
        }
        return listOf(to)
    }
}
