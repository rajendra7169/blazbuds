package com.blazify.buds.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.LocalPrefs
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.theme.ThemeChoice

@Composable
fun SettingsScreen(back: (() -> Unit)?) = PlaceFrame("Settings", back) {
    val prefs = LocalPrefs.current
    val palette = LocalPalette.current
    Panel {
        SectionTitle("Look")
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Like the system", prefs.theme == ThemeChoice.System) { prefs.theme(ThemeChoice.System) }
            Choice("Light", prefs.theme == ThemeChoice.Light) { prefs.theme(ThemeChoice.Light) }
            Choice("Dark", prefs.theme == ThemeChoice.Dark) { prefs.theme(ThemeChoice.Dark) }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(BudsIcons.Moon, "Less motion", prefs.reduceMotion, { prefs.reduceMotion(it) }, detail = "Fades instead of slides, and the earbuds hold still", color = com.blazify.buds.ui.blocks.Hue.Indigo)
    }
    Panel {
        SectionTitle("About Blazbuds")
        Text("Version ${com.blazify.buds.ui.VERSION}", style = MaterialTheme.typography.bodyMedium, color = palette.text)
        Spacer(Modifier.height(6.dp))
        Hint("No ads, no account, nothing sent anywhere: the app talks only to your earbuds.")
        Spacer(Modifier.height(6.dp))
        Hint("Free software under the GNU GPL 3.0. The Soundcore protocol is understood thanks to the OpenSCQ30 and Gadgetbridge projects. Text is set in Figtree (SIL Open Font License).")
    }
}
