package com.blazify.buds.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.model.Buds
import com.blazify.buds.ui.blocks.LocalReduceMotion
import com.blazify.buds.ui.screens.EarbudsScreen
import com.blazify.buds.ui.screens.HomeScreen
import com.blazify.buds.ui.screens.Place
import com.blazify.buds.ui.screens.PlaceScreen
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The whole app on any platform. With no earbuds chosen yet it opens on the list of earbuds; after that, on Home,
 * with the few deeper pages sliding in from the side.
 */
@Composable
fun BudsApp(hub: Hub, prefs: Prefs = remember { Prefs() }) {
    BudsTheme(prefs.theme) {
        CompositionLocalProvider(LocalReduceMotion provides prefs.reduceMotion, LocalPrefs provides prefs) {
            val buds by hub.current.collectAsState()
            val earbuds by hub.earbuds.collectAsState()
            val scope = rememberCoroutineScope()
            var place by remember { mutableStateOf<Place?>(null) }
            var loading by remember { mutableStateOf(true) }
            val reload: suspend () -> Unit = {
                loading = true
                hub.reload()
                loading = false
            }
            Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                val now = buds
                if (now == null) {
                    EarbudsScreen(earbuds, null, loading, reload, pick = { hub.choose(it.device) }, openSettings = settingsAction(hub), back = null)
                    return@Box
                }
                val state by now.state.collectAsState()
                AnimatedContent(
                    place,
                    transitionSpec = {
                        val dir = if (targetState != null) 1 else -1
                        (slideInHorizontally(tween(Motion.SLOW)) { dir * it / 5 } + fadeIn(tween(Motion.NORMAL))) togetherWith
                            (slideOutHorizontally(tween(Motion.SLOW)) { -dir * it / 5 } + fadeOut(tween(Motion.QUICK)))
                    },
                    label = "place",
                ) { shown ->
                    when (shown) {
                        null -> HomeScreen(
                            state,
                            change = { scope.launch { now.apply(it) } },
                            open = { place = it },
                            retry = { scope.launch { now.refresh() } },
                        )
                        Place.Earbuds -> EarbudsScreen(
                            earbuds,
                            hub.chosenAddress,
                            loading,
                            reload,
                            pick = {
                                hub.choose(it.device)
                                place = null
                            },
                            openSettings = settingsAction(hub),
                            back = { place = null },
                        )
                        else -> PlaceScreen(shown, state, change = { scope.launch { now.apply(it) } }, back = { place = null })
                    }
                }
            }
        }
    }
}

private fun settingsAction(hub: Hub): (() -> Unit)? = if (hub.canOpenSettings) ({ hub.openBluetoothSettings() }) else null

/** The app around one pair of earbuds that is already there, as for the demo and the screenshots. */
@Composable
fun BudsApp(buds: Buds, prefs: Prefs = remember { Prefs() }) {
    val scope = rememberCoroutineScope()
    val hub = remember(buds) { Hub(OnePair(buds), scope, null).also { it.choose(OnePair.DEVICE) } }
    BudsApp(hub, prefs)
}

private class OnePair(private val buds: Buds) : Platform {
    override suspend fun paired() = listOf(DEVICE)
    override fun open(device: Device, scope: CoroutineScope) = buds

    companion object {
        val DEVICE = Device("Liberty 4 NC", "00:00:00:00:00:00", connected = true)
    }
}
