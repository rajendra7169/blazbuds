package com.blazify.buds.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.blazify.buds.R
import com.blazify.buds.ui.BatteryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Every quarter of an hour or so, while the app is closed, Android lets this look at the earbuds for a moment: their
 * batteries go into the history, and a low one brings the alert.
 */
class BatteryJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        // The app has them open already and is looking after it.
        if (Remote.live != null) return false
        scope.launch {
            runCatching {
                Remote.battery(applicationContext)?.let { state ->
                    BatteryLog().look(state)?.let { notifyLow(applicationContext, it) }
                    // Low-battery automations, such as Normal mode to make the rest last.
                    Automations.run(applicationContext, com.blazify.buds.ui.RuleBook().forBattery(state))
                }
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB = 41
        private const val CHANNEL = "battery"

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(JOB) != null) return
            jobs.schedule(
                JobInfo.Builder(JOB, ComponentName(context, BatteryJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun notifyLow(context: Context, text: String) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Low battery", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notice = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Low battery")
                .setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(CHANNEL.hashCode(), notice) }
        }
    }
}
