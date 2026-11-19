package com.blazify.buds.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line drawings of what each sound is about, shown large and dim across the preset tiles. */
object SoundArt {
    val Guitar = art("M9 21a4.5 4.5 0 0 1-1.6-8.7 3.2 3.2 0 0 1 4.3-4.3A4.5 4.5 0 0 1 9 21zM11.6 12.4L19 5M17.5 3.5l3 3M8.5 16.5a1.3 1.3 0 1 0 0-.1M6.8 14.6l2.6 2.6")
    val Piano = art("M3 5h18v14H3zM7.5 5v14M12 5v14M16.5 5v14", filled = "M6.2 5h2.6v8H6.2zM10.7 5h2.6v8h-2.6zM15.2 5h2.6v8h-2.6z")
    val Mic = art("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5.5 10.5a6.5 6.5 0 0 0 13 0M12 17v4M8.5 21h7")
    val Drum = art("M4 9c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 9v7c0 1.7 3.6 3 8 3s8-1.3 8-3V9M8 6.5L5 2.5M16 6.5l3-4M4 12.5l4 2 4-2 4 2 4-2")
    val Sax = art("M14 3h4M16 3v8.5a5 5 0 0 1-10 0V10h3v1.5a2 2 0 0 0 4 0V6M16 6.5h1.5M16 9h1.5M6 10l-1.5-1")
    val Note = art("M9 18a2.5 2.5 0 1 1-2.5-2.5H9V5.5l10-2V15a2.5 2.5 0 1 1-2.5-2.5H19M9 9l10-2")
    val Headphones = art("M4.5 14v-1.5a7.5 7.5 0 0 1 15 0V14M4.5 14h3v6h-1.5a1.5 1.5 0 0 1-1.5-1.5zM19.5 14h-3v6h1.5a1.5 1.5 0 0 0 1.5-1.5z")
    val Speaker = art("M6 3h12v18H6zM12 11a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM12 6a1 1 0 1 0 0 .1")
    val Book = art("M3.5 5.5a2 2 0 0 1 2-2H11v16.5H5.5a2 2 0 0 0-2 2zM20.5 5.5a2 2 0 0 0-2-2H13v16.5h5.5a2 2 0 0 1 2 2z")
    val Vinyl = art("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM12 6a6 6 0 0 1 6 6M12 18a6 6 0 0 1-6-6")
    val Bolt = art("M13 2.5L5.5 13.5H12l-1 8 7.5-11H12z")
    val Cup = art("M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h2a2 2 0 0 1 0 4h-2M8.5 3.5c0 1.5 1.5 1.5 1.5 3M12.5 3.5c0 1.5 1.5 1.5 1.5 3")
    val Sparkle = art("M12 3l1.8 5.4L19 10l-5.2 1.6L12 17l-1.8-5.4L5 10l5.2-1.6zM18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z")
    val Heart = art("M12 20s-7.5-4.6-7.5-10.2A4.2 4.2 0 0 1 12 7.3a4.2 4.2 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z")
    val Disco = art("M12 2.5V6M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12zM6.4 10h11.2M6.4 14h11.2M12 6v12M9 6.8v10.4M15 6.8v10.4")
    val Wave = art("M2 12c1.7-5 3.3-5 5 0s3.3 5 5 0 3.3-5 5 0 3.3 5 5 0")
    val Low = art("M2 14c3-7 5-7 8 0s5 7 8 0 3-4 4-3M2 20h20")
    val High = art("M2 12l2-4 2 8 2-10 2 12 2-12 2 10 2-8 2 6 2-4 2 2")
    val Star = art("M12 3l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 16.8l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z")
    val Violin = art("M10 21a3 3 0 0 1-2.4-4.8 2.6 2.6 0 0 1 0-3.4A3 3 0 0 1 12 9.6a3 3 0 0 1 4.4 3.2 2.6 2.6 0 0 1 0 3.4A3 3 0 0 1 14 21zM12 9.6V2.5M10.8 15h2.4M10.5 3.5h3")
    val Leaf = art("M5 19c0-8 6-14 15-14 0 9-6 15-14 15M5 19l7-7")

    /** The drawing for a preset, by what its name is about. */
    fun forPreset(name: String): ImageVector {
        val n = name.lowercase()
        return when {
            "acoustic" in n -> Guitar
            "piano" in n -> Piano
            "podcast" in n || "vocal" in n -> Mic
            "spoken" in n -> Book
            "jazz" in n -> Sax
            "rock" in n -> Bolt
            "classical" in n -> Violin
            "dance" in n -> Disco
            "electronic" in n -> Wave
            "hip" in n -> Headphones
            "latin" in n -> Drum
            "lounge" in n -> Cup
            "r&b" in n || "rnb" in n -> Heart
            "pop" in n -> Star
            "deep" in n || "bass booster" in n -> Low
            "bass reducer" in n -> Leaf
            "treble" in n -> High
            "small speaker" in n -> Speaker
            "flat" in n -> Wave
            "signature" in n -> Sparkle
            else -> Note
        }
    }

    private fun art(path: String, filled: String? = null): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .apply { if (filled != null) addPath(pathData = PathParser().parsePathString(filled).toNodes(), fill = SolidColor(Color.Black)) }
            .build()
}
