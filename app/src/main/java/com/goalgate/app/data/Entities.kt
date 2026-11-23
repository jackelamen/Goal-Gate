package com.goalgate.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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
    /**
     * When the goal applies: "ALWAYS", or "WORKDAY" (only during the workday
     * hours set in Settings, never on a day off or a public holiday).
     */
    @ColumnInfo(defaultValue = SCHEDULE_ALWAYS)
    val schedule: String = SCHEDULE_ALWAYS,
) {
    companion object {
        const val SCHEDULE_ALWAYS = "ALWAYS"
        const val SCHEDULE_WORKDAY = "WORKDAY"
    }
}

/**
 * One step towards a goal, in the user's own words. Steps are optional and
 * kept in order; the first one not yet checked off is the goal's "next step".
 * No dates, priorities or sub-steps: this is a short list, not a task manager.
 */
@Entity(tableName = "goal_steps", indices = [Index("goalId")])
data class GoalStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The goal this step belongs to. */
    val goalId: Long,
    /** The step itself, at most [STEP_MAX_CHARS] characters. */
    val text: String,
    /** Order within the goal: 0 is first. */
    val position: Int,
    /** When the user checked it off (milliseconds since 1970), or null while open. */
    val doneAt: Long? = null,
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
    /**
     * "YES", "MAYBE" or "NO" (see [Answer]), "DOWNTIME" when the user chose
     * "It's my downtime", "BREAK" when they chose "Take a break", or null if
     * they left before answering.
     */
    val answer: String? = null,
    /** With "DOWNTIME" or "BREAK": the minutes chosen. Otherwise null. */
    val downtimeMinutes: Int? = null,
) {
    companion object {
        const val REASON_OPEN = "OPEN"
        const val REASON_TIME_CHECK = "TIME_CHECK"
        /** The downtime the user chose has ended while they were still in the app. */
        const val REASON_DOWNTIME_END = "DOWNTIME_END"
        /** The break the user chose has ended while they were still in the app. */
        const val REASON_BREAK_END = "BREAK_END"

        const val ANSWER_DOWNTIME = "DOWNTIME"
        /** "Take a break": a short, limited rest (see MAX_BREAKS_PER_DAY). */
        const val ANSWER_BREAK = "BREAK"

        const val ACTION_NONE = "NONE"
        const val ACTION_CONTINUE = "CONTINUE"
        const val ACTION_CLOSE = "CLOSE"
    }
}
