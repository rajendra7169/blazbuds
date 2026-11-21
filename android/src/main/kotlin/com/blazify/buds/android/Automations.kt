package com.blazify.buds.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blazify.buds.core.Logbook
import com.blazify.buds.core.model.Change
import com.blazify.buds.ui.Rule
import com.blazify.buds.ui.RuleBook
import com.blazify.buds.ui.localMoment
import com.blazify.buds.ui.words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The automations while the app is closed: the phone wakes it at each time rule's minute, the connect receiver runs
 * the connect rules, and the battery check runs the low-battery ones. The app runs the same list while it is open;
 * the book writes each run down, so a moment never fires a rule twice.
 */
object Automations {
    private const val TAG = "BlazbudsRules"

    /** Puts these rules' changes on the earbuds: through the app's link when it is open, else a short one. */
    suspend fun run(context: Context, rules: List<Rule>) {
        if (rules.isEmpty()) return
        val changes: List<Change> = rules.flatMap { it.action.changes() }
        rules.forEach { android.util.Log.i(TAG, "run: ${it.trigger.words()} -> ${it.action.words()}") }
        rules.forEach { Logbook.add("Automation: ${it.trigger.words()} → ${it.action.words()}") }
        val done = Remote.applyAll(context, changes)
        android.util.Log.i(TAG, if (done) "applied" else "earbuds not reachable")
    }

    /** Plans the next wake-up for the time rules, or none when there are none. */
    fun plan(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val wake = PendingIntent.getBroadcast(
            context, 0, Intent(context, RuleAlarm::class.java).setAction(RuleAlarm.WAKE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val times = RuleBook().times()
        alarms.cancel(wake)
        if (times.isEmpty()) return
        val now = LocalDateTime.now()
        val next = times.flatMap { at ->
            (0..7).map { ahead -> now.toLocalDate().plusDays(ahead.toLong()).atStartOfDay().plusMinutes(at.minute.toLong()) }
                .filter { it.isAfter(now) && it.dayOfWeek.value in at.days }
        }.minOrNull() ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A short window rather than an exact alarm, which would need its own permission; a minute late is fine.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, 60_000L, wake)
        android.util.Log.i(TAG, "next time rule at $next")
    }
}

/** Wakes for a time rule, and plans the alarms again after a restart or an update. */
class RuleAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            WAKE -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        Automations.run(app, RuleBook().dueAt(localMoment()))
                    } finally {
                        Automations.plan(app)
                        pending.finish()
                    }
                }
            }
            else -> Automations.plan(app)
        }
    }

    companion object {
        const val WAKE = "com.blazify.buds.RULE_WAKE"
    }
}
