package com.blazify.buds.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.Settings

/** The app's own choices, kept on the device: the look, and how much things move. */
class Prefs(private val store: Settings = Settings()) {
    /** Battery history and the low-battery alert. */
    val battery = BatteryLog(store)

    var theme by mutableStateOf(runCatching { ThemeChoice.valueOf(store.getString(THEME, ThemeChoice.System.name)) }.getOrDefault(ThemeChoice.System))
        private set
    var reduceMotion by mutableStateOf(store.getBoolean(REDUCE_MOTION, false))
        private set

    /** The person's own sounds, saved by name. */
    var sounds by mutableStateOf(readSounds())
        private set

    private fun readSounds(): List<SavedSound> =
        store.getString(SOUNDS, "").split("\n").mapNotNull { SavedSound.parse(it) }

    private fun writeSounds() = store.putString(SOUNDS, sounds.joinToString("\n") { it.line() })

    fun saveSound(name: String, gains: List<Double>) {
        val sound = SavedSound(name.trim(), gains)
        val at = sounds.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
        sounds = if (at >= 0) sounds.toMutableList().also { it[at] = sound } else sounds + sound
        writeSounds()
    }

    fun deleteSound(name: String) {
        sounds = sounds.filterNot { it.name == name }
        writeSounds()
    }

    /** The case battery as last heard for these earbuds, kept for when both earbuds are out and can't ask the case. */
    fun rememberCase(earbuds: String, percent: Int, at: Long) = store.putString("case_" + earbuds, "$percent,$at")

    fun lastCase(earbuds: String): com.blazify.buds.core.model.Battery? =
        store.getString("case_" + earbuds, "").split(",").takeIf { it.size == 2 }?.let { (p, t) ->
            val percent = p.toIntOrNull() ?: return null
            val at = t.toLongOrNull() ?: return null
            com.blazify.buds.core.model.Battery(percent, seenAt = at)
        }

    fun theme(choice: ThemeChoice) {
        theme = choice
        store.putString(THEME, choice.name)
    }

    fun reduceMotion(on: Boolean) {
        reduceMotion = on
        store.putBoolean(REDUCE_MOTION, on)
    }

    /** Whether the app may ask GitHub, now and then, if a newer Blazbuds is out. */
    var checkUpdates by mutableStateOf(store.getBoolean(CHECK_UPDATES, true))
        private set

    /** The newer version last found, so it shows straight away; and the one the person chose to hear no more about. */
    var knownUpdate by mutableStateOf<Release?>(null)
    var dismissedUpdate by mutableStateOf(store.getString(DISMISSED_UPDATE, ""))
        private set

    fun checkUpdates(on: Boolean) {
        checkUpdates = on
        store.putBoolean(CHECK_UPDATES, on)
        if (!on) knownUpdate = null
    }

    fun dismissUpdate(version: String) {
        dismissedUpdate = version
        store.putString(DISMISSED_UPDATE, version)
    }

    /** When GitHub was last asked, in milliseconds since 1970, so it is asked at most every few hours. */
    var lastUpdateCheck: Long
        get() = store.getLong(LAST_UPDATE_CHECK, 0L)
        set(value) = store.putLong(LAST_UPDATE_CHECK, value)

    private companion object {
        const val CHECK_UPDATES = "check_updates"
        const val DISMISSED_UPDATE = "dismissed_update"
        const val LAST_UPDATE_CHECK = "last_update_check"
        const val THEME = "theme"
        const val REDUCE_MOTION = "reduce_motion"
        const val SOUNDS = "my_sounds"
    }
}

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("No prefs") }

/** Which system the app runs on, for the right download and the right words. */
val LocalLook = staticCompositionLocalOf { SystemLook.Android }

/**
 * A sound the person made: its name and the gain of each band. It travels as a short code that anyone with Blazbuds
 * can paste in: BLAZBUDS-EQ:Name:27,18,0,7,21,32,6,17 (tenths of a decibel).
 */
data class SavedSound(val name: String, val gains: List<Double>) {
    fun line() = "${name.replace("\t", " ").replace("\n", " ")}\t${gains.joinToString(",") { (it * 10).toInt().toString() }}"

    fun code() = "BLAZBUDS-EQ:${name.replace(":", " ")}:${gains.joinToString(",") { kotlin.math.round(it * 10).toInt().toString() }}"

    companion object {
        fun parse(line: String): SavedSound? {
            val parts = line.split("\t")
            if (parts.size != 2) return null
            val gains = parts[1].split(",").mapNotNull { it.trim().toIntOrNull()?.div(10.0) }
            return if (parts[0].isBlank() || gains.isEmpty()) null else SavedSound(parts[0], gains)
        }

        /** Finds a shared code anywhere in a message. */
        fun fromCode(text: String): SavedSound? {
            val match = Regex("BLAZBUDS-EQ:([^:\n]{1,40}):(-?\\d+(?:,-?\\d+){1,15})").find(text) ?: return null
            val gains = match.groupValues[2].split(",").map { (it.toInt().coerceIn(-120, 120)) / 10.0 }
            return SavedSound(match.groupValues[1].trim(), gains)
        }
    }
}

/** Shares text the platform's way: the share sheet on a phone, the clipboard on a computer. */
val LocalShare = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Whether this device can play the find sound through the earbuds, which works for any brand. */
val LocalCanFind = staticCompositionLocalOf { false }

/** Sound out through the earbuds, for EarPrint's beeps and demo. */
interface SoundOut {
    fun play(samples: ShortArray, loop: Boolean)
    fun stop()
    fun steadyVolume(on: Boolean)
}

/** The room's loudness, for checking it's quiet enough for the hearing check. */
interface RoomEar {
    fun listen(onLevel: (Double) -> Unit): (() -> Unit)?
    fun ask(done: (Boolean) -> Unit)
}

val LocalRoomEar = staticCompositionLocalOf<RoomEar> {
    object : RoomEar {
        override fun listen(onLevel: (Double) -> Unit): (() -> Unit)? = null
        override fun ask(done: (Boolean) -> Unit) = done(false)
    }
}

val LocalSoundOut = staticCompositionLocalOf<SoundOut> {
    object : SoundOut {
        override fun play(samples: ShortArray, loop: Boolean) {}
        override fun stop() {}
        override fun steadyVolume(on: Boolean) {}
    }
}

/**
 * The case battery to show: the live level while an earbud sits in the case, otherwise the last one heard. The
 * earbuds only learn the case's level through its charging contacts, so with both out they can't tell.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
@androidx.compose.runtime.Composable
fun caseBattery(state: com.blazify.buds.core.model.BudsState): com.blazify.buds.core.model.Battery? {
    val prefs = LocalPrefs.current
    val live = state.case
    androidx.compose.runtime.LaunchedEffect(state.name, live?.percent) {
        if (live != null) prefs.rememberCase(state.name, live.percent, kotlin.time.Clock.System.now().epochSeconds)
    }
    return live ?: androidx.compose.runtime.remember(state.name, live) { prefs.lastCase(state.name) }
}

/** "just now", "5 min ago", "2 h ago", "3 days ago". */
@OptIn(kotlin.time.ExperimentalTime::class)
fun ago(seconds: Long): String {
    val passed = (kotlin.time.Clock.System.now().epochSeconds - seconds).coerceAtLeast(0)
    return when {
        passed < 60 -> "just now"
        passed < 3600 -> "${passed / 60} min ago"
        passed < 86_400 -> "${passed / 3600} h ago"
        else -> "${passed / 86_400} days ago"
    }
}
