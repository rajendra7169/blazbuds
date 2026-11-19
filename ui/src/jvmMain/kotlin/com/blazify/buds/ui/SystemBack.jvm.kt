package com.blazify.buds.ui

import androidx.compose.runtime.Composable

/** Computers have no back gesture; each page has its own back arrow. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit
