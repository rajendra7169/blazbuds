package com.blazify.buds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.theme.LocalPalette

/** One version's changes, kept in the app so they read the same offline. */
private data class Version(val number: String, val date: String, val groups: List<Pair<String, List<String>>>)

private val CHANGES = listOf(
    Version(
        "1.2.0", "November 2026",
        listOf(
            "Highlights" to listOf(
                "One app for earbuds from Soundcore, Honor and Huawei, Ultima and other JieLi pairs, Samsung, Sony, Nothing and CMF, OnePlus, OPPO and realme",
                "EarPrint: a hearing test and a listening test make a sound shaped to your ears",
                "Android, Linux and Windows, with a tray panel on computers",
            ),
            "Sound" to listOf(
                "Presets with pictures, and your own sounds made with +, shared by a short code",
                "Custom equalizers on the earbuds that have one: Soundcore, Ultima, Samsung Buds3 and newer, Sony, Nothing's Advanced EQ, OnePlus and OPPO",
                "Noise cancelling strengths, ambient level, wind noise, voice detection and speak-to-chat where the earbuds offer them",
            ),
            "Everyday" to listOf(
                "Battery for each earbud and the case, a low-battery alert from 10 to 40%, and a week of battery history",
                "Find my earbuds rings the earbuds themselves when they can, else plays a sound through them",
                "Touch controls drawn on an ear, the ear tip fit test, dual-connect and the devices the earbuds remember",
                "Updates from GitHub, this page, and Report a problem with a copy of everything needed",
            ),
        ),
    ),
)

@Composable
fun ChangelogScreen(back: () -> Unit) = PlaceFrame("What's new", back) {
    val palette = LocalPalette.current
    CHANGES.forEach { version ->
        Panel {
            Row {
                Text(version.number, style = MaterialTheme.typography.titleLarge, color = palette.text, modifier = Modifier.weight(1f))
                Text(version.date, style = MaterialTheme.typography.labelMedium, color = palette.muted)
            }
            version.groups.forEach { (title, lines) ->
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.accent)
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { line ->
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(end = 8.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = palette.text)
                        }
                    }
                }
            }
        }
    }
}
