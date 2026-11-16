package com.goalgate.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * Each class in this file is one table in the Room database.
 * Each property is one column.
 */

/** An app the user has chosen. Opening it starts a pause. */
@Entity(tableName = "selected_apps")
data class SelectedApp(
    @PrimaryKey val packageName: String,
)

/**
 * One of the user's goals, exactly as they typed it. GoalGate never rewrites
 * the text, so it never has to guess at grammar.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The goal itself, at most [GOAL_MAX_CHARS] characters. */
    val text: String,
    /** An optional one-line reason ("why this matters"), or null. */
    val why: String? = null,
    /** Order on screen: 0 is the top. Only meaningful for goals not yet achieved. */
    val position: Int,
    val createdAt: Long,
    /** When the user marked it achieved (milliseconds since 1970), or null while active. */
    val achievedAt: Long? = null,
)

/** One row for every pause GoalGate showed. */
@Entity(tableName = "trigger_logs")
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The app that was opened, e.g. "com.instagram.android". */
    val packageName: String,
    /** When the app came to the foreground (milliseconds since 1970). */
    val openedAt: Long,
    /** Milliseconds between the app opening and the pause appearing on screen. */
    val delayMs: Long,
    /** Which detection engine caught it: "USAGE" or "ACCESSIBILITY". */
    val engine: String,
    /**
     * The reflection line shown after answering, or "" if none was shown.
     * (The column keeps its old name from WordGate so no data has to move.)
     */
    val questionId: String,
    /** true = the user answered the pause question, null = they left before answering. */
    val correct: Boolean? = null,
    /** "CONTINUE", "CLOSE", or "NONE" if the user left another way. */
    val action: String = ACTION_NONE,
    /**
     * Why the pause appeared: "OPEN" (the app was opened) or "TIME_CHECK"
     * (the user stayed in the app for the time set in Settings).
     */
    @ColumnInfo(defaultValue = REASON_OPEN)
    val reason: String = REASON_OPEN,
    /** The goal the user picked on the pause, or null for "None right now" (or no answer). */
    val goalId: Long? = null,
    /** "YES", "MAYBE" or "NO" (see [Answer]), or null if they left before answering. */
    val answer: String? = null,
) {
    companion object {
        const val REASON_OPEN = "OPEN"
        const val REASON_TIME_CHECK = "TIME_CHECK"

        const val ACTION_NONE = "NONE"
        const val ACTION_CONTINUE = "CONTINUE"
        const val ACTION_CLOSE = "CLOSE"
    }
}
