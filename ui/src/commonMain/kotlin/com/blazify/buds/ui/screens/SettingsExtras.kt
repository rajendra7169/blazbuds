package com.blazify.buds.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Panels one platform adds to Settings, below When they connect: the computer's Home Assistant link. Empty elsewhere.
 */
val LocalSettingsExtras = staticCompositionLocalOf<@Composable ColumnScope.() -> Unit> { {} }
