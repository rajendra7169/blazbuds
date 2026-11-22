package com.blazify.buds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.theme.LocalPalette

/** One piece of a release's notes, as the app shows it. */
sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
    data class Line(val text: String) : NoteBlock
}

/**
 * A release's notes, read for the app rather than for a web page.
 *
 * The notes are written for GitHub: a centred block with a download button, links, a hidden list of checksums. Shown
 * as they are, they read as code. What is kept is what a person wants from an update notice: the headings, the points
 * under them with their bold words, and plain sentences. Links keep their words and lose their address.
 */
object ReleaseNotes {
    fun read(notes: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        var skipping: String? = null
        for (raw in notes.lines()) {
            val line = raw.trim()
            // Whole HTML blocks (the download button, the checksums) are left out, however many lines they take.
            skipping?.let { tag ->
                if (line.contains("</$tag>", ignoreCase = true)) skipping = null
                continue
            }
            val opening = Regex("^<(div|details|p|table)\\b", RegexOption.IGNORE_CASE).find(line)
            if (opening != null) {
                val tag = opening.groupValues[1].lowercase()
                if (!line.contains("</$tag>", ignoreCase = true)) skipping = tag
                continue
            }
            if (line.isEmpty() || line.startsWith("<") || line.matches(Regex("^[-*_]{3,}$")) || line.startsWith("```")) continue
            when {
                line.startsWith("#") -> blocks += NoteBlock.Heading(plain(line.trimStart('#').trim()))
                line.startsWith("- ") || line.startsWith("* ") -> blocks += NoteBlock.Bullet(line.drop(2).trim())
                else -> blocks += NoteBlock.Line(line)
            }
        }
        // A heading with nothing under it says nothing.
        return blocks.filterIndexed { i, b -> b !is NoteBlock.Heading || blocks.getOrNull(i + 1).let { it != null && it !is NoteBlock.Heading } }
    }

    /** The first [limit] points and lines, with the headings that introduce them. */
    fun short(notes: String, limit: Int = 8): List<NoteBlock> {
        val out = mutableListOf<NoteBlock>()
        var shown = 0
        for (b in read(notes)) {
            if (b is NoteBlock.Heading) {
                if (shown >= limit) break
                out += b
            } else {
                if (shown >= limit) break
                out += b
                shown++
            }
        }
        return out.filterIndexed { i, b -> b !is NoteBlock.Heading || out.getOrNull(i + 1).let { it != null && it !is NoteBlock.Heading } }
    }

    /** Markdown words as text: links keep their words, `code` and images go plain, ** stays as a marker for bold. */
    private fun plain(text: String): String = text
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace("`", "")

    /** The text with its **bold** parts in bold. */
    fun styled(text: String): AnnotatedString = buildAnnotatedString {
        val parts = plain(text).split("**")
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
    }
}

/** A release's notes, short and tidy, for the update notice. */
@Composable
fun ReleaseNotesView(notes: String, limit: Int = 8, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val blocks = remember(notes, limit) { ReleaseNotes.short(notes, limit) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(
                    block.text, style = MaterialTheme.typography.labelLarge, color = palette.accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is NoteBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = palette.muted, modifier = Modifier.padding(end = 6.dp))
                    Text(ReleaseNotes.styled(block.text), style = MaterialTheme.typography.bodySmall, color = palette.text)
                }
                is NoteBlock.Line -> Text(ReleaseNotes.styled(block.text), style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
    }
}
