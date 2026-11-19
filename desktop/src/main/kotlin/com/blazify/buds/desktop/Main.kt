package com.blazify.buds.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.ui.BudsApp
import java.awt.Dimension

fun main(args: Array<String>) = application {
    val buds = DemoBuds()
    val state = rememberWindowState(size = DpSize(440.dp, 900.dp))
    Window(onCloseRequest = ::exitApplication, state = state, title = "Blazbuds") {
        // Never narrower or shorter than the phone layout, so nothing breaks.
        window.minimumSize = Dimension(360, 560)
        BudsApp(buds)
    }
}
