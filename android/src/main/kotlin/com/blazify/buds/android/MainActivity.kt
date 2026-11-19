package com.blazify.buds.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
            val hub = remember { Hub(AndroidPlatform(applicationContext), scope).also { this.hub = it } }
            // Look again once Bluetooth is allowed.
            LaunchedEffect(granted) { hub.reload() }
            BudsApp(hub)
        }
    }

    override fun onStart() {
        super.onStart()
        hub?.watch(true)
    }

    override fun onStop() {
        // In the background the earbuds aren't asked every few seconds.
        hub?.watch(false)
        super.onStop()
    }
}
