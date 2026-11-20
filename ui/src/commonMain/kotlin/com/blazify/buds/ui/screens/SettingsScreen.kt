package com.blazify.buds.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.LocalLook
import com.blazify.buds.ui.LocalPrefs
import com.blazify.buds.ui.PROFILE_URL
import com.blazify.buds.ui.REPO_URL
import com.blazify.buds.ui.Updates
import com.blazify.buds.ui.VERSION
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.resources.Res
import com.blazify.buds.ui.resources.app_blazify
import com.blazify.buds.ui.resources.app_blazix
import com.blazify.buds.ui.resources.app_fitevo
import com.blazify.buds.ui.resources.app_nexterm
import com.blazify.buds.ui.resources.developer_photo
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.ThemeChoice
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun SettingsScreen(back: (() -> Unit)?, openChangelog: () -> Unit = {}) = PlaceFrame("Settings", back) {
    val prefs = LocalPrefs.current
    Panel {
        SectionTitle("Look")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeTile(ThemeChoice.System, "System", BudsIcons.Auto, prefs.theme == ThemeChoice.System, Modifier.weight(1f)) { prefs.theme(it) }
            ThemeTile(ThemeChoice.Light, "Light", BudsIcons.Sun, prefs.theme == ThemeChoice.Light, Modifier.weight(1f)) { prefs.theme(it) }
            ThemeTile(ThemeChoice.Dark, "Dark", BudsIcons.Moon, prefs.theme == ThemeChoice.Dark, Modifier.weight(1f)) { prefs.theme(it) }
        }
        Spacer(Modifier.height(12.dp))
        SwitchRow(BudsIcons.Moon, "Less motion", prefs.reduceMotion, { prefs.reduceMotion(it) }, detail = "Fades instead of slides, and the earbuds hold still", color = Hue.Indigo)
    }
    ConnectPanel()
    UpdatesPanel(openChangelog)
    DeveloperPanel()
    OtherAppsPanel()
    Panel {
        SectionTitle("About Blazbuds")
        Text("Version $VERSION", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text)
        Spacer(Modifier.height(6.dp))
        Hint("No ads and no account. The app talks to your earbuds, and goes online only to ask GitHub whether a newer version is out, which you can switch off above.")
        Spacer(Modifier.height(6.dp))
        Hint("Free software under the GNU GPL 3.0.")
    }
}

/** A small picture of the app in that look, its icon and its name; the chosen one is ringed. */
@Composable
private fun ThemeTile(choice: ThemeChoice, label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, pick: (ThemeChoice) -> Unit) {
    val palette = LocalPalette.current
    val ring by animateColorAsState(if (selected) palette.accent else palette.line, label = "ring")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).border(if (selected) 2.dp else 1.dp, ring, shape).press(role = Role.RadioButton) { pick(choice) }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A tiny screen: light, dark, or split down the middle for "like the system".
        val light = Color(0xFFF5F7F8)
        val dark = Color(0xFF111518)
        Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).background(if (choice == ThemeChoice.Dark) dark else light)) {
            if (choice == ThemeChoice.System) Box(Modifier.fillMaxWidth(0.5f).fillMaxHeight().align(Alignment.CenterEnd).background(dark))
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(28.dp).height(6.dp).background(palette.accent, CircleShape))
                Box(Modifier.width(40.dp).height(5.dp).background(Color(0x55888888), CircleShape))
                Box(Modifier.width(22.dp).height(5.dp).background(Color(0x55888888), CircleShape))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) palette.accent else palette.muted, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Whether to look for new versions, what was found, and the way to it; plus what changed in each version. */
@Composable
private fun UpdatesPanel(openChangelog: () -> Unit) {
    val prefs = LocalPrefs.current
    val palette = LocalPalette.current
    val look = LocalLook.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Panel {
        SectionTitle("Updates")
        SwitchRow(BudsIcons.Refresh, "Look for new versions", prefs.checkUpdates, { prefs.checkUpdates(it) }, detail = "Asks GitHub now and then; nothing about you is sent", color = Hue.Green)
        val update = prefs.knownUpdate
        if (update != null) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.accent.copy(alpha = 0.12f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Blazbuds ${update.version} is out", style = MaterialTheme.typography.titleSmall, color = palette.text)
                val notes = update.notes.lines().filter { it.isNotBlank() }.take(6).joinToString("\n") { it.trim().trimStart('#').trim() }
                if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                val file = update.fileFor(look)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file != null) Choice("Download", true, icon = BudsIcons.Download) { uri.openUri(file) }
                    Choice("Release page", false, icon = BudsIcons.OpenLink) { uri.openUri(update.page) }
                }
                if (file == null) Hint("Get it the same way you got this version.")
            }
        } else if (prefs.checkUpdates) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result ?: "You have version $VERSION.", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.weight(1f))
                Choice(if (checking) "Checking…" else "Check now", false, icon = BudsIcons.Refresh) {
                    if (checking) return@Choice
                    checking = true
                    scope.launch {
                        val found = Updates.latest()
                        checking = false
                        result = when {
                            found == null -> "Couldn't reach GitHub. Try again later."
                            Updates.isNewer(found.version) -> { prefs.foundUpdate(found); null }
                            else -> "You have the newest version, $VERSION."
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        SettingsRow(BudsIcons.Spark, "What's new", "What changed in each version", Hue.Orange, openChangelog)
    }
}

/** Who makes Blazbuds, in a few words: no links, no location. */
@Composable
private fun DeveloperPanel() {
    val palette = LocalPalette.current
    Panel {
        SectionTitle("Developer")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painterResource(Res.drawable.developer_photo), "Rajendra Pandey",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, palette.accent, CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text("Rajendra Pandey", style = MaterialTheme.typography.titleMedium, color = palette.text, fontWeight = FontWeight.SemiBold)
                Text("Builds Blazbuds", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "I build Blazbuds on my own, in the open. It began because I wanted one app for all my earbuds, whatever brand they are, " +
                "that looks the way I think one should. What goes into each version is mostly what people tell me is broken or missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
        )
        Spacer(Modifier.height(12.dp))
        val uri = LocalUriHandler.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("GitHub", false, icon = BudsIcons.GitHub) { uri.openUri(PROFILE_URL) }
            Choice("Website", false, icon = BudsIcons.Globe) { uri.openUri(WEBSITE_URL) }
        }
    }
}

private const val WEBSITE_URL = "https://rajendrapandey.info.np"

private data class OtherApp(val name: String, val about: String, val logo: org.jetbrains.compose.resources.DrawableResource, val url: String)

/** The developer's other apps; public ones open their code, private ones the developer's page. */
private val OTHER_APPS = listOf(
    OtherApp("Blazify", "A free music player and YouTube Music client", Res.drawable.app_blazify, "https://github.com/rajendra7169/blazify"),
    OtherApp("Blazix", "A video player with offline viewing and no ads", Res.drawable.app_blazix, PROFILE_URL),
    OtherApp("Fitevo", "Fitness, food and workouts, kept on your phone", Res.drawable.app_fitevo, "https://github.com/rajendra7169/fitevo"),
    OtherApp("NexTerm", "A modern terminal for Windows", Res.drawable.app_nexterm, "https://github.com/rajendra7169/NexTerm"),
)

@Composable
private fun OtherAppsPanel() {
    val palette = LocalPalette.current
    val uri = LocalUriHandler.current
    Panel(padding = 8.dp) {
        SectionTitle("More apps by the developer", Modifier.padding(start = 8.dp, top = 8.dp))
        OTHER_APPS.forEach { app ->
            Row(
                Modifier.fillMaxWidth().press { uri.openUri(app.url) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Each app's own icon, on a soft tile so ones with clear edges sit the same as the square ones.
                Image(
                    painterResource(app.logo), app.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.raised).border(1.dp, palette.line, RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                    Text(app.about, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(BudsIcons.OpenLink, null, tint = palette.muted, modifier = Modifier.size(18.dp))
            }
        }
        // A star helps others find Blazbuds; the popup asks once a release, and here it always is.
        Row(
            Modifier.fillMaxWidth().press { uri.openUri(REPO_URL) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).background(palette.accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(BudsIcons.GitHub, null, tint = palette.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Star Blazbuds on GitHub", style = MaterialTheme.typography.bodyLarge, color = palette.text)
                Text("It helps others find it, and it's free", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            Icon(BudsIcons.OpenLink, null, tint = palette.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** A row that opens another page: an icon in a coloured circle, a title and a line under it. */
@Composable
fun SettingsRow(icon: ImageVector, title: String, detail: String, hue: Color, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().press(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(hue, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        }
        Icon(BudsIcons.Forward, null, tint = palette.muted, modifier = Modifier.size(18.dp))
    }
}

/** The card that greets the earbuds as they connect: on or off, and on a phone the permission it needs. */
@Composable
private fun ConnectPanel() {
    val prefs = LocalPrefs.current
    val over = com.blazify.buds.ui.LocalOverApps.current
    // Looked at again every second while here, so coming back from the system's page shows the new answer.
    var allowed by remember { mutableStateOf(over?.allowed() ?: true) }
    androidx.compose.runtime.LaunchedEffect(over) {
        while (over != null) {
            allowed = over.allowed()
            kotlinx.coroutines.delay(1_000)
        }
    }
    Panel {
        SectionTitle("When they connect")
        SwitchRow(
            BudsIcons.Earbuds, "Show the case and battery", prefs.connectPopup, { prefs.connectPopup(it) },
            detail = "A card slides up as you open the case", color = Hue.Teal,
        )
        if (prefs.connectPopup && over != null && !allowed) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint("Your phone asks first: allow Blazbuds to show over other apps.", Modifier.weight(1f))
                Choice("Allow", true) { over.ask() }
            }
        }
    }
}

