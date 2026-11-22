package com.blazify.buds.desktop

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fetches a real published package the way an update does. Runs only with SELF_UPDATE_LIVE=1. */
class SelfUpdateLiveTest {
    @Test
    fun fetchesTheWholePackageWithProgress() {
        assumeTrue(System.getenv("SELF_UPDATE_LIVE") == "1")
        val seen = mutableListOf<Float?>()
        val url = "https://github.com/rajendra7169/blazbuds/releases/download/v1.3.0/blazbuds_1.3.0_amd64.deb"
        val file = SelfUpdate.fetch(url, "blazbuds_1.3.0_amd64.deb", "test-1.3.0") { seen += it }
        assertEquals(60172898L, file.length())
        assertTrue(seen.isNotEmpty() && seen.last() == 1f, "progress reached 100%")
        println("SELFUPDATE fetched ${file.length()} bytes to $file, ${seen.size} progress steps, last ${seen.last()}")
        file.parentFile.deleteRecursively()
    }
}
