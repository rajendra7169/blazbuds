package com.blazify.buds.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatesTest {
    @Test
    fun comparesVersionsByNumberNotText() {
        assertTrue(Updates.isNewer("1.10.0", "1.9.2"))
        assertTrue(Updates.isNewer("v1.2.1", "1.2.0"))
        assertTrue(Updates.isNewer("2.0", "1.9.9"))
        assertFalse(Updates.isNewer("1.2.0", "1.2.0"))
        assertFalse(Updates.isNewer("1.1.9", "1.2.0"))
    }

    @Test
    fun readsGitHubsAnswer() {
        // Shaped like GitHub's real answer: the release's page first, the author's page later, assets before the body.
        val json = """
            {"url":"https://api.github.com/x","html_url":"https://github.com/rajendra7169/blazbuds/releases/tag/v1.3.0",
             "id":1,"author":{"login":"rajendra7169","html_url":"https://github.com/rajendra7169"},
             "tag_name":"v1.3.0","name":"Blazbuds 1.3.0","assets":[
               {"name":"blazbuds-1.3.0.apk","uploader":{"login":"rajendra7169"},"browser_download_url":"https://github.com/d/blazbuds-1.3.0.apk"},
               {"name":"blazbuds_1.3.0_amd64.deb","browser_download_url":"https://github.com/d/blazbuds_1.3.0_amd64.deb"}],
             "body":"## New\n- Find rings the earbuds \"themselves\"\n- café"}
        """.trimIndent()
        val release = Updates.parse(json)!!
        assertEquals("1.3.0", release.version)
        assertEquals("https://github.com/rajendra7169/blazbuds/releases/tag/v1.3.0", release.page)
        assertEquals("## New\n- Find rings the earbuds \"themselves\"\n- café", release.notes)
        assertEquals("https://github.com/d/blazbuds-1.3.0.apk", release.fileFor(SystemLook.Android))
        assertEquals("https://github.com/d/blazbuds_1.3.0_amd64.deb", release.fileFor(SystemLook.Linux))
        assertEquals(null, release.fileFor(SystemLook.Windows))
    }

    @Test
    fun readsLongReleaseNotesWithoutOverflowing() {
        // Real notes run to several kilobytes; the old pattern overflowed the stack past about 1,700 characters.
        val notes = "- A fix with \\\"quotes\\\" and a line\\n".repeat(4_000)
        val json = "{\"html_url\":\"https://x/r\",\"tag_name\":\"v9.0.0\",\"body\":\"$notes\"}"
        val release = Updates.parse(json)!!
        assertEquals("9.0.0", release.version)
        assertTrue(release.notes.length > 100_000)
        assertTrue(release.notes.startsWith("- A fix with \"quotes\" and a line\n"))
    }
}
