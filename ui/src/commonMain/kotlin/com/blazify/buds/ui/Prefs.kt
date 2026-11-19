package com.blazify.buds.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.blazify.buds.ui.theme.ThemeChoice
import com.russhwolf.settings.Settings

/** The app's own choices, kept on the device: the look, and how much things move. */
class Prefs(private val store: Settings = Settings()) {
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

    fun theme(choice: ThemeChoice) {
        theme = choice
        store.putString(THEME, choice.name)
    }

    fun reduceMotion(on: Boolean) {
        reduceMotion = on
        store.putBoolean(REDUCE_MOTION, on)
    }

    private companion object {
        const val THEME = "theme"
        const val REDUCE_MOTION = "reduce_motion"
        const val SOUNDS = "my_sounds"
    }
}

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("No prefs") }

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

val LocalSoundOut = staticCompositionLocalOf<SoundOut> {
    object : SoundOut {
        override fun play(samples: ShortArray, loop: Boolean) {}
        override fun stop() {}
        override fun steadyVolume(on: Boolean) {}
    }
}
