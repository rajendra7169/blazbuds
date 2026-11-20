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
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.rememberTrayState
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
import com.blazify.buds.core.link.DesktopBluetooth
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

/** A computer: Bluetooth through BlueZ on Linux and through Windows' own sockets on Windows; the rest is shared. */
object DesktopPlatform : Platform {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    private val bluetooth = DesktopBluetooth.here()

    /** The tray icon the app shows, which carries the notices on Windows. */
    var tray: TrayState? = null

    /** A desktop notification: the system's own notifier on Linux, a notice from the tray icon on Windows. */
    override fun notify(title: String, text: String) {
        if (!windows) {
            runCatching { ProcessBuilder("notify-send", "-a", "Blazbuds", "-i", "audio-headphones", title, text).start() }
            return
        }
        tray?.let { it.sendNotification(Notification(title, text, Notification.Type.Info)); return }
        // No tray of our own yet: a short-lived tray icon shows the notice, then goes.
        runCatching {
            if (!java.awt.SystemTray.isSupported()) return
            val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val icon = java.awt.TrayIcon(image, "Blazbuds")
            java.awt.SystemTray.getSystemTray().add(icon)
            icon.displayMessage(title, text, java.awt.TrayIcon.MessageType.INFO)
            javax.swing.Timer(10_000) { java.awt.SystemTray.getSystemTray().remove(icon) }.apply { isRepeats = false; start() }
        }
    }

    override fun bluetoothOn(): Boolean = bluetooth.radioOn()

    override val look: SystemLook = System.getProperty("os.name").let {
        when {
            it.startsWith("Windows") -> SystemLook.Windows
            it.startsWith("Mac") -> SystemLook.Mac
            else -> SystemLook.Linux
        }
    }

    /** A Soundcore model's control channel: its own service, else the plain serial port, as OpenSCQ30 falls back. */
    private fun soundcoreLink(address: String, service: String): com.blazify.buds.core.link.Connection {
        val serial = com.blazify.buds.core.soundcore.generic.SoundcoreModel.SERIAL_PORT
        return try {
            bluetooth.open(address, service)
        } catch (e: com.blazify.buds.core.link.LinkException) {
            if (e.busy || service == serial) throw e
            runCatching { bluetooth.open(address, serial) }.getOrElse { throw e }
        }
    }

    override suspend fun paired(): List<Device> = bluetooth.devices()

    override suspend fun peek(device: Device): Pair<Boolean, Int?> = bluetooth.info(device.address)

    override fun open(device: Device, scope: CoroutineScope): Buds = when (Catalog.model(device).family) {
        com.blazify.buds.core.catalog.Family.Soundcore -> Catalog.model(device).soundcore?.let { model ->
            com.blazify.buds.core.soundcore.generic.GenericSoundcoreBuds(
                device.name.trim().removePrefix("soundcore").removePrefix("Soundcore").trim().ifEmpty { model.name },
                model, scope, { soundcoreLink(device.address, model.service) },
            ).also { it.start() }
        } ?: SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, { bluetooth.open(device.address, A3947.SERVICE) })
            .also { it.start() }
        com.blazify.buds.core.catalog.Family.Huawei -> com.blazify.buds.core.huawei.HuaweiBuds(
            device.name, scope,
            listOf(
                { bluetooth.open(device.address, com.blazify.buds.core.huawei.HuaweiBuds.SERIAL) },
                { bluetooth.rfcomm(device.address, 1) },
                { bluetooth.rfcomm(device.address, 16) },
            ),
        ).also { it.start() }
        com.blazify.buds.core.catalog.Family.Jieli -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, { bluetooth.open(device.address, com.blazify.buds.core.huawei.HuaweiBuds.SERIAL) }).also { it.start() }
        com.blazify.buds.core.catalog.Family.Samsung -> com.blazify.buds.core.samsung.SamsungModel.of(device.name)!!.let { model ->
            com.blazify.buds.core.samsung.SamsungBuds(device.name, model, scope, { bluetooth.open(device.address, model.service) }).also { it.start() }
        }
        com.blazify.buds.core.catalog.Family.Nothing -> com.blazify.buds.core.nothing.NothingBuds(device.name, scope, { bluetooth.open(device.address, com.blazify.buds.core.nothing.NothingBuds.SERVICE) }).also { it.start() }
        com.blazify.buds.core.catalog.Family.Melody -> com.blazify.buds.core.oneplus.MelodyBuds(
            device.name, scope,
            listOf(
                { bluetooth.open(device.address, com.blazify.buds.core.oneplus.Melody.SERVICE) },
                { bluetooth.open(device.address, com.blazify.buds.core.oneplus.Melody.SERIAL) },
            ),
        ).also { it.start() }
        com.blazify.buds.core.catalog.Family.Sony -> com.blazify.buds.core.sony.SonyBuds(
            device.name, scope,
            listOf(
                { bluetooth.open(device.address, com.blazify.buds.core.sony.Sony.SERVICE_V2) },
                { bluetooth.open(device.address, com.blazify.buds.core.sony.Sony.SERVICE_V1) },
            ),
        ).also { it.start() }
        com.blazify.buds.core.catalog.Family.None -> {
            // Unknown earbuds: try JieLi's login once (many budget pairs use that chip), remember, else the basics.
            val basic = { BasicBuds(device.name, scope) { bluetooth.info(device.address) }.also { it.start() } }
            val store = java.util.prefs.Preferences.userRoot().node("blazbuds/tried")
            val key = "jieli_" + device.address.replace(":", "")
            val open = { bluetooth.open(device.address, com.blazify.buds.core.huawei.HuaweiBuds.SERIAL) }
            when (store.get(key, null)) {
                "false" -> basic()
                "true" -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, open).also { it.start() }
                else -> com.blazify.buds.core.TryingBuds(
                    scope,
                    { verdict -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, open, verdict = verdict).also { it.start() } },
                    basic,
                    { works -> store.put(key, works.toString()) },
                )
            }
        }
    }

    override val hasBluetoothSettings: Boolean = true

    override val canFind: Boolean = true

    private var clip: javax.sound.sampled.Clip? = null

    /** Plays the find sound on the computer's sound output, which is the earbuds while they are connected. */
    override fun findSound(left: Boolean, right: Boolean) {
        if (!left && !right) stopSound() else playSound(com.blazify.buds.core.FindTone.samples(left, right), loop = true)
    }

    override fun listenToRoom(onLevel: (Double) -> Unit): (() -> Unit)? {
        val rate = 16_000f
        val format = javax.sound.sampled.AudioFormat(rate, 16, 1, true, false)
        val line = runCatching { javax.sound.sampled.AudioSystem.getTargetDataLine(format).also { it.open(format) } }.getOrNull() ?: return null
        val going = java.util.concurrent.atomic.AtomicBoolean(true)
        Thread({
            val noise = com.blazify.buds.core.earprint.RoomNoise()
            val bytes = ByteArray(rate.toInt() / 10 * 2)
            val samples = ShortArray(bytes.size / 2)
            line.start()
            while (going.get()) {
                val got = line.read(bytes, 0, bytes.size)
                for (i in 0 until got / 2) samples[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
                val level = noise.level(samples, got / 2, rate.toInt())
                javax.swing.SwingUtilities.invokeLater { if (going.get()) onLevel(level) }
            }
            line.stop()
            line.close()
        }, "blazbuds-room").apply { isDaemon = true }.start()
        return { going.set(false) }
    }

    override fun playSound(samples: ShortArray, loop: Boolean) {
        stopSound()
        runCatching {
            val bytes = ByteArray(samples.size * 2)
            samples.forEachIndexed { i, v -> bytes[i * 2] = v.toByte(); bytes[i * 2 + 1] = (v.toInt() shr 8).toByte() }
            val format = javax.sound.sampled.AudioFormat(com.blazify.buds.core.FindTone.RATE.toFloat(), 16, 2, true, false)
            val next = javax.sound.sampled.AudioSystem.getClip()
            next.open(format, bytes, 0, bytes.size)
            if (loop) next.loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY) else next.start()
            clip = next
        }
    }

    override fun stopSound() {
        clip?.let { it.stop(); it.close() }
        clip = null
    }

    /** A computer has no share sheet; the text goes to the clipboard, ready to paste anywhere. */
    override fun share(text: String) {
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
    }

    override fun bluetoothSettings() {
        if (windows) {
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("ms-settings:bluetooth")) }
                .recoverCatching { ProcessBuilder("cmd", "/c", "start", "", "ms-settings:bluetooth").start() }
            return
        }
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
    val hub = remember { if (demo) null else Hub(DesktopPlatform, scope) }
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
        val trayState = rememberTrayState()
        androidx.compose.runtime.DisposableEffect(trayState) {
            DesktopPlatform.tray = trayState
            onDispose { DesktopPlatform.tray = null }
        }
        Tray(
            state = trayState,
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
                androidx.compose.runtime.CompositionLocalProvider(com.blazify.buds.ui.LocalPrefs provides prefs) {
                    QuickPanel(state, change = { scope.launch { buds.apply(it) } }, openApp = { shown = true; panel = false })
                }
            }
        }
    }

    if (!shown && !trayWorks) exitApplication()
}

