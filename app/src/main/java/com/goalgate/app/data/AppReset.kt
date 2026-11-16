package com.goalgate.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.goalgate.app.GoalGateApp
import com.goalgate.app.detection.Engines
import com.goalgate.app.detection.GateController
import com.goalgate.app.ui.GoalGateWidget

/**
 * "Reset GoalGate" in Settings: deletes everything GoalGate has stored and
 * sends the user back to the start of setup.
 *
 * Deleted: chosen apps, every answer and trigger, review list, streak and
 * stats, all settings, cooldowns and time-check clocks.
 * Not changed: permissions the user granted in Android settings. An app
 * cannot switch those off itself; setup simply skips the ones already on.
 */
object AppReset {

    /** Does the work on the background thread, then calls [onDone] on the main thread. */
    fun resetEverything(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext as GoalGateApp
        app.dbThread.execute {
            app.database.clearAllTables()
            for (file in listOf("settings", "gate_state")) {
                app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
            }
            GateController.forgetEverything()
            Handler(Looper.getMainLooper()).post {
                Engines.apply(app)           // stops detection until setup is finished again
                GoalGateWidget.refresh(app)  // the widget's streak goes back to 0
                onDone()
            }
        }
    }
}
