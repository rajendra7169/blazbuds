package com.blazify.buds.android

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.blazify.buds.ui.BudsApp
import com.blazify.buds.ui.Hub

class MainActivity : ComponentActivity() {
    private var granted by mutableIntStateOf(0)
    private var hub: Hub? = null

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 12 and later ask once before an app may talk to paired devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        setContent {
            val scope = rememberCoroutineScope()
            val platform = remember { AndroidPlatform(applicationContext) }
            val hub = remember { Hub(platform, scope).also { this.hub = it } }
            val current by hub.current.collectAsState()
            LaunchedEffect(current) { current?.let { platform.watchForWidget(it, scope) } }
            // Look again once Bluetooth is allowed.
            LaunchedEffect(granted) { hub.reload() }
            BudsApp(hub)
        }
    }

    /** Bluetooth switched on or off, from the shade or settings: look again at once. */
    private val bluetoothChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            hub?.refresh()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(bluetoothChanged, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        hub?.watch(true)
        hub?.refresh()
    }

    override fun onStop() {
        unregisterReceiver(bluetoothChanged)
        // In the background the earbuds aren't asked every few seconds.
        hub?.watch(false)
        super.onStop()
    }
}
