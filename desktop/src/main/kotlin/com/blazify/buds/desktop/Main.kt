package com.blazify.buds.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.StrokeCap
import com.blazify.buds.core.BasicBuds
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.link.LinuxBluetooth
import com.blazify.buds.core.link.info
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.SoundcoreBuds
import com.blazify.buds.ui.BudsApp
import com.blazify.buds.ui.Hub
import com.blazify.buds.ui.Platform
import com.blazify.buds.ui.SystemLook
import com.blazify.buds.ui.Prefs
import com.blazify.buds.ui.blocks.LogoShape
import com.blazify.buds.ui.blocks.label
import com.blazify.buds.ui.screens.QuickPanel
import com.blazify.buds.ui.theme.BudsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.GraphicsEnvironment

/** Bluetooth on a Linux computer, through BlueZ. */
object LinuxPlatform : Platform {
    override val look: SystemLook = System.getProperty("os.name").let {
        when {
            it.startsWith("Windows") -> SystemLook.Windows
            it.startsWith("Mac") -> SystemLook.Mac
            else -> SystemLook.Linux
        }
    }

    override suspend fun paired(): List<Device> = LinuxBluetooth.devices()

    override suspend fun peek(device: Device): Pair<Boolean, Int?> = LinuxBluetooth.info(device.address)

    override fun open(device: Device, scope: CoroutineScope): Buds = when (Catalog.model(device).support) {
        Support.Full -> SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, { LinuxBluetooth.open(device.address, A3947.SERVICE) })
            .also { it.start() }
        Support.Basic -> BasicBuds(device.name, scope) { LinuxBluetooth.info(device.address) }.also { it.start() }
    }

    override val hasBluetoothSettings: Boolean = true

    /** A computer has no share sheet; the text goes to the clipboard, ready to paste anywhere. */
    override fun share(text: String) {
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
    }

    override fun bluetoothSettings() {
        runCatching { ProcessBuilder("gnome-control-center", "bluetooth").start() }
            .recoverCatching { ProcessBuilder("blueman-manager").start() }
    }
}

/** The app's icon for the window and the tray: the earbud in white on a teal tile, drawn at any size. */
object AppIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val side = size.minDimension
        drawRoundRect(Color(0xFF0F9488), cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.23f))
        val inner = Size(side * 0.72f, side * 0.72f)
        val offset = (side - inner.width) / 2
        translate(offset, offset) {
            val mark = Path().apply {
                fillType = PathFillType.EvenOdd
                addPath(LogoShape.path(LogoShape.OUTLINE, inner))
                addPath(LogoShape.path(LogoShape.OPENING, inner))
            }
            drawPath(mark, Color.White)
            drawPath(LogoShape.path(LogoShape.WAVE, inner), Color.White, style = Stroke(inner.width * LogoShape.WAVE_WIDTH / 1000f, cap = StrokeCap.Round))
        }
    }
}

private fun BudsState.batteryLine(): String {
    val parts = listOfNotNull(
        left?.let { "Left ${it.percent}%" },
        right?.let { "Right ${it.percent}%" },
        case?.let { "Case ${it.percent}%" },
        overall?.let { "Battery ${it.percent}%" },
    )
    return if (!isReady) "Not connected" else parts.joinToString(" · ").ifEmpty { "Connected" }
}

fun main(args: Array<String>) = application {
    val prefs = remember { Prefs() }
    val scope = rememberCoroutineScope()
    val demo = "--demo" in args
    val hub = remember { if (demo) null else Hub(LinuxPlatform, scope) }
    val demoBuds = remember { if (demo) DemoBuds() else null }
    var shown by remember { mutableStateOf("--hidden" !in args) }
    var panel by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(size = DpSize(440.dp, 900.dp))
    val current by (hub?.current ?: remember { kotlinx.coroutines.flow.MutableStateFlow(demoBuds) }).collectAsState()
    val buds = current
    val state = buds?.state?.collectAsState()?.value
    LaunchedEffect(hub) { hub?.reload() }

    // The tray stays while the window is closed, so the earbuds are one click away.
    val trayWorks = remember { java.awt.SystemTray.isSupported() }
    if (trayWorks) {
        Tray(
            icon = AppIcon,
            tooltip = state?.let { "${it.name}: ${it.batteryLine()}" } ?: "Blazbuds",
            onAction = { panel = !panel },
            menu = {
                Item(state?.batteryLine() ?: "No earbuds chosen", enabled = false, onClick = {})
                if (state?.sound != null && buds != null) {
                    Separator()
                    ListeningMode.entries.forEach { mode ->
                        CheckboxItem(mode.label(), checked = state.sound?.mode == mode) { scope.launch { buds.apply(Change.Mode(mode)) } }
                    }
                }
                Separator()
                Item("Open Blazbuds", onClick = { shown = true })
                Item("Quit", onClick = ::exitApplication)
            },
        )
    }

    if (shown) {
        Window(
            onCloseRequest = { if (trayWorks) shown = false else exitApplication() },
            state = windowState,
            title = "Blazbuds",
            icon = AppIcon,
        ) {
            // Never narrower or shorter than the phone layout, so nothing breaks.
            window.minimumSize = Dimension(360, 560)
            if (hub != null) BudsApp(hub, prefs) else BudsApp(demoBuds!!, prefs)
        }
    }

    if (panel && buds != null && state != null) {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val panelState = rememberWindowState(
            size = DpSize(360.dp, 330.dp),
            position = WindowPosition((screen.x + screen.width - 380).dp, (screen.y + 12).dp),
        )
        Window(
            onCloseRequest = { panel = false },
            state = panelState,
            title = "Blazbuds",
            icon = AppIcon,
            undecorated = true,
            resizable = false,
            alwaysOnTop = true,
        ) {
            LaunchedEffect(Unit) {
                window.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) { panel = false }
                })
            }
            BudsTheme(prefs.theme) {
                QuickPanel(state, change = { scope.launch { buds.apply(it) } }, openApp = { shown = true; panel = false })
            }
        }
    }

    if (!shown && !trayWorks) exitApplication()
}

