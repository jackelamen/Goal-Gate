package com.goalgate.app.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.goalgate.app.GoalGateApp
import com.goalgate.app.R
import com.goalgate.app.data.todaysGoal
import java.time.LocalDate

/**
 * The home-screen widget: today's goal and a "Check in" button that opens
 * the Goals tab. "Today's goal" is the goal picked most recently today on a
 * pause, or the user's first goal (see todaysGoal).
 *
 * Widgets are drawn by the launcher, not by GoalGate, so they use an XML
 * layout (res/layout/widget_goalgate.xml) filled in with RemoteViews,
 * instead of Compose.
 *
 * Android refreshes it every hour (res/xml/goalgate_widget_info.xml), and
 * GoalGate also refreshes it after every answer and goal change (see [refresh]).
 */
class GoalGateWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        // The database must not be read on the main thread. goAsync() lets the
        // work finish on GoalGate's background thread.
        val pending = goAsync()
        val app = context.applicationContext as GoalGateApp
        app.dbThread.execute {
            try {
                val views = buildViews(context, app)
                for (id in widgetIds) manager.updateAppWidget(id, views)
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, app: GoalGateApp): RemoteViews {
        val goal = todaysGoal(
            app.database.goalDao().getAll(),
            app.database.triggerLogDao().getAll(),
            LocalDate.now(),
        )
        val views = RemoteViews(context.packageName, R.layout.widget_goalgate)
        views.setTextViewText(R.id.widget_goal, goal?.text?.let { userText(it) } ?: context.getString(R.string.widget_no_goal))

        // Tap anywhere: open the Goals tab.
        val tap = PendingIntent.getActivity(
            context,
            0,
            MainActivity.goalsIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, tap)
        views.setOnClickPendingIntent(R.id.widget_check_in, tap)
        return views
    }

    companion object {
        /** Redraws every GoalGate widget on the home screen (for example after an answer). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GoalGateWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, GoalGateWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
