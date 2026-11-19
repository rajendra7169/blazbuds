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
import com.blazify.buds.core.model.Buds
import com.blazify.buds.ui.blocks.LocalReduceMotion
import com.blazify.buds.ui.screens.HomeScreen
import com.blazify.buds.ui.screens.Place
import com.blazify.buds.ui.screens.PlaceScreen
import com.blazify.buds.ui.theme.BudsTheme
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.Motion
import kotlinx.coroutines.launch

/** The whole app on any platform: Home, and the few places it opens, sliding in from the side. */
@Composable
fun BudsApp(buds: Buds, prefs: Prefs = remember { Prefs() }, onRetry: () -> Unit = {}) {
    BudsTheme(prefs.theme) {
        CompositionLocalProvider(LocalReduceMotion provides prefs.reduceMotion, LocalPrefs provides prefs) {
            val state by buds.state.collectAsState()
            val scope = rememberCoroutineScope()
            var place by remember { mutableStateOf<Place?>(null) }
            Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                AnimatedContent(
                    place,
                    transitionSpec = {
                        val forward = targetState != null
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(Motion.SLOW)) { dir * it / 5 } + fadeIn(tween(Motion.NORMAL))) togetherWith
                            (slideOutHorizontally(tween(Motion.SLOW)) { -dir * it / 5 } + fadeOut(tween(Motion.QUICK)))
                    },
                    label = "place",
                ) { shown ->
                    if (shown == null) {
                        HomeScreen(
                            state,
                            change = { scope.launch { buds.apply(it) } },
                            open = { place = it },
                            retry = onRetry,
                        )
                    } else {
                        PlaceScreen(shown, state, change = { scope.launch { buds.apply(it) } }, back = { place = null })
                    }
                }
            }
        }
    }
}
