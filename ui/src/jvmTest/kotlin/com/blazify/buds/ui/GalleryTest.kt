package com.blazify.buds.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GalleryTest {
    private val liberty = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)

    @Test
    fun theRepositorysListReads() {
        val file = listOf(File("../community/sounds.json"), File("community/sounds.json")).first { it.isFile }
        val sounds = assertNotNull(Gallery.parse(file.readText()))
        assertTrue(sounds.size >= 5)
        val forLiberty = Gallery.fitting(sounds, liberty)
        assertTrue(forLiberty.map { it.name }.containsAll(listOf("Vocal clarity", "Warm bass", "Podcast")))
        // Each one travels as a code that reads back as the same sound.
        forLiberty.forEach { assertEquals(it.gains, SavedSound.fromCode(it.code())?.gains) }
    }

    @Test
    fun onlySoundsForTheseBandsShow() {
        val json = """{"sounds": [
            {"name": "A", "about": "x", "by": "me", "bands": [100, 200], "gains": [1, -1.5]},
            {"name": "B", "bands": [63, 125, 250], "gains": [0, 1, 2]},
            {"name": "Broken", "bands": [1, 2], "gains": [1]},
            {"name": "", "bands": [100, 200], "gains": [1, 1]}
        ]}"""
        val sounds = assertNotNull(Gallery.parse(json))
        assertEquals(listOf("A", "B"), sounds.map { it.name })
        assertEquals(listOf("A"), Gallery.fitting(sounds, listOf(100, 200)).map { it.name })
        assertEquals(listOf(1.0, -1.5), sounds[0].gains)
    }

    @Test
    fun notTheListIsNothing() {
        assertNull(Gallery.parse("<html>Not found</html>"))
        assertNull(Gallery.parse("{\"other\": 1}"))
    }
}
