package com.blazify.buds.ui

import androidx.compose.runtime.Composable

/** The iPhone has no back button; each page has its own back arrow, as on a computer. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit
