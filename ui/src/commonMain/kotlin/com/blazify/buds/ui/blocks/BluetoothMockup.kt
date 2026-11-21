package com.blazify.buds.ui.blocks

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blazify.buds.ui.SystemLook
import com.blazify.buds.ui.icons.BudsIcons

/**
 * A small drawing of this device's own Bluetooth settings, in that system's own look, with the earbuds in its list and
 * a pulsing finger mark on what to tap. [pairing] shows the screen for pairing something new; otherwise the list of
 * devices paired before, where one tap reconnects.
 */
@Composable
fun BluetoothMockup(look: SystemLook, name: String, pairing: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "tap")
    val beat by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "beat")
    // The finger mark reads the pulse only as it is drawn, so the drawing of the settings isn't composed again each frame.
    val pulseNow = { beat }
    Box(modifier.fillMaxWidth().semantics { contentDescription = "Bluetooth settings: tap $name" }, contentAlignment = Alignment.Center) {
        when (look) {
            SystemLook.Android -> Phone(round = 30) { AndroidScreen(name, pairing, pulseNow) }
            SystemLook.Ios -> Phone(round = 38) { IosScreen(name, pairing, pulseNow) }
            else -> Window(look) { DesktopScreen(look, name, pairing, pulseNow) }
        }
    }
}

@Composable
private fun Phone(round: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.width(230.dp).clip(RoundedCornerShape(round.dp)).background(Color(0xFF1B1D21)).padding(7.dp)
            .clip(RoundedCornerShape((round - 7).dp)).background(Color.White),
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", style = small(10, FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 52.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B1D21)))
            Spacer(Modifier.weight(1f))
            Icon(BudsIcons.Bluetooth, null, Modifier.size(10.dp), tint = Color(0xFF222222))
        }
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun Window(look: SystemLook, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 380.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFD0D4D9), RoundedCornerShape(10.dp)).background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFEDEFF2)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (look) {
                    SystemLook.Windows -> "Settings"
                    SystemLook.Mac -> "System Settings"
                    else -> "Settings"
                },
                style = small(11, FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            repeat(3) { Box(Modifier.padding(start = 6.dp).size(9.dp).clip(CircleShape).background(Color(0xFFC5C9CE))) }
        }
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AndroidScreen(name: String, pairing: Boolean, beat: () -> Float) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(BudsIcons.Back, null, Modifier.size(14.dp), tint = Color(0xFF222222))
            Text(if (pairing) "Pair new device" else "Connected devices", style = small(14, FontWeight.Medium), modifier = Modifier.padding(start = 8.dp))
        }
        if (pairing) {
            Section("Available devices", Color(0xFF1A73E8))
            Item(BudsIcons.Earbuds, name, null, tapped = true, beat = beat)
            Item(BudsIcons.Phone, "Galaxy A15", null)
            Item(BudsIcons.Laptop, "Living room TV", null)
        } else {
            Item(BudsIcons.Plus, "Pair new device", null)
            Section("Previously connected devices", Color(0xFF1A73E8))
            Item(BudsIcons.Earbuds, name, "Tap to connect", tapped = true, beat = beat)
            Item(BudsIcons.Laptop, "Car stereo", null)
        }
    }
}

@Composable
private fun IosScreen(name: String, pairing: Boolean, beat: () -> Float) {
    Column(Modifier.background(Color(0xFFF2F2F7)).padding(horizontal = 10.dp)) {
        Text("‹ Settings", style = small(11, FontWeight.Normal, Color(0xFF0A84FF)), modifier = Modifier.padding(top = 6.dp))
        Text("Bluetooth", style = small(18, FontWeight.Bold), modifier = Modifier.padding(vertical = 4.dp))
        Group {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bluetooth", style = small(12, FontWeight.Normal), modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 30.dp, height = 18.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF34C759)), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.padding(2.dp).size(14.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Text(if (pairing) "OTHER DEVICES" else "MY DEVICES", style = small(9, FontWeight.Normal, Color(0xFF8E8E93)), modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp))
        Group {
            IosItem(name, if (pairing) "" else "Not Connected", tapped = true, beat = beat)
            IosItem(if (pairing) "Living room TV" else "Car stereo", if (pairing) "" else "Not Connected")
        }
    }
}

@Composable
private fun DesktopScreen(look: SystemLook, name: String, pairing: Boolean, beat: () -> Float) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (look == SystemLook.Windows) "Bluetooth & devices" else "Bluetooth", style = small(15, FontWeight.Bold))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF4F5F7)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(BudsIcons.Bluetooth, null, Modifier.size(14.dp), tint = Color(0xFF1A73E8))
            Text("Bluetooth  On", style = small(12, FontWeight.Normal), modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (pairing && look == SystemLook.Windows) Text("Add device", style = small(11, FontWeight.Medium, Color(0xFF1A73E8)))
        }
        Text("Devices", style = small(11, FontWeight.Medium, Color(0xFF5F6368)), modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF4F5F7)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(BudsIcons.Earbuds, null, Modifier.size(16.dp), tint = Color(0xFF333333))
            Text(name, style = small(12, FontWeight.Medium), modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(contentAlignment = Alignment.Center) {
                Finger(beat)
                Text(
                    if (pairing) "Pair" else "Connect",
                    style = small(11, FontWeight.Medium, Color.White),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A73E8)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(text: String, color: Color) {
    Text(text, style = small(10, FontWeight.Medium, color), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Item(icon: ImageVector, title: String, detail: String?, tapped: Boolean = false, beat: () -> Float = { 0f }) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (tapped) Color(0xFFE8F0FE) else Color.Transparent).padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color(0xFF444746))
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = small(12, FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = small(10, FontWeight.Normal, Color(0xFF5F6368)))
        }
        if (tapped) Finger(beat)
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White), content = content)
}

@Composable
private fun IosItem(name: String, state: String, tapped: Boolean = false, beat: () -> Float = { 0f }) {
    Row(
        Modifier.fillMaxWidth().background(if (tapped) Color(0xFFE5F0FF) else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = small(12, FontWeight.Normal), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state.isNotEmpty()) Text(state, style = small(10, FontWeight.Normal, Color(0xFF8E8E93)))
        if (tapped) Finger(beat)
    }
}

/** A finger's touch: a dot with a ring that spreads and fades. */
@Composable
private fun Finger(beat: () -> Float) {
    Box(
        Modifier.size(26.dp).drawBehind {
            val now = beat()
            val c = Color(0xFF0F9488)
            drawCircle(c.copy(alpha = 0.35f * (1f - now)), radius = size.minDimension / 2 * (0.5f + 0.5f * now))
            drawCircle(c.copy(alpha = 0.8f), radius = size.minDimension * 0.2f)
        },
    )
}

private fun small(size: Int, weight: FontWeight, color: Color = Color(0xFF1F1F1F)) =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size + 4).sp)
