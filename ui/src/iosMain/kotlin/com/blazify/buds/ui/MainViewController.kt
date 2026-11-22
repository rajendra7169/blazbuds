package com.blazify.buds.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController

/**
 * The whole app for the iPhone: the same screens as on a phone or a computer, over the Bluetooth that Swift brings.
 * Swift calls this once and shows what it returns.
 */
fun MainViewController(bridge: BluetoothBridge): UIViewController = ComposeUIViewController {
    val scope = rememberCoroutineScope()
    val platform = remember { IosPlatform(bridge) }
    val hub = remember { Hub(platform, scope) }
    LaunchedEffect(Unit) {
        // Bluetooth switched on or off, or earbuds came or went: look again.
        platform.watch { hub.refresh() }
        hub.reload()
    }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        // Away from the screen the earbuds aren't asked every few seconds; back on it, look again at once.
        val front = center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, main) { _ ->
            hub.watch(true)
            hub.refresh()
        }
        val back = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ ->
            hub.watch(false)
        }
        onDispose {
            center.removeObserver(front)
            center.removeObserver(back)
            hub.shutdown()
        }
    }
    // iPhone copies are installed by hand, so they don't look for new versions on GitHub.
    BudsApp(hub, watchUpdates = false)
}
