package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.catalog.Catalog
import com.blazify.buds.core.catalog.Family
import com.blazify.buds.core.link.Device
import com.blazify.buds.core.soundcore.hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundcoreModelsTest {
    @Test
    fun eachModelUsesTheServiceItsSampleAnnounces() {
        var checked = 0
        for (model in SoundcoreModels.all) {
            val (_, uuid) = SoundcoreSamples.NAMES[model.code] ?: continue
            assertEquals(uuid, model.service, "${model.code}'s control service")
            checked++
        }
        assertTrue(checked >= 30, "only $checked models have samples")
        // Models without a sample: the plain serial port, or a service of Soundcore's own.
        SoundcoreModels.all.forEach { assertTrue(it.service == SoundcoreModel.SERIAL_PORT || it.service.startsWith("0cf12d31-fac3-4553-bd80-d6832e7"), it.code) }
    }

    @Test
    fun codesAreUnique() {
        assertEquals(SoundcoreModels.all.size, SoundcoreModels.all.map { it.code }.toSet().size)
    }

    @Test
    fun everySampleReadsAsItsModel() {
        for ((code, state) in SoundcoreSamples.STATE) {
            val model = SoundcoreModels.byCode(code) ?: continue
            val parsed = model.parse(hex(state))
            assertNotNull(model.state(parsed, model.name).model, code)
        }
    }

    @Test
    fun findsModelsByTheNamesTheyGive() {
        for ((code, entry) in SoundcoreSamples.NAMES) {
            val model = SoundcoreModels.byCode(code) ?: continue
            assertEquals(model, SoundcoreModels.find(entry.first), "${entry.first} should be $code")
        }
        assertEquals("A3062", SoundcoreModels.find("Soundcore Space One Pro")?.code)
        assertEquals("A3035", SoundcoreModels.find("soundcore Space One")?.code)
        assertEquals("A3930", SoundcoreModels.find("Soundcore Liberty 2 Pro+")?.code)
        assertEquals("A3909", SoundcoreModels.find("Soundcore Liberty 2 Pro")?.code)
        assertEquals("A3959", SoundcoreModels.find("soundcore R50i NC")?.code)
        assertEquals("A3949", SoundcoreModels.find("soundcore R50i")?.code)
        assertNull(SoundcoreModels.find("Soundcore Liberty 9"))
        assertNull(SoundcoreModels.find("soundcore"))
    }

    @Test
    fun theCatalogKeepsTheLiberty4NcOnItsOwnDriver() {
        val nc = Catalog.model(Device("soundcore Liberty 4 NC", "00:00:00:00:00:01", true))
        assertEquals(Family.Soundcore, nc.family)
        assertNull(nc.soundcore)
        val q30 = Catalog.model(Device("Soundcore Life Q30", "00:00:00:00:00:02", true))
        assertEquals(Family.Soundcore, q30.family)
        assertEquals("A3028", q30.soundcore?.code)
        assertEquals("A3953", Catalog.model(Device("soundcore Liberty 4", "00:00:00:00:00:03", true)).soundcore?.code)
    }
}
