package com.blazify.buds.ui

import androidx.compose.runtime.Composable

/** The phone's back gesture or button: while [enabled], it calls [onBack] instead of leaving the app. */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
