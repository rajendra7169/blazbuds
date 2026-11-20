package com.blazify.buds.core.nothing

import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Family
import com.blazify.buds.core.link.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The names Nothing and CMF products give themselves over Bluetooth pick the Nothing driver, and others don't. */
class NothingCatalogTest {
    @Test
    fun theCatalogKnowsThemByName() {
        listOf("Nothing Ear", "Nothing Ear (a)", "Ear (2)", "ear (1)", "Nothing ear (1)", "Ear (stick)", "Nothing Headphone (1)", "CMF Buds Pro 2", "CMF Neckband Pro", "CMF Headphone Pro", "CMF Buds")
            .forEach { assertEquals(Family.Nothing, Catalog.model(Device(it, "00:00:00:00:00:00", true)).family, it) }
        listOf("Galaxy Buds Pro", "Earbuds X7e", "Liberty 4 NC").forEach {
            assertTrue(Catalog.model(Device(it, "00:00:00:00:00:00", true)).family != Family.Nothing, it)
        }
    }
}
