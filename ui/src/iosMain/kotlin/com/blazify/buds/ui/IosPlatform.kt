package com.blazify.buds.ui

import com.blazify.buds.core.BasicBuds
import com.blazify.buds.core.Logbook
import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Family
import com.blazify.buds.core.catalog.Support
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Buds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * The iPhone. iOS lists no paired devices and has no serial ports, so the earbuds are the ones connected now that
 * answer on a control service the app knows, reached through Swift ([BluetoothBridge]); the brand drivers are the
 * same as everywhere else. What iOS doesn't allow (a background battery look, the card over other apps, shortcuts,
 * the widget, opening Bluetooth settings) stays off through the [Platform] defaults.
 */
class IosPlatform(private val bridge: BluetoothBridge) : Platform {
    override val look: SystemLook = SystemLook.Ios

    /** How each pair was last found, by id: which service answered. */
    @Volatile
    private var routes: Map<String, String> = emptyMap()

    override suspend fun paired(): List<Device> {
        val found = bridge.connectedEarbuds().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3 || parts[0].isBlank()) null else Triple(parts[0], parts[1].ifBlank { "Earbuds" }, parts[2])
        }
        routes = routes + found.associate { it.first to it.third }
        return found
            .map { (id, name, route) -> Device(name, id, connected = true) to route }
            // Found only through Fast Pair, which many devices offer: listed when the app knows them by name.
            .filter { (device, route) -> route != "fastpair" || Catalog.model(device).support != Support.Basic }
            .map { it.first }
    }

    override fun bluetoothOn(): Boolean = bridge.isOn()

    override fun open(device: Device, scope: CoroutineScope): Buds {
        val id = device.address
        val route = routes[id].orEmpty()
        val model = Catalog.model(device)
        val open: suspend () -> Connection = { link(id, "") }
        val basic = { BasicBuds(device.name, scope) { (id in routesNow()) to null }.also { it.start() } }
        return when (model.family) {
            Family.Soundcore -> model.soundcore?.let { soundcore ->
                com.blazify.buds.core.soundcore.generic.GenericSoundcoreBuds(
                    device.name.trim().removePrefix("soundcore").removePrefix("Soundcore").trim().ifEmpty { soundcore.name },
                    soundcore, scope, open, ::note,
                ).also { it.start() }
            } ?: com.blazify.buds.core.soundcore.SoundcoreBuds(device.name.removePrefix("soundcore ").trim(), scope, open, ::note).also { it.start() }
            Family.Huawei -> com.blazify.buds.core.huawei.HuaweiBuds(device.name, scope, listOf(open), ::note).also { it.start() }
            Family.Jieli -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, open, ::note).also { it.start() }
            Family.Nothing -> com.blazify.buds.core.nothing.NothingBuds(device.name, scope, open, ::note).also { it.start() }
            Family.Melody -> com.blazify.buds.core.oneplus.MelodyBuds(device.name, scope, listOf(open), ::note).also { it.start() }
            Family.Sony -> com.blazify.buds.core.sony.SonyBuds(
                device.name, scope,
                if (id.startsWith("ea:")) listOf({ link(id, SONY_V2) }, { link(id, SONY_V1) }) else listOf(open),
                ::note,
            ).also { it.start() }
            // Galaxy Buds from the Buds2 on speak only over the classic link, which iOS keeps to itself.
            Family.Samsung -> basic()
            Family.None -> when (route) {
                // A JieLi service on earbuds the app doesn't know by name: try JieLi's login once, as elsewhere.
                "jieli" -> com.blazify.buds.core.TryingBuds(
                    scope,
                    { verdict -> com.blazify.buds.core.jieli.JieliBuds(device.name, scope, open, ::note, verdict).also { it.start() } },
                    basic,
                    {},
                )
                else -> basic()
            }
        }
    }

    /** The ids connected now, for earbuds the app only shows the basics of. */
    private fun routesNow(): Set<String> = bridge.connectedEarbuds().map { it.substringBefore('\t') }.toSet()

    /** Opens a link through Swift and waits for it, as a socket's connect would. */
    private suspend fun link(id: String, route: String): Connection {
        val pipe = BudsPipe()
        bridge.connect(id, route, pipe)
        try {
            withTimeout(OPEN_TIMEOUT) { pipe.ready() }
        } catch (e: TimeoutCancellationException) {
            pipe.close()
            throw LinkException("The earbuds didn't open their control channel")
        } catch (e: CancellationException) {
            pipe.close()
            throw e
        } catch (e: LinkException) {
            pipe.close()
            throw e
        }
        return pipe
    }

    override val hasBluetoothCheck: Boolean = true

    override suspend fun bluetoothCheck(): String = suspendCancellableCoroutine { done ->
        bridge.check { text ->
            Logbook.add("Bluetooth check ran (${text.lines().size} lines)")
            if (done.isActive) done.resume(text)
        }
    }

    override fun share(text: String) {
        val top = topController() ?: return
        top.presentViewController(UIActivityViewController(listOf(text), null), animated = true, completion = null)
    }

    /**
     * A low-battery notice. The app reads the batteries only while it runs, which on the iPhone includes a while in
     * the background as long as it holds a link to the earbuds.
     */
    override fun notify(title: String, text: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { allowed, _ ->
            if (!allowed) return@requestAuthorizationWithOptions
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(text)
            }
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("battery", content, null), null)
        }
    }

    override val canFind: Boolean = true

    private var player: AVAudioPlayer? = null

    /** The find sound plays as music does, so it goes to the earbuds while they are connected. */
    override fun findSound(left: Boolean, right: Boolean) {
        if (!left && !right) stopSound() else playSound(com.blazify.buds.core.FindTone.samples(left, right), loop = true)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun playSound(samples: ShortArray, loop: Boolean) {
        stopSound()
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        }
        val sound = AVAudioPlayer(wave(samples, com.blazify.buds.core.FindTone.RATE).toData(), null)
        sound.numberOfLoops = if (loop) -1 else 0
        sound.prepareToPlay()
        sound.play()
        player = sound
    }

    override fun stopSound() {
        player?.stop()
        player = null
    }

    private fun topController(): UIViewController? {
        @Suppress("DEPRECATION")
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: break
        return top
    }

    /** Lets the screen look again when Bluetooth or the connected earbuds change. */
    fun watch(changed: () -> Unit) = bridge.watch(changed)

    private fun note(line: String) = Logbook.add(line)

    private companion object {
        const val OPEN_TIMEOUT = 15_000L
        const val SONY_V2 = "jp.co.sony.songpal.mdr.link2"
        const val SONY_V1 = "jp.co.sony.songpal.mdr.link"
    }
}

/** 16-bit stereo samples as a WAV file, which the iPhone's player takes as it is. */
internal fun wave(samples: ShortArray, rate: Int): ByteArray {
    val size = samples.size * 2
    val out = ByteArray(44 + size)
    fun text(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
    fun int(at: Int, v: Int) = (0 until 4).forEach { out[at + it] = (v shr (8 * it)).toByte() }
    fun short(at: Int, v: Int) = (0 until 2).forEach { out[at + it] = (v shr (8 * it)).toByte() }
    text(0, "RIFF"); int(4, 36 + size); text(8, "WAVE")
    text(12, "fmt "); int(16, 16); short(20, 1); short(22, 2); int(24, rate); int(28, rate * 4); short(32, 4); short(34, 16)
    text(36, "data"); int(40, size)
    samples.forEachIndexed { i, v -> short(44 + i * 2, v.toInt()) }
    return out
}
