package com.blazify.buds.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Scene
import com.russhwolf.settings.Settings

/** What starts an automation. */
sealed interface Trigger {
    /** The earbuds connect: out of the case, or back in range. */
    data object Connect : Trigger

    /** A time of day, on some days of the week (1 = Monday … 7 = Sunday). */
    data class At(val minute: Int, val days: Set<Int> = ALL_DAYS) : Trigger

    /** An earbud's battery falls to this level or below; once each time it runs down. */
    data class BatteryBelow(val percent: Int) : Trigger
}

/** What an automation does to the earbuds. */
sealed interface Action {
    data class PutScene(val id: String) : Action
    data class PutMode(val mode: ListeningMode) : Action

    fun changes(): List<Change> = when (this) {
        is PutScene -> Scene.DEFAULTS.firstOrNull { it.id == id }?.changes().orEmpty()
        is PutMode -> listOf(Change.Mode(mode))
    }
}

/** One automation: when [trigger] happens, do [action], unless switched off. */
data class Rule(val id: String, val trigger: Trigger, val action: Action, val on: Boolean = true) {
    fun line(): String = listOf(id, if (on) "1" else "0", trigger.code(), action.code()).joinToString("|")

    companion object {
        fun parse(line: String): Rule? {
            val p = line.split("|")
            if (p.size != 4) return null
            val trigger = parseTrigger(p[2]) ?: return null
            val action = parseAction(p[3]) ?: return null
            return Rule(p[0], trigger, action, p[1] == "1")
        }
    }
}

val ALL_DAYS = (1..7).toSet()
val WEEKDAYS = (1..5).toSet()
val WEEKEND = setOf(6, 7)

private fun Trigger.code(): String = when (this) {
    Trigger.Connect -> "C"
    is Trigger.At -> "T,$minute,${days.sorted().joinToString("")}"
    is Trigger.BatteryBelow -> "B,$percent"
}

private fun Action.code(): String = when (this) {
    is Action.PutScene -> "S,$id"
    is Action.PutMode -> "M,${mode.name}"
}

private fun parseTrigger(code: String): Trigger? {
    val p = code.split(",")
    return when (p[0]) {
        "C" -> Trigger.Connect
        "T" -> {
            val minute = p.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return null
            val days = p.getOrNull(2).orEmpty().mapNotNull { it.digitToIntOrNull() }.filter { it in 1..7 }.toSet()
            Trigger.At(minute, days.ifEmpty { ALL_DAYS })
        }
        "B" -> p.getOrNull(1)?.toIntOrNull()?.let { Trigger.BatteryBelow(it.coerceIn(5, 50)) }
        else -> null
    }
}

private fun parseAction(code: String): Action? {
    val p = code.split(",")
    return when (p[0]) {
        "S" -> p.getOrNull(1)?.takeIf { id -> Scene.DEFAULTS.any { it.id == id } }?.let { Action.PutScene(it) }
        "M" -> p.getOrNull(1)?.let { name -> runCatching { ListeningMode.valueOf(name) }.getOrNull() }?.let { Action.PutMode(it) }
        else -> null
    }
}

/** The local day of the week (1 = Monday … 7 = Sunday) and minute of the day, and a key for the day itself. */
data class LocalMoment(val day: Int, val minute: Int, val date: String)

expect fun localMoment(): LocalMoment

/**
 * The person's automations, kept on the device. The app, the phone's background helpers and the computer's tray all
 * read the same list, and a run is written down so the same moment never fires a rule twice from two places.
 */
class RuleBook(private val store: Settings = Settings()) {
    var rules by mutableStateOf(read())
        private set

    /** One switch that holds every automation without forgetting them. */
    var paused by mutableStateOf(store.getBoolean(PAUSED, false))
        private set

    private fun read(): List<Rule> = store.getString(RULES, "").split("\n").mapNotNull { Rule.parse(it) }

    private fun write() = store.putString(RULES, rules.joinToString("\n") { it.line() })

    /** Reads the list again, as written by another part of the app. */
    fun reload() {
        rules = read()
        paused = store.getBoolean(PAUSED, false)
    }

    fun save(rule: Rule) {
        val at = rules.indexOfFirst { it.id == rule.id }
        rules = if (at >= 0) rules.toMutableList().also { it[at] = rule } else rules + rule
        write()
    }

    fun delete(id: String) {
        rules = rules.filterNot { it.id == id }
        write()
    }

    fun turn(id: String, on: Boolean) {
        rules.firstOrNull { it.id == id }?.let { save(it.copy(on = on)) }
    }

    fun pause(on: Boolean) {
        paused = on
        store.putBoolean(PAUSED, on)
    }

    private fun live(): List<Rule> = if (paused) emptyList() else rules.filter { it.on }

    /**
     * The rules for the earbuds connecting, each at most once in a short while, so the phone hearing the connection
     * and the app seeing it don't both run it.
     */
    fun forConnect(now: Long): List<Rule> = live().filter { it.trigger == Trigger.Connect }.filter { claim(it, "connect", now, CONNECT_GAP) }

    /** The time rules due at [moment] or up to [lateBy] minutes before it, each once a day. */
    fun dueAt(moment: LocalMoment, lateBy: Int = 10): List<Rule> = live().filter { rule ->
        val at = rule.trigger as? Trigger.At ?: return@filter false
        val late = moment.minute - at.minute
        at.days.contains(moment.day) && late in 0..lateBy && claimOnce(rule, "time:${moment.date}")
    }

    /** The low-battery rules that [state] sets off, once per run-down; a charge above the level sets them again. */
    fun forBattery(state: BudsState): List<Rule> {
        val levels = listOfNotNull(state.left?.percent, state.right?.percent, state.overall?.percent).filter { it >= 0 }
        if (levels.isEmpty()) return emptyList()
        val lowest = levels.min()
        val charging = state.left?.charging == true || state.right?.charging == true
        return live().filter { rule ->
            val level = (rule.trigger as? Trigger.BatteryBelow)?.percent ?: return@filter false
            val key = FIRED + rule.id
            if (charging || lowest > level) {
                store.remove(key)
                false
            } else if (store.getBoolean(key, false)) {
                false
            } else {
                store.putBoolean(key, true)
                true
            }
        }
    }

    /** The minutes of the day with a time rule switched on, for the phone to wake at. */
    fun times(): List<Trigger.At> = live().mapNotNull { it.trigger as? Trigger.At }

    private fun claim(rule: Rule, kind: String, now: Long, gap: Long): Boolean {
        val key = RAN + rule.id + ":" + kind
        val last = store.getLong(key, 0L)
        if (now - last < gap) return false
        store.putLong(key, now)
        return true
    }

    private fun claimOnce(rule: Rule, mark: String): Boolean {
        val key = RAN + rule.id + ":time"
        if (store.getString(key, "") == mark) return false
        store.putString(key, mark)
        return true
    }

    companion object {
        private const val RULES = "rules"
        private const val PAUSED = "rules_paused"
        private const val RAN = "rule_ran_"
        private const val FIRED = "rule_battery_fired_"
        private const val CONNECT_GAP = 90_000L

        /** A new rule's id: short, and different from the others. */
        fun newId(now: Long): String = now.toString(36)
    }
}

/** The words for a rule, as the list shows it. */
fun Trigger.words(): String = when (this) {
    Trigger.Connect -> "When the earbuds connect"
    is Trigger.At -> "At ${clock(minute)}${daysWords(days)}"
    is Trigger.BatteryBelow -> "When battery drops to $percent%"
}

fun Action.words(): String = when (this) {
    is Action.PutScene -> (Scene.DEFAULTS.firstOrNull { it.id == id }?.name ?: id) + " scene"
    is Action.PutMode -> mode.words()
}

fun ListeningMode.words(): String = when (this) {
    ListeningMode.NoiseCancelling -> "Noise cancelling"
    ListeningMode.Transparency -> "Transparency"
    ListeningMode.Normal -> "Normal"
}

fun clock(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

private fun daysWords(days: Set<Int>): String = when (days) {
    ALL_DAYS -> ""
    WEEKDAYS -> " on weekdays"
    WEEKEND -> " at weekends"
    else -> " on " + days.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
}

val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
