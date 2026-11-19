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
    }
}

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("No prefs") }
