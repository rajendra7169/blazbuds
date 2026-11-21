package com.blazify.buds.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Scene
import com.blazify.buds.ui.ALL_DAYS
import com.blazify.buds.ui.Action
import com.blazify.buds.ui.DAY_NAMES
import com.blazify.buds.ui.Rule
import com.blazify.buds.ui.RuleBook
import com.blazify.buds.ui.Trigger
import com.blazify.buds.ui.WEEKDAYS
import com.blazify.buds.ui.WEEKEND
import com.blazify.buds.ui.blocks.Choice
import com.blazify.buds.ui.blocks.Hint
import com.blazify.buds.ui.blocks.Hue
import com.blazify.buds.ui.blocks.Panel
import com.blazify.buds.ui.blocks.SectionTitle
import com.blazify.buds.ui.blocks.SwitchRow
import com.blazify.buds.ui.blocks.press
import com.blazify.buds.ui.clock
import com.blazify.buds.ui.icons.BudsIcons
import com.blazify.buds.ui.theme.LocalPalette
import com.blazify.buds.ui.words

/**
 * Automations: rules that change the earbuds by themselves, as they connect, at a time of day, or as the battery runs
 * low. Every rule is in plain view with its own switch, and one switch at the top holds them all.
 */
@Composable
fun AutomationsScreen(book: RuleBook, now: () -> Long, changed: () -> Unit = {}, back: () -> Unit) {
    var editing by remember { mutableStateOf<Rule?>(null) }
    val open = editing
    if (open != null) {
        RuleEditor(
            open,
            isNew = book.rules.none { it.id == open.id },
            save = {
                book.save(it)
                changed()
                editing = null
            },
            delete = {
                book.delete(open.id)
                changed()
                editing = null
            },
            back = { editing = null },
        )
        return
    }
    PlaceFrame("Automations", back) {
        Panel {
            SwitchRow(
                BudsIcons.Auto, "Run automations", !book.paused, { book.pause(!it); changed() },
                detail = if (book.paused) "Held: none of them runs" else "They change your earbuds by themselves", color = Hue.Indigo,
            )
        }
        if (book.rules.isEmpty()) {
            Panel {
                SectionTitle("Ideas to start with")
                Spacer(Modifier.height(4.dp))
                Hint("Tap one to add it. You can change or remove it any time.")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IDEAS.forEach { (trigger, action) ->
                        IdeaRow(trigger, action) {
                            book.save(Rule(RuleBook.newId(now()) + trigger.hashCode().toUInt().toString(36), trigger, action))
                            changed()
                        }
                    }
                }
            }
        } else {
            Panel(padding = 8.dp) {
                book.rules.forEach { rule ->
                    RuleRow(rule, faded = book.paused, open = { editing = rule }, turn = { book.turn(rule.id, it); changed() })
                }
            }
        }
        BigButton("Add automation", BudsIcons.Plus) {
            editing = Rule(RuleBook.newId(now()), Trigger.Connect, Action.PutScene("focus"))
        }
        Hint(
            "On a phone, automations also run while Blazbuds is closed. On a computer they run while Blazbuds is open or in the tray.",
            Modifier.padding(horizontal = 8.dp),
        )
    }
}

private val IDEAS = listOf(
    Trigger.Connect to Action.PutScene("focus"),
    Trigger.At(22 * 60, ALL_DAYS) to Action.PutScene("relax"),
    Trigger.At(8 * 60 + 30, WEEKDAYS) to Action.PutScene("commute"),
    Trigger.BatteryBelow(20) to Action.PutMode(ListeningMode.Normal),
)

private fun Trigger.icon(): ImageVector = when (this) {
    Trigger.Connect -> BudsIcons.Earbuds
    is Trigger.At -> BudsIcons.Sun
    is Trigger.BatteryBelow -> BudsIcons.Battery
}

private fun Trigger.hue(): Color = when (this) {
    Trigger.Connect -> Hue.Teal
    is Trigger.At -> Hue.Amber
    is Trigger.BatteryBelow -> Hue.Red
}

@Composable
private fun IdeaRow(trigger: Trigger, action: Action, add: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clip(RoundedCornerShape(14.dp)).background(palette.raised)
            .press(onClick = add).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(trigger.hue(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(trigger.icon(), null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(trigger.words(), style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Text("→ " + action.words(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        }
        Icon(BudsIcons.Plus, "Add", tint = palette.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RuleRow(rule: Rule, faded: Boolean, open: () -> Unit, turn: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clip(RoundedCornerShape(14.dp)).press(onClick = open)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val live = rule.on && !faded
        Box(Modifier.size(40.dp).background(if (live) rule.trigger.hue() else palette.raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(rule.trigger.icon(), null, tint = if (live) Color.White else palette.muted, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(rule.trigger.words(), style = MaterialTheme.typography.bodyLarge, color = if (live) palette.text else palette.muted)
            Text("→ " + rule.action.words(), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        }
        androidx.compose.material3.Switch(
            rule.on,
            onCheckedChange = turn,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = palette.accent,
                checkedThumbColor = palette.onAccent,
                uncheckedTrackColor = palette.raised,
                uncheckedBorderColor = palette.line,
                uncheckedThumbColor = palette.muted,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(start: Rule, isNew: Boolean, save: (Rule) -> Unit, delete: () -> Unit, back: () -> Unit) {
    var trigger by remember { mutableStateOf(start.trigger) }
    var action by remember { mutableStateOf(start.action) }
    PlaceFrame(if (isNew) "New automation" else "Automation", back) {
        Panel {
            SectionTitle("When")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("They connect", trigger == Trigger.Connect, icon = BudsIcons.Earbuds) { trigger = Trigger.Connect }
                Choice("At a time", trigger is Trigger.At, icon = BudsIcons.Sun) { if (trigger !is Trigger.At) trigger = Trigger.At(22 * 60) }
                Choice("Battery low", trigger is Trigger.BatteryBelow, icon = BudsIcons.Battery) { if (trigger !is Trigger.BatteryBelow) trigger = Trigger.BatteryBelow(20) }
            }
            when (val t = trigger) {
                is Trigger.At -> {
                    Spacer(Modifier.height(16.dp))
                    TimePicker(t.minute) { trigger = t.copy(minute = it) }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("Every day", t.days == ALL_DAYS) { trigger = t.copy(days = ALL_DAYS) }
                        Choice("Weekdays", t.days == WEEKDAYS) { trigger = t.copy(days = WEEKDAYS) }
                        Choice("Weekends", t.days == WEEKEND) { trigger = t.copy(days = WEEKEND) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAY_NAMES.forEachIndexed { i, name ->
                            val day = i + 1
                            Choice(name, day in t.days) {
                                val days = if (day in t.days) t.days - day else t.days + day
                                if (days.isNotEmpty()) trigger = t.copy(days = days)
                            }
                        }
                    }
                }
                is Trigger.BatteryBelow -> {
                    Spacer(Modifier.height(14.dp))
                    Hint("When either earbud drops to")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 40).forEach { level ->
                            Choice("$level%", t.percent == level) { trigger = Trigger.BatteryBelow(level) }
                        }
                    }
                }
                Trigger.Connect -> {
                    Spacer(Modifier.height(10.dp))
                    Hint("As you take them out of the case, or come back in range.")
                }
            }
        }
        Panel {
            SectionTitle("Do")
            Spacer(Modifier.height(8.dp))
            Hint("A scene")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Scene.DEFAULTS.forEach { scene ->
                    Choice(scene.name, action == Action.PutScene(scene.id)) { action = Action.PutScene(scene.id) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Hint("Or just the listening mode")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListeningMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        ListeningMode.NoiseCancelling -> BudsIcons.Cancelling
                        ListeningMode.Transparency -> BudsIcons.Transparency
                        ListeningMode.Normal -> BudsIcons.Normal
                    }
                    Choice(mode.words(), action == Action.PutMode(mode), icon = icon) { action = Action.PutMode(mode) }
                }
            }
        }
        BigButton(if (isNew) "Add" else "Save", BudsIcons.Check) { save(start.copy(trigger = trigger, action = action)) }
        if (!isNew) BigButton("Remove", BudsIcons.Trash, quiet = true) { delete() }
    }
}

/** Hours and minutes, each moved with a minus and a plus; minutes in fives. */
@Composable
private fun TimePicker(minute: Int, pick: (Int) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Stepper("Hour") { up -> pick(((minute + if (up) 60 else -60) + DAY) % DAY) }
        Text(
            clock(minute), style = MaterialTheme.typography.displaySmall, color = palette.text, textAlign = TextAlign.Center,
            modifier = Modifier.width(150.dp),
        )
        Stepper("Minutes") { up ->
            val m = minute % 60
            val next = if (up) (m / 5 + 1) * 5 else ((m + 4) / 5 - 1) * 5
            pick(minute - m + ((next % 60) + 60) % 60)
        }
    }
}

private const val DAY = 24 * 60

@Composable
private fun Stepper(label: String, step: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("+", "$label up") { step(true) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        StepButton("−", "$label down") { step(false) }
    }
}

@Composable
private fun StepButton(sign: String, label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier.size(44.dp).background(palette.raised, CircleShape).clip(CircleShape).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, style = MaterialTheme.typography.titleLarge, color = palette.accent)
    }
}
