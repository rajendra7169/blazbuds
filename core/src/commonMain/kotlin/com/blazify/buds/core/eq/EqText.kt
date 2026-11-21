package com.blazify.buds.core.eq

/** Equalizer settings as text, the way Equalizer APO and AutoEq write them. */
object EqText {
    /** The points of a "GraphicEQ: 20 -1.2; 21 -1.1; …" line, in hertz and decibels, rising; empty when there is none. */
    fun graphicEq(text: String): List<Pair<Double, Double>> {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("GraphicEQ:", ignoreCase = true) } ?: return emptyList()
        return line.substringAfter(':').split(';').mapNotNull { pair ->
            val parts = pair.trim().split(Regex("\\s+"))
            if (parts.size != 2) return@mapNotNull null
            val hz = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val db = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (hz <= 0 || !db.isFinite()) null else hz to db
        }.sortedBy { it.first }
    }
}
