package com.goalgate.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The rules for goals, in plain Kotlin so they are easy to read and to test.
 * No Android code here.
 */

/** At most this many goals that are not yet achieved. */
const val MAX_ACTIVE_GOALS = 5

/** Longest goal, in characters. Long goals shrink to fit their card. */
const val GOAL_MAX_CHARS = 60

/** Longest "why" line, in characters. */
const val WHY_MAX_CHARS = 60

/** Longest step, in characters. */
const val STEP_MAX_CHARS = 60

/** At most this many steps in one goal. Steps are a short path, not a task list. */
const val MAX_STEPS = 20

/** Goals not yet achieved, in the order the user arranged them. */
fun activeGoals(goals: List<Goal>): List<Goal> =
    goals.filter { it.achievedAt == null }.sortedWith(compareBy<Goal> { it.position }.thenBy { it.id })

/** Achieved goals, most recent first. */
fun achievedGoals(goals: List<Goal>): List<Goal> =
    goals.filter { it.achievedAt != null }.sortedByDescending { it.achievedAt }

fun canAddGoal(goals: List<Goal>): Boolean = activeGoals(goals).size < MAX_ACTIVE_GOALS

/**
 * Tidies what the user typed without changing their words: one line, no
 * spaces at either end, and no longer than [max]. Returns null if nothing is left.
 */
fun cleanGoalText(typed: String, max: Int = GOAL_MAX_CHARS): String? {
    val oneLine = typed.replace('\n', ' ').replace('\r', ' ').trim()
    return oneLine.take(max).trimEnd().ifEmpty { null }
}

/**
 * Moves the active goal [id] up ([delta] = -1) or down (+1) and numbers the
 * active goals 0, 1, 2... again. Returns only the goals whose position
 * changed, ready to save. Moving past either end does nothing.
 */
fun moveGoal(goals: List<Goal>, id: Long, delta: Int): List<Goal> {
    val ordered = activeGoals(goals).toMutableList()
    val from = ordered.indexOfFirst { it.id == id }
    if (from < 0) return emptyList()
    val to = (from + delta).coerceIn(0, ordered.lastIndex)
    if (to == from) return emptyList()
    ordered.add(to, ordered.removeAt(from))
    return renumber(ordered)
}

/** Gives [ordered] the positions 0, 1, 2... Returns the goals that changed. */
fun renumber(ordered: List<Goal>): List<Goal> =
    ordered.mapIndexedNotNull { index, goal -> if (goal.position == index) null else goal.copy(position = index) }

/** The position for a new goal: after every active goal. */
fun nextPosition(goals: List<Goal>): Int = (activeGoals(goals).maxOfOrNull { it.position } ?: -1) + 1

/**
 * The goal for the widget: the one picked most recently today, if it is still
 * active; otherwise the user's first goal. Null when there is no active goal.
 */
fun todaysGoal(
    goals: List<Goal>,
    logs: List<TriggerLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Goal? {
    val active = activeGoals(goals)
    val byId = active.associateBy { it.id }
    val pickedToday = logs
        .filter { it.goalId != null && Instant.ofEpochMilli(it.openedAt).atZone(zone).toLocalDate() == today }
        .sortedByDescending { it.openedAt }
        .firstNotNullOfOrNull { byId[it.goalId] }
    return pickedToday ?: active.firstOrNull()
}

// ---------------------------------------------------------------------------
// Steps inside goals
// ---------------------------------------------------------------------------

/**
 * The goal's next step: the first one, in order, not yet checked off.
 * Null when the goal has no steps or all of them are done.
 */
fun nextStep(steps: List<GoalStep>, goalId: Long): GoalStep? =
    steps.filter { it.goalId == goalId && it.doneAt == null }
        .minWithOrNull(compareBy<GoalStep> { it.position }.thenBy { it.id })

/** The next step of every goal that has one, by goal id. */
fun nextSteps(steps: List<GoalStep>): Map<Long, GoalStep> =
    steps.groupBy { it.goalId }.mapNotNull { (goalId, list) -> nextStep(list, goalId)?.let { goalId to it } }.toMap()

/** Returns [items] with the one at [from] moved up (-1) or down (+1). Past either end: unchanged. */
fun <T> moveItem(items: List<T>, from: Int, delta: Int): List<T> {
    val to = (from + delta).coerceIn(0, items.lastIndex)
    if (from !in items.indices || to == from) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}
