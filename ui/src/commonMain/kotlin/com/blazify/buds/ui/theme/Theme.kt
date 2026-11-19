package com.blazify.buds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blazify.buds.ui.resources.Res
import com.blazify.buds.ui.resources.figtree
import org.jetbrains.compose.resources.Font

/**
 * The app's colours. Quiet cool neutrals carry the screens; teal marks what is on; the earbuds' glow takes the colour
 * of the listening mode.
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val cancelling: Color,
    val transparency: Color,
    val good: Color,
    val warn: Color,
    val low: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    background = Color(0xFFF5F7F8),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFEDF1F3),
    line = Color(0xFFE1E6E9),
    text = Color(0xFF121619),
    muted = Color(0xFF5A646C),
    accent = Color(0xFF0F9488),
    onAccent = Color(0xFFFFFFFF),
    cancelling = Color(0xFF5560E0),
    transparency = Color(0xFF4FB3F0),
    good = Color(0xFF2E9D5B),
    warn = Color(0xFFD99A1E),
    low = Color(0xFFD64545),
    dark = false,
)

val DarkPalette = Palette(
    background = Color(0xFF0C0F12),
    card = Color(0xFF151A1F),
    raised = Color(0xFF1D2329),
    line = Color(0xFF252C33),
    text = Color(0xFFE9EDF0),
    muted = Color(0xFF8B96A0),
    accent = Color(0xFF3DD5C3),
    onAccent = Color(0xFF04201C),
    cancelling = Color(0xFF7C86F0),
    transparency = Color(0xFF6CC4F5),
    good = Color(0xFF4CC27F),
    warn = Color(0xFFF0B444),
    low = Color(0xFFF06A6A),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

enum class ThemeChoice { System, Light, Dark }

@Composable
fun figtree(): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(Res.font.figtree, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

@Composable
fun BudsTheme(choice: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    val family = figtree()
    fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
    )
    // One scale for the whole app: 12, 14, 16, 20, 28.
    val typography = Typography(
        headlineMedium = style(28, FontWeight.Bold, 34, -0.3),
        titleLarge = style(20, FontWeight.SemiBold, 26),
        titleMedium = style(16, FontWeight.SemiBold, 22),
        bodyLarge = style(16, FontWeight.Normal, 22),
        bodyMedium = style(14, FontWeight.Normal, 20),
        labelLarge = style(14, FontWeight.Medium, 18),
        labelMedium = style(12, FontWeight.Medium, 16, 0.4),
        labelSmall = style(12, FontWeight.Normal, 16),
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent, background = palette.background,
            surface = palette.card, onSurface = palette.text, onSurfaceVariant = palette.muted,
            surfaceVariant = palette.raised, outline = palette.line, surfaceContainerHighest = palette.raised,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent, background = palette.background,
            surface = palette.card, onSurface = palette.text, onSurfaceVariant = palette.muted,
            surfaceVariant = palette.raised, outline = palette.line, surfaceContainerHighest = palette.raised,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** How long things move: quick, and never past 350 ms. */
object Motion {
    const val QUICK = 150
    const val NORMAL = 220
    const val SLOW = 350
}
