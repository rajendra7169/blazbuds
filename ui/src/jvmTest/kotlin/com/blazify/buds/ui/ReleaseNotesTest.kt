package com.blazify.buds.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseNotesTest {
    private val notes = """
<div align="center">

### [![Download Blazbuds for Android](https://img.shields.io/badge/Download_Blazbuds_1.3.0-3DD5C3?style=for-the-badge)](https://github.com/x/y.apk)

**One app for your wireless earbuds, whatever brand they are.** That button gives you the Android app.
On Linux, take [blazbuds_1.3.0_amd64.deb](https://github.com/x/y.deb)

</div>

---

## Highlights
- **Tuned for your earbuds:** one tap applies a correction
- **Automations:** the earbuds change by themselves

## New features
- **Left and right balance** on earbuds that take each ear's sound

<details>
<summary>Checksums (SHA-256)</summary>

```
222f9bc7  blazbuds-1.3.0.apk
```
</details>
""".trimIndent()

    @Test
    fun theDownloadBlockAndChecksumsAreLeftOut() {
        val blocks = ReleaseNotes.read(notes)
        val text = blocks.joinToString("\n") { it.toString() }
        assertFalse("div" in text || "shields.io" in text || "Checksums" in text || "222f9bc7" in text || "---" in text, text)
        assertEquals(NoteBlock.Heading("Highlights"), blocks.first())
        assertEquals(5, blocks.size)
    }

    @Test
    fun boldStaysBoldAndLinksKeepTheirWords() {
        val styled = ReleaseNotes.styled("**Tuned for your earbuds:** see [the page](https://x.y)")
        assertEquals("Tuned for your earbuds: see the page", styled.text)
        assertEquals(1, styled.spanStyles.size)
    }

    @Test
    fun shortKeepsTheHeadingsOfWhatItShows() {
        val short = ReleaseNotes.short(notes, limit = 2)
        assertEquals(listOf(NoteBlock.Heading("Highlights")), short.filterIsInstance<NoteBlock.Heading>())
        assertEquals(2, short.count { it is NoteBlock.Bullet })
        assertTrue(short.none { it is NoteBlock.Line })
    }
}
