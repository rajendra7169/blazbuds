package com.blazify.buds.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.Gallery
import com.blazify.buds.ui.QrCode
import com.blazify.buds.ui.SavedSound
import com.blazify.buds.ui.SharedSound
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.EqCurve
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.LinkRow
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/** A QR code drawn dark on white, whatever the theme, with the quiet margin scanners need. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val code = remember(text) { QrCode.encode(text) } ?: return
    Canvas(
        modifier.aspectRatio(1f).background(Color.White, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "QR code" },
    ) {
        val cells = code.size + 8
        val cell = size.width / cells
        for (y in 0 until code.size) for (x in 0 until code.size) {
            if (code.dark(x, y)) drawRect(Color(0xFF101418), Offset((x + 4) * cell, (y + 4) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** One of your sounds to pass on: its QR code for a phone beside you, the code itself, and the share sheet. */
@Composable
fun ShareSoundDialog(sound: SavedSound, share: (String) -> Unit, close: () -> Unit) {
    val palette = LocalPalette.current
    val code = sound.code()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        modifier = Modifier.offset(x = com.blazify.buds.ui.blocks.LocalPopupShift.current),
        containerColor = palette.card,
        title = { Text("Share ${sound.name}", color = palette.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QrImage(code, Modifier.widthIn(max = 240.dp).fillMaxWidth())
                Text(
                    "Scan it with a phone camera, then paste the text in Sound › Custom › Add a code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                )
                Text(code, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = palette.text, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            Choice("Share", true, icon = BudsIcons.Share) {
                share("My Blazbuds sound \"${sound.name}\". In Blazbuds, open Sound › Custom › Add a code and paste:\n$code")
            }
        },
        dismissButton = { Choice("Close", false, onClick = close) },
    )
}

/** Where the gallery is: still loading, out of reach, or read. */
sealed interface GalleryState {
    data object Loading : GalleryState
    data object Failed : GalleryState
    data class Ready(val sounds: List<SharedSound>) : GalleryState
}

/** Sounds by others, read from Blazbuds' repository only now that it is opened. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(bands: List<Int>, close: () -> Unit, pick: (SharedSound) -> Unit) {
    val palette = LocalPalette.current
    var tries by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<GalleryState>(GalleryState.Loading) }
    LaunchedEffect(tries) {
        state = GalleryState.Loading
        state = Gallery.load()?.let { GalleryState.Ready(it) } ?: GalleryState.Failed
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = close,
        modifier = Modifier.offset(x = com.blazify.buds.ui.blocks.LocalPopupShift.current),
        containerColor = palette.card,
        contentColor = palette.text,
    ) {
        GalleryContent(state, bands, retry = { tries++ }, pick = pick)
    }
}

@Composable
fun GalleryContent(state: GalleryState, bands: List<Int>, retry: () -> Unit, pick: (SharedSound) -> Unit) {
    val palette = LocalPalette.current
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Sounds by others", style = MaterialTheme.typography.titleLarge, color = palette.text)
        Hint("Shared by people who use Blazbuds. Only sounds made for your earbuds' bands are shown. Tap one to add it to your sounds.")
        when (state) {
            GalleryState.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = palette.accent)
            }
            GalleryState.Failed -> {
                Hint("The gallery can't be reached right now. Check the connection and try again.")
                Choice("Try again", true, onClick = retry)
            }
            is GalleryState.Ready -> {
                val fitting = Gallery.fitting(state.sounds, bands)
                if (fitting.isEmpty()) Hint("No shared sounds fit these earbuds' bands yet. Yours could be the first.")
                fitting.forEach { sound -> GalleryRow(sound) { pick(sound) } }
            }
        }
        Spacer(Modifier.height(4.dp))
        LinkRow(BudsIcons.Send, "Share your sound", "Send its code, and it may join the gallery", color = com.blazify.buds.ui.blocks.Hue.Teal) {
            uri.openUri("${com.blazify.buds.ui.REPO_URL}/issues/new?template=${Gallery.SHARE_FORM}")
        }
    }
}

@Composable
private fun GalleryRow(sound: SharedSound, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(palette.raised, RoundedCornerShape(16.dp)).press(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).background(palette.card, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            EqCurve(sound.gains, Modifier.height(34.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(sound.name, style = MaterialTheme.typography.titleMedium, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sound.about.isNotEmpty()) Text(sound.about, style = MaterialTheme.typography.bodyMedium, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sound.by.isNotEmpty()) Text("By ${sound.by}", style = MaterialTheme.typography.labelSmall, color = palette.accent)
        }
        androidx.compose.material3.Icon(BudsIcons.Plus, "Add", tint = palette.accent, modifier = Modifier.size(22.dp))
    }
}
