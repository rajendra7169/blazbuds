package com.blazify.buds.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette

/** Settings › Home Assistant: the broker and login, the Connect switch, and how it's going. */
@Composable
fun HomeAssistantPanel(settings: HaSettings, bridge: HomeAssistant) {
    val status by bridge.status.collectAsState()
    val palette = LocalPalette.current
    Panel {
        SectionTitle("Home Assistant")
        SwitchRow(
            BudsIcons.Home, "Connect", settings.on, { settings.connect(it) },
            detail = "Battery, listening mode and scenes in Home Assistant, over MQTT",
            enabled = settings.on || settings.canConnect,
            color = Hue.Blue,
        )
        Spacer(Modifier.height(8.dp))
        val editable = !settings.on
        Field(settings.host, settings::host, "Broker", "192.168.1.10 or homeassistant.local", editable)
        Spacer(Modifier.height(8.dp))
        Field(settings.port, settings::port, "Port", "1883", editable, keyboard = KeyboardType.Number)
        Spacer(Modifier.height(8.dp))
        Field(settings.username, settings::username, "Username", "Optional", editable)
        Spacer(Modifier.height(8.dp))
        Field(settings.password, settings::password, "Password", "Optional", editable, secret = true)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dot = when {
                status.good -> Hue.Green
                status.trouble -> Hue.Amber
                else -> palette.muted
            }
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(status.text, style = MaterialTheme.typography.bodyMedium, color = palette.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Hint(
            if (settings.on) "Switch off Connect to change these."
            else "Needs the MQTT integration in Home Assistant. The password stays on this computer.",
            Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Field(
    value: String,
    change: (String) -> Unit,
    label: String,
    hint: String,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    val palette = LocalPalette.current
    OutlinedTextField(
        value, change,
        label = { Text(label) },
        placeholder = { Text(hint, color = palette.muted) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.line,
            disabledBorderColor = palette.line,
            focusedLabelColor = palette.accent,
            unfocusedLabelColor = palette.muted,
            disabledLabelColor = palette.muted,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            disabledTextColor = palette.muted,
            cursorColor = palette.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
