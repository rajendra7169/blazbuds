package com.blazify.buds.android

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blazify.buds.R
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.ui.LocalPrefs
import com.blazify.buds.ui.Prefs
import com.blazify.buds.ui.blocks.ConnectCard
import com.blazify.buds.ui.theme.BudsTheme
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The card that slides up over whatever is on screen when the person's earbuds connect (as they open the case):
 * the case opening, and each battery once read. It needs "display over other apps", goes by itself after a few
 * seconds, opens the app on a tap, and stays away while the app itself is open.
 */
object ConnectPopup {
    /** Whether the app's own window is in front, where Home already shows all this. */
    @Volatile
    var appInFront = false

    private var shownAt = 0L
    private var showing: ComposeView? = null

    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(context)

    fun show(context: Context, address: String, name: String) {
        val now = System.currentTimeMillis()
        // Both earbuds can each raise "connected"; one card is enough.
        if (showing != null || now - shownAt < 20_000) return
        shownAt = now
        val app = context.applicationContext
        val windows = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = OverlayOwner()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val state: MutableState<BudsState?> = mutableStateOf(null)
        val prefs = Prefs()
        lateinit var view: ComposeView
        fun remove() {
            if (showing !== view) return
            showing = null
            runCatching { windows.removeView(view) }
            owner.destroy()
            scope.cancel()
        }
        view = ComposeView(app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    shown = true
                    delay(9_000)
                    shown = false
                    delay(400)
                    remove()
                }
                BudsTheme(prefs.theme) {
                    CompositionLocalProvider(LocalPrefs provides prefs) {
                        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                            AnimatedVisibility(shown, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                                ConnectCard(
                                    name = state.value?.name?.ifBlank { null } ?: name,
                                    state = state.value,
                                    onOpen = {
                                        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        remove()
                                    },
                                    onClose = { remove() },
                                )
                            }
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // Taps outside go to whatever is underneath; the card never takes the keyboard.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        runCatching {
            windows.addView(view, params)
            showing = view
            owner.start()
            android.util.Log.i(TAG, "card shown for $name")
        }.onFailure {
            android.util.Log.w(TAG, "card could not show: $it")
            scope.cancel()
            return
        }
        // The batteries: from the app's own link when it has one, else a short look of its own.
        scope.launch {
            val live = Remote.live?.state?.value?.takeIf { it.isReady && it.name.isNotBlank() }
            state.value = live ?: Remote.battery(app)
            android.util.Log.i(TAG, "card battery: ${state.value?.let { "L ${it.left?.percent} R ${it.right?.percent} case ${it.case?.percent}" } ?: "not read"}")
        }
    }

    /** A lifecycle for a view that lives outside any activity. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performRestore(Bundle())
        }

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

/**
 * Hears every Bluetooth connection, even with the app closed, and greets the person's own earbuds with the card when
 * they ask for it and allowed it.
 */
class ConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val store = Settings()
        val on = store.getBoolean(Prefs.CONNECT_POPUP, true)
        val allowed = ConnectPopup.allowed(context)
        android.util.Log.i(TAG, "connected: popup on=$on, allowed over apps=$allowed, app in front=${ConnectPopup.appInFront}")
        if (!on || ConnectPopup.appInFront) return
        if (!allowed) {
            // The card is on but the phone hasn't allowed it over other apps: say so once, with the way to allow it.
            if (Remote.chosen()?.address.equals(intentDevice(intent)?.address, ignoreCase = true) && !store.getBoolean(NUDGED, false)) {
                store.putBoolean(NUDGED, true)
                nudge(context)
            }
            return
        }
        val device = intentDevice(intent) ?: return
        // Only earbuds the app knows: the pair it was last using.
        val chosen = Remote.chosen() ?: return
        if (!chosen.address.equals(device.address, ignoreCase = true)) {
            android.util.Log.i(TAG, "connected: not the chosen pair, no card")
            return
        }
        android.util.Log.i(TAG, "connected: ${chosen.name}, card in a moment")
        // A moment for the earbuds to settle before the card asks them anything.
        Handler(Looper.getMainLooper()).postDelayed({ ConnectPopup.show(context, chosen.address, chosen.name) }, 1_200)
    }
}

private const val TAG = "BlazbudsConnect"
private const val NUDGED = "connect_popup_nudged"

@Suppress("DEPRECATION")
private fun intentDevice(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

/** The one notice that the card needs a permission, opening the phone's page for it. */
private fun nudge(context: Context) {
    val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
    val channel = "connect_card"
    manager.createNotificationChannel(android.app.NotificationChannel(channel, "Connect card", android.app.NotificationManager.IMPORTANCE_DEFAULT))
    val allow = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName))
    val tap = android.app.PendingIntent.getActivity(context, 7, allow, android.app.PendingIntent.FLAG_IMMUTABLE)
    val text = "Allow Blazbuds to show over other apps, and the case and each battery appear as you open the case."
    val notice = android.app.Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle("See your battery as you open the case")
        .setContentText(text)
        .setStyle(android.app.Notification.BigTextStyle().bigText(text))
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    runCatching { manager.notify(channel.hashCode(), notice) }
}
