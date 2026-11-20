package com.blazify.buds.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The app's own line icons on a 24-unit grid; their colour comes from the Icon's tint. */
object BudsIcons {
    val Back = line("M15 5l-7 7 7 7")
    val Forward = line("M9 5l7 7-7 7")
    val Check = line("M5 12.5l4.5 4.5L19 7.5")
    val Close = line("M6 6l12 12M18 6L6 18")
    val Settings = line("M10.37 5.19L10.51 2.62A9.5 9.5 0 0 1 13.49 2.62L13.63 5.19A7 7 0 0 1 15.66 6.03L17.58 4.31A9.5 9.5 0 0 1 19.69 6.42L17.97 8.34A7 7 0 0 1 18.81 10.37L21.38 10.51A9.5 9.5 0 0 1 21.38 13.49L18.81 13.63A7 7 0 0 1 17.97 15.66L19.69 17.58A9.5 9.5 0 0 1 17.58 19.69L15.66 17.97A7 7 0 0 1 13.63 18.81L13.49 21.38A9.5 9.5 0 0 1 10.51 21.38L10.37 18.81A7 7 0 0 1 8.34 17.97L6.42 19.69A9.5 9.5 0 0 1 4.31 17.58L6.03 15.66A7 7 0 0 1 5.19 13.63L2.62 13.49A9.5 9.5 0 0 1 2.62 10.51L5.19 10.37A7 7 0 0 1 6.03 8.34L4.31 6.42A9.5 9.5 0 0 1 6.42 4.31L8.34 6.03A7 7 0 0 1 10.37 5.19ZM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val Bolt = line("M13 2.5L5.5 13.5H12l-1 8 7.5-11H12z", strokeless = true)
    val Refresh = line("M20 12a8 8 0 1 1-2.34-5.66M20 4v4.5h-4.5")
    val Bluetooth = line("M7 7.5l10 9-5 4.5V3l5 4.5-10 9")

    // Listening modes: three rings closing in, open waves, a plain ring.
    val Cancelling = line("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", filled = "M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z")
    val Transparency = line("M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM7.5 7.5a6.4 6.4 0 0 0 0 9M16.5 7.5a6.4 6.4 0 0 1 0 9M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8")
    val Normal = line("M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z")

    val Plane = line("M21 15.5l-8-4.5V5a1.5 1.5 0 0 0-3 0v6l-8 4.5V17l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-4.5l8 2.5z")
    val Train = line("M7 3h10a2 2 0 0 1 2 2v10a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5a2 2 0 0 1 2-2zM5 10h14M8 21l2-3M16 21l-2-3", filled = "M8 13.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2zM16 13.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2z")
    val Bus = line("M6 3h12a2 2 0 0 1 2 2v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a2 2 0 0 1 2-2zM4 11h16M7 18v2.5M17 18v2.5M8 6.5h8")
    val Car = line("M5 16v3M19 16v3M3.5 12l2-5.5A2 2 0 0 1 7.4 5h9.2a2 2 0 0 1 1.9 1.5l2 5.5M3 12h18v4H3z")
    val Wind = line("M3 8h11a2.5 2.5 0 1 0-2.5-2.5M3 12h16a2.5 2.5 0 1 1-2.5 2.5M3 16h8a2.5 2.5 0 1 1-2.5 2.5")
    val Voice = line("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5.5 10.5a6.5 6.5 0 0 0 13 0M12 17v4")

    val Game = line("M7 7h10a5 5 0 0 1 4.6 7l-1 2.3a2.5 2.5 0 0 1-4.2.6L14.5 15h-5l-1.9 1.9a2.5 2.5 0 0 1-4.2-.6l-1-2.3A5 5 0 0 1 7 7zM7.5 10v4M5.5 12h4", filled = "M15.5 10.2a1 1 0 1 0 0 2 1 1 0 0 0 0-2zM17.8 12.4a1 1 0 1 0 0 2 1 1 0 0 0 0-2z")
    val Surround = line("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 3c5 0 9 4 9 9M12 21c-5 0-9-4-9-9M3 12c0-5 4-9 9-9M21 12c0 5-4 9-9 9", strokeless = false)
    val Ear = line("M7 9.5a5.5 5.5 0 0 1 11 0c0 3.3-2.7 4.4-3.4 6.2-.6 1.6-1 4.3-3.6 4.3a3 3 0 0 1-3-3M10 9.5a2.5 2.5 0 0 1 5 0c0 1.3-1.2 1.9-1.8 2.6")
    val Lock = line("M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1zM8 11V8a4 4 0 0 1 8 0v3")
    val Hd = line("M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM7 9v6M7 12h4M11 9v6M14 9v6h1.5a3 3 0 0 0 0-6z")
    val Phone = line("M8 2.5h8a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2zM11 18.5h2")
    val Laptop = line("M5 5h14a1 1 0 0 1 1 1v10H4V6a1 1 0 0 1 1-1zM2 19h20")
    val Touch = line("M9 11V5.5a1.5 1.5 0 0 1 3 0V11M12 10V8.5a1.5 1.5 0 0 1 3 0V11M15 10a1.5 1.5 0 0 1 3 0v4.5a6.5 6.5 0 0 1-6.5 6.5h-.8a6 6 0 0 1-4.5-2L4 15.5a1.6 1.6 0 0 1 2.3-2.2L9 15")
    val Equalizer = line("M6 4v6M6 14v6M12 4v2M12 10v10M18 4v9M18 17v3M4 12h4M10 8h4M16 15h4")
    val Find = line("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z", filled = "M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z")
    val Info = line("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5.5M12 7.5v.5")
    val Moon = line("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val Focus = line("M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3", filled = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z")
    val Power = line("M12 3v8M7 6a7.5 7.5 0 1 0 10 0")
    val Plus = line("M12 5v14M5 12h14")
    val Support = line("M4.5 13v-1.5a7.5 7.5 0 0 1 15 0V13M4.5 13h2.5v5H5.5a1 1 0 0 1-1-1zM19.5 13H17v5h1.5a1 1 0 0 0 1-1zM18 18c0 2-2 3-5 3h-1")
    val Home = line("M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1z")
    val Earbuds = line("M10 3a5 5 0 1 0 0 10h0v6.5a1.75 1.75 0 0 0 3.5 0V7.2A5 5 0 0 0 10 3z", filled = "M10.5 6.6a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z")
    val Case = line("M5 9.5h14v5.5a5 5 0 0 1-5 5h-4a5 5 0 0 1-5-5zM5 9.5a4.5 4.5 0 0 1 4.5-4.5h5A4.5 4.5 0 0 1 19 9.5M11 13h2")
    val Trash = line("M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13M10.5 11v5.5M13.5 11v5.5")
    val Copy = line("M9 9h10v11H9zM5 15V4h10")
    val Spatial = line("M12 12m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7")
    val Play = line("M8 5v14l11-7z")
    val Undo = line("M4 5v5h5M4.6 10A8 8 0 1 1 6 17")
    val Download = line("M12 4v11M7 10l5 5 5-5M5 20h14")
    val Share = line("M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    val Battery = line("M4 7h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1zM21 10.5v3")

    private fun line(path: String, filled: String? = null, strokeless: Boolean = false): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (strokeless) SolidColor(Color.Black) else null,
                stroke = if (strokeless) null else SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .apply {
                if (filled != null) {
                    addPath(pathData = PathParser().parsePathString(filled).toNodes(), fill = SolidColor(Color.Black))
                }
            }
            .build()
}
