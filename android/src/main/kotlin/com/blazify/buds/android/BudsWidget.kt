package com.blazify.buds.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.blazify.buds.R
import com.blazify.buds.core.model.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The home-screen widget: battery for each earbud and the case, and the three modes, one tap each. */
class BudsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BudsWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val snap = Remote.snapshot()
            val views = RemoteViews(context.packageName, R.layout.widget_buds)
            views.setTextViewText(R.id.name, snap?.name?.removePrefix("soundcore ") ?: "Blazbuds")
            fun level(value: Int?) = if (value == null || value < 0) "–" else "$value%"
            views.setTextViewText(R.id.left, level(snap?.left))
            views.setTextViewText(R.id.case_level, level(snap?.case))
            views.setTextViewText(R.id.right, level(snap?.right))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.root, open)
            listOf(
                Triple(R.id.mode_cancel, ListeningMode.NoiseCancelling, 1),
                Triple(R.id.mode_aware, ListeningMode.Transparency, 2),
                Triple(R.id.mode_normal, ListeningMode.Normal, 3),
            ).forEach { (id, mode, code) ->
                val intent = Intent(context, WidgetAction::class.java).putExtra(WidgetAction.MODE, mode.name)
                views.setOnClickPendingIntent(id, PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                views.setInt(id, "setBackgroundResource", if (snap?.mode == mode) R.drawable.widget_mode_on else R.drawable.widget_mode_off)
                views.setTextColor(id, context.getColor(if (snap?.mode == mode) R.color.widget_on_text else R.color.widget_text))
            }
            return views
        }
    }
}

/** A mode tapped on the widget: set it on the earbuds, then draw the widget again. */
class WidgetAction : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = runCatching { ListeningMode.valueOf(intent.getStringExtra(MODE) ?: return) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Remote.setMode(context, mode)
                BudsWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val MODE = "mode"
    }
}
