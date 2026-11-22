package com.blazify.buds.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.Logbook
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.ui.LocalLook
import com.blazify.buds.ui.REPO_URL
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

private val QUESTIONS = listOf(
    "My earbuds won't connect" to "Take them out of the case so they wake up, and check they are connected in your Bluetooth settings. Then tap Try again on Home.",
    "It says another app is using them" to "Only one app on a phone can control earbuds at a time. Close the brand's own app (swipe it away from recent apps), then tap Try again.",
    "A change from another app shows late" to "The app asks your earbuds for their settings every few seconds while it is open, so a change made elsewhere shows within about three seconds.",
    "Why is battery in steps of 20%?" to "Some earbuds, the Liberty 4 NC among them, report their battery in fifths, so the app shows exactly what they say rather than a guess.",
    "Sound changes don't work" to "Many earbuds accept sound changes only while both of them are out of the case.",
    "Switching two devices or best quality stops the music" to "The earbuds restart to make that switch, so the music stops for a few seconds. LDAC, the best quality, works with one device at a time.",
    "My earbuds aren't listed" to "They still get battery and the basics. Ask for them with Request your earbuds below, and they can be added.",
)

/** What the wish list in a request offers. */
private val WISHES = listOf("Battery", "Noise cancelling", "Equalizer", "Touch controls", "Find my earbuds", "Fit test", "Something else")

/** Help: answers to what goes wrong most, a way to report anything else, and a way to ask for more earbuds. */
@Composable
fun SupportScreen(state: BudsState?, version: String, back: () -> Unit, check: (suspend () -> String)? = null) {
    val palette = LocalPalette.current
    var open by remember { mutableIntStateOf(-1) }
    // The last Bluetooth check, which Copy everything carries too.
    var checked by remember { mutableStateOf<String?>(null) }
    PlaceFrame("Help", back) {
        Panel(padding = 8.dp) {
            QUESTIONS.forEachIndexed { i, (question, answer) ->
                Column(Modifier.fillMaxWidth().press { open = if (open == i) -1 else i }.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(question, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
                        Icon(if (open == i) BudsIcons.Close else BudsIcons.Plus, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                    }
                    AnimatedVisibility(open == i) {
                        Text(answer, style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        if (check != null) CheckPanel(check, checked) { checked = it }
        ReportPanel(state, version, checked)
        RequestPanel(state, version)
    }
}

/** The details any report needs: the app, the system, the earbuds and what they said lately. Nothing personal. */
private fun details(state: BudsState?, version: String, look: String, withLog: Boolean, check: String? = null): String = buildString {
    appendLine("Blazbuds $version on $look")
    appendLine("Earbuds: ${state?.name ?: "none"} (${state?.model?.ifEmpty { "model unknown" } ?: "none"}) · firmware ${state?.firmware ?: "?"}")
    appendLine("Link: ${state?.link ?: "none"} · ready: ${state?.isReady}")
    appendLine("Battery: L ${state?.left?.percent ?: "-"} · R ${state?.right?.percent ?: "-"} · case ${state?.case?.percent ?: "-"}")
    appendLine("Mode: ${state?.sound?.mode ?: "-"} · two devices: ${state?.multipoint?.enabled ?: "-"} · switches: ${state?.toggles?.keys?.joinToString() ?: "-"}")
    if (withLog) {
        val log = Logbook.all()
        appendLine()
        appendLine("Log (${log.size} lines):")
        log.forEach { appendLine(it) }
        if (check != null) {
            appendLine()
            appendLine("Bluetooth check:")
            appendLine(check)
        }
    }
}

/** Opens a new issue on GitHub with the title and text filled in. */
private fun issueUrl(title: String, body: String, label: String, form: String): String {
    fun enc(s: String) = buildString {
        s.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch) else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
    // Naming the form keeps GitHub on it; the text given replaces the form's own.
    return "$REPO_URL/issues/new?template=${enc(form)}&labels=${enc(label)}&title=${enc(title)}&body=${enc(body)}"
}

@Composable
private fun ReportPanel(state: BudsState?, version: String, check: String?) {
    val palette = LocalPalette.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val look = LocalLook.current.name
    // 0 nothing copied yet, 1 the summary, 2 everything.
    var copied by remember { mutableIntStateOf(0) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HueDot(BudsIcons.Bug, Hue.Red)
            Column(Modifier.weight(1f)) {
                Text("Report a problem", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("Something not working? Two steps.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Step("1", "Copy everything: the app, your system, the earbuds and what they said lately. Their messages can hold the names of devices they remember, so look it over before you post it; or copy just the summary.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(if (copied == 2) "Copied" else "Copy everything", copied == 2, icon = if (copied == 2) BudsIcons.Check else BudsIcons.Copy) {
                clipboard.setText(AnnotatedString(details(state, version, look, withLog = true, check = check)))
                copied = 2
            }
            Choice(if (copied == 1) "Copied" else "Just the summary", copied == 1, icon = if (copied == 1) BudsIcons.Check else BudsIcons.Copy) {
                clipboard.setText(AnnotatedString(details(state, version, look, withLog = false)))
                copied = 1
            }
        }
        Spacer(Modifier.height(12.dp))
        Step("2", "Open a report, say what happened, and paste what you copied under it.")
        Spacer(Modifier.height(8.dp))
        Choice("Report", false, icon = BudsIcons.Send) {
            val body = "**What happened**\n\n\n**What you expected**\n\n\n**Details** (paste what Blazbuds copied here)\n\n" +
                details(state, version, look, withLog = false)
            uri.openUri(issueUrl("Problem: ", body, "bug", "problem.md"))
        }
    }
}

/**
 * What the system reports about Bluetooth and the earbuds connected now, for when they don't show up or won't
 * connect: run it, then copy it into a report.
 */
@Composable
private fun CheckPanel(check: suspend () -> String, result: String?, done: (String) -> Unit) {
    val palette = LocalPalette.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HueDot(BudsIcons.Bluetooth, Hue.Blue)
            Column(Modifier.weight(1f)) {
                Text("Bluetooth check", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("What this device sees of your earbuds", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Earbuds missing or not connecting? Close the brand's own app, have them connected (or, the first time on an iPhone, in pairing mode), then run this. It takes up to half a minute.",
            style = MaterialTheme.typography.bodyMedium, color = palette.muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(if (running) "Checking…" else "Run the check", running, icon = BudsIcons.Bluetooth) {
                if (running) return@Choice
                running = true
                copied = false
                scope.launch {
                    done(runCatching { check() }.getOrElse { "The check stopped: ${it.message}" })
                    running = false
                }
            }
            if (result != null && !running) {
                Choice(if (copied) "Copied" else "Copy", copied, icon = if (copied) BudsIcons.Check else BudsIcons.Copy) {
                    clipboard.setText(AnnotatedString(result))
                    copied = true
                }
            }
        }
        if (result != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                result,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = palette.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.raised).padding(10.dp),
            )
            Spacer(Modifier.height(8.dp))
            Hint("Copy everything, under Report a problem, carries this too.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestPanel(state: BudsState?, version: String) {
    val palette = LocalPalette.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val look = LocalLook.current.name
    // The connected pair's name only when the app gives it just the basics: that is the pair worth asking about.
    val basicsOnly = state != null && state.sound == null && state.equalizer == null && state.controls == null && state.toggles.isEmpty()
    var name by remember { mutableStateOf(if (basicsOnly) state?.name.orEmpty() else "") }
    var brand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var wishes by remember { mutableStateOf(setOf<String>()) }
    var copied by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HueDot(BudsIcons.Earbuds, Hue.Teal)
            Column(Modifier.weight(1f)) {
                Text("Request your earbuds", style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("Ask for full control of a pair the app doesn't know yet", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Field(name, { name = it }, "Name in Bluetooth settings", "Like soundcore Liberty 5")
        Spacer(Modifier.height(8.dp))
        Field(brand, { brand = it }, "Brand and model", "Like Anker Soundcore Liberty 5 (A3957)")
        Spacer(Modifier.height(12.dp))
        Text("What should it do?", style = MaterialTheme.typography.labelLarge, color = palette.text)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WISHES.forEach { wish ->
                Choice(wish, wish in wishes) { wishes = if (wish in wishes) wishes - wish else wishes + wish }
            }
        }
        Spacer(Modifier.height(12.dp))
        Field(notes, { notes = it }, "Anything else", "The brand's own app, what works there, what you miss", lines = 3)
        Spacer(Modifier.height(12.dp))
        val ready = name.isNotBlank() || brand.isNotBlank()
        val text = buildString {
            appendLine("**Earbuds:** ${brand.ifBlank { "-" }}")
            appendLine("**Bluetooth name:** ${name.ifBlank { "-" }}")
            appendLine("**Wanted:** ${wishes.joinToString().ifEmpty { "-" }}")
            if (notes.isNotBlank()) appendLine("**Notes:** $notes")
            appendLine()
            appendLine("Blazbuds $version on $look; what it shows now: ${if (state?.name == name && name.isNotBlank()) "connected, link ${state.link}, model ${state.model.ifEmpty { "unknown" }}" else "not connected"}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Send request", ready, icon = BudsIcons.Send) {
                if (ready) uri.openUri(issueUrl("Earbuds request: ${brand.ifBlank { name }}", text, "device request", "earbuds-request.md"))
            }
            Choice(if (copied) "Copied" else "Copy instead", false, icon = if (copied) BudsIcons.Check else BudsIcons.Copy) {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Hint("Send request opens a page on GitHub with all of this filled in. Requests with the most votes come first.")
    }
}

@Composable
private fun Step(number: String, text: String) {
    val palette = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, style = MaterialTheme.typography.labelLarge, color = palette.accent)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, hint: String, lines: Int = 1) {
    val palette = LocalPalette.current
    OutlinedTextField(
        value, change,
        label = { Text(label) },
        placeholder = { Text(hint, color = palette.muted) },
        singleLine = lines == 1,
        minLines = lines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.line,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.muted,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            cursorColor = palette.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HueDot(icon: androidx.compose.ui.graphics.vector.ImageVector, hue: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(40.dp).background(hue, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
    }
}
