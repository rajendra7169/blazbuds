package com.blazify.buds.desktop

import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Scene
import com.blazify.buds.ui.blocks.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The small local port the running app listens on: one copy at a time, and the command line's way in. */
object ControlPort {
    /** BLAZBUDS_PORT moves it, for trying a second app beside the real one without the two meeting. */
    val number: Int = System.getenv("BLAZBUDS_PORT")?.toIntOrNull() ?: 47613

    /** The first line the app sends back, so a caller knows it reached Blazbuds and not another program. */
    const val HELLO = "blazbuds"

    /**
     * One caller on the port. A second start of the app sends "show" (or "hello" when starting hidden) and reads
     * only the first line; the command line sends a command and reads the JSON line after it.
     */
    fun serve(peer: java.net.Socket, control: Control?, show: () -> Unit) {
        peer.soTimeout = 2_000
        val asked = peer.getInputStream().bufferedReader().readLine()
        val out = peer.getOutputStream()
        // Says who it is, so a second start knows it found Blazbuds and not some other program.
        out.write("$HELLO\n".toByteArray())
        when (asked) {
            null, "hello" -> {}
            "show" -> show()
            else -> {
                val answer = control?.answer(asked)
                    ?: Json.write(mapOf("ok" to false, "error" to "Blazbuds is still starting. Try again in a moment.", "code" to "starting"))
                out.write("$answer\n".toByteArray())
            }
        }
        out.flush()
    }
}

/** Short words for the modes, used by the command line and its JSON. */
object ModeWords {
    fun id(mode: ListeningMode): String = when (mode) {
        ListeningMode.NoiseCancelling -> "anc"
        ListeningMode.Transparency -> "transparency"
        ListeningMode.Normal -> "normal"
    }

    fun parse(word: String): ListeningMode? = when (word.trim().lowercase().replace("_", "-").replace(" ", "-")) {
        "anc", "nc", "noise", "cancelling", "noise-cancelling", "noisecancelling", "cancel" -> ListeningMode.NoiseCancelling
        "transparency", "transparent", "ambient", "aware" -> ListeningMode.Transparency
        "normal", "off" -> ListeningMode.Normal
        else -> null
    }
}

/**
 * Answers the command line, one line in and one line of JSON out:
 * `get state`, `set mode anc|transparency|normal`, `scene <id or name>`, `preset <id or name>`.
 *
 * Changes run in the app's own [scope], the same way a tap does, and the answer carries the state after them.
 */
class Control(
    private val earbuds: () -> Buds?,
    private val scope: CoroutineScope,
    /** The case battery last heard, for when both earbuds are out and can't ask the case. */
    private val lastCase: (String) -> Battery? = { null },
) {
    fun answer(line: String): String = Json.write(
        runCatching { reply(line.trim()) }.getOrElse { failure("Something went wrong: ${it.message ?: it::class.simpleName}", "error") },
    )

    private fun reply(line: String): Map<String, Any?> {
        val words = line.split(Regex("\\s+"), limit = 3)
        val verb = words.getOrElse(0) { "" }.lowercase()
        val buds = earbuds() ?: return failure("No earbuds chosen yet. Open Blazbuds and pick yours.", "no_earbuds")
        return when {
            verb == "get" || verb == "status" -> state(buds.state.value)
            verb == "set" && words.getOrNull(1)?.lowercase() == "mode" -> {
                val mode = words.getOrNull(2)?.let(ModeWords::parse) ?: return failure("Pick a mode: anc, transparency or normal.", "bad_mode")
                val state = buds.state.value
                notReady(state)?.let { return it }
                val sound = state.sound ?: return failure("These earbuds have no listening modes to change.", "no_modes")
                if (mode !in sound.options.modes) return failure("These earbuds don't have ${mode.label()}.", "bad_mode")
                change(buds, listOf(Change.Mode(mode)))
            }
            verb == "scene" -> {
                val wanted = line.substringAfter(' ', "").trim()
                val scene = Scene.DEFAULTS.firstOrNull { it.id.equals(wanted, true) || it.name.equals(wanted, true) }
                    ?: return failure("No scene called \"$wanted\". Scenes: ${Scene.DEFAULTS.joinToString(", ") { it.id }}.", "bad_scene")
                val state = buds.state.value
                notReady(state)?.let { return it }
                if (state.sound == null) return failure("These earbuds have no listening modes, so scenes can't change them.", "no_modes")
                change(buds, scene.changes())
            }
            verb == "preset" -> {
                val wanted = line.substringAfter(' ', "").trim()
                val state = buds.state.value
                notReady(state)?.let { return it }
                val eq = state.equalizer ?: return failure("These earbuds have no sound presets.", "no_presets")
                val preset = eq.presets.firstOrNull { it.name.equals(wanted, true) || it.id.toString() == wanted }
                    ?: return failure("No preset called \"$wanted\". Presets: ${eq.presets.joinToString(", ") { it.name }}.", "bad_preset")
                change(buds, listOf(Change.Preset(preset.id)))
            }
            else -> failure("Unknown command \"$line\".", "unknown")
        }
    }

    private fun notReady(state: BudsState): Map<String, Any?>? = when (state.link) {
        Link.Connected -> null
        Link.Busy -> failure("Another app is using ${state.name} right now.", "busy")
        else -> failure("${state.name} isn't connected.", "not_connected")
    }

    private fun change(buds: Buds, changes: List<Change>): Map<String, Any?> {
        runBlocking { withTimeout(10_000) { scope.async { changes.forEach { buds.apply(it) } }.await() } }
        return state(buds.state.value)
    }

    private fun failure(text: String, code: String) = mapOf("ok" to false, "error" to text, "code" to code)

    fun state(s: BudsState): Map<String, Any?> = mapOf("ok" to true) + describe(s, lastCase(s.name))

    companion object {
        /** Everything the command line and Home Assistant show about a pair, as plain values. */
        fun describe(s: BudsState, lastCase: Battery? = null): Map<String, Any?> {
            val case = s.case ?: lastCase?.takeIf { s.isReady }
            return linkedMapOf(
                "name" to s.name,
                "model" to s.model,
                "connected" to s.isReady,
                "link" to s.link.name.lowercase(),
                "battery" to linkedMapOf(
                    "left" to battery(s.left),
                    "right" to battery(s.right),
                    "case" to battery(case),
                    "overall" to battery(s.overall),
                ),
                "mode" to s.sound?.mode?.let(ModeWords::id),
                "mode_label" to s.sound?.mode?.label(),
                "modes" to s.sound?.options?.modes?.map(ModeWords::id),
                "scene" to Scene.DEFAULTS.firstOrNull { it.matches(s) }?.id,
                "scenes" to Scene.DEFAULTS.map { mapOf("id" to it.id, "name" to it.name) },
                "preset" to s.equalizer?.preset?.let { mapOf("id" to it.id, "name" to it.name) },
                "presets" to s.equalizer?.presets?.map { mapOf("id" to it.id, "name" to it.name) },
                "firmware" to s.firmware,
            )
        }

        private fun battery(b: Battery?): Map<String, Any?>? = b?.let {
            linkedMapOf("percent" to it.percent, "charging" to it.charging).apply { it.seenAt?.let { at -> put("seen_at", at) } }
        }
    }
}
