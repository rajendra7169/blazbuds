package com.blazify.buds.core.eq

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The built-in corrections against the measurements they came from: the Kotlin fit, which also fits imported curves
 * in the app, must give the gains tools/autoeq/refresh.py wrote, to within one step of the earbuds.
 */
class TunedTableTest {
    private val measurements = listOf(File("../tools/autoeq/measurements"), File("tools/autoeq/measurements")).firstOrNull { it.isDirectory }

    @Test
    fun theKotlinFitAgreesWithTheTable() {
        val dir = measurements ?: error("tools/autoeq/measurements is missing")
        Tuned.all.forEach { curve ->
            val file = File(dir, curve.file + ".txt")
            assertTrue(file.isFile, "${curve.label}: ${file.path} is missing")
            val points = EqText.graphicEq(file.readText())
            assertTrue(points.size > 50, "${curve.label}: no GraphicEQ curve")
            val fitted = EqFit.fit(points, curve.frequencies, step = curve.step)
            fitted.zip(curve.gains).forEach { (kotlin, table) ->
                assertTrue(abs(kotlin - table) <= curve.step + 1e-9, "${curve.label} ${curve.frequencies}: Kotlin $fitted, table ${curve.gains}")
            }
        }
    }
}
