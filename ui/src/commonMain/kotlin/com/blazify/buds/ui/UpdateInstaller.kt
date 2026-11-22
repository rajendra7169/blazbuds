package com.blazify.buds.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Puts a new version in by itself, where the platform allows it: on a computer installed from the .deb, Blazbuds
 * fetches the new package, closes, and the system's package manager installs it — asking for the password in its own
 * window — before Blazbuds opens again. Elsewhere there is none and the notice offers the download instead.
 */
interface UpdateInstaller {
    /** Whether this copy can take [release] in by itself. */
    fun canInstall(release: Release): Boolean

    /**
     * Fetches [release]'s file, telling [progress] how far it has got (0 to 1, or null while the size is unknown),
     * then closes the app and installs it. Returns only when something stopped it, with the reason.
     */
    suspend fun install(release: Release, progress: (Float?) -> Unit): String
}

val LocalUpdateInstaller = staticCompositionLocalOf<UpdateInstaller?> { null }
