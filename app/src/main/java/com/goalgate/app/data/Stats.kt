package com.goalgate.app.data

import java.time.Instant
import java.time.ZoneId

/*
 * Small, plain calculations used by the Reflections and Debug screens and by
 * the pause. They take a list of log rows and return numbers. No Android code
 * here, so they are easy to read and easy to unit test.
 *
 * GoalGate counts things; it never scores them. There are no streaks,
 * percentages or grades.
 */

private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

/** Part of the day, for the weekly sentence ("mostly in the evening"). */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** 05:00-11:59 morning, 12:00-16:59 afternoon, 17:00-21:59 evening, otherwise night. */
fun dayPartOf(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

/** How often one goal was picked this week. [goalId] null = "None right now". */
data class GoalCount(val goalId: Long?, val times: Int)

/** One app's week: how many times GoalGate paused it, and how often the user chose Close. */
data class AppWeek(val packageName: String, val pauses: Int, val closes: Int)

/**
 * The one plain sentence at the top of Reflections:
 * "This week you answered No 6 times. Most of those were in the evening."
 * [mostly] is null when no part of the day stands out.
 */
data class WeekSentence(val answer: Answer, val times: Int, val mostly: DayPart?)

/** Everything the Reflections screen shows for the last seven days. */
data class Week(
    val pauses: Int,
    val yes: Int,
    val maybe: Int,
    val no: Int,
    val closes: Int,
    val continues: Int,
    val goals: List<GoalCount>,
    val apps: List<AppWeek>,
    val sentence: WeekSentence?,
    /** Times the user chose "It's my downtime" and went on, and the minutes they chose in total. */
    val downtimes: Int = 0,
    val downtimeMinutes: Int = 0,
) {
    val answered get() = yes + maybe + no
}

/** The rows from the last seven days (up to [now]). */
fun lastWeek(logs: List<TriggerLog>, now: Long): List<TriggerLog> =
    logs.filter { it.openedAt in (now - WEEK_MS)..now }

fun computeWeek(logs: List<TriggerLog>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Week {
    val week = lastWeek(logs, now)
    val answered = week.filter { answerOf(it.answer) != null }
    val count = { a: Answer -> answered.count { answerOf(it.answer) == a } }
    return Week(
        pauses = week.size,
        yes = count(Answer.YES),
        maybe = count(Answer.MAYBE),
        no = count(Answer.NO),
        closes = week.count { it.action == TriggerLog.ACTION_CLOSE },
        continues = week.count { it.action == TriggerLog.ACTION_CONTINUE },
        goals = answered
            .groupBy { it.goalId }
            .map { (id, rows) -> GoalCount(id, rows.size) }
            // Busiest first; "None right now" after the goals when tied.
            .sortedWith(compareByDescending<GoalCount> { it.times }.thenBy { it.goalId == null }.thenBy { it.goalId }),
        apps = weekByApp(week, now),
        sentence = weekSentence(answered, zone),
        downtimes = startedDowntimes(week).size,
        downtimeMinutes = startedDowntimes(week).sumOf { it.downtimeMinutes ?: 0 },
    )
}

/** Pauses where the user chose "It's my downtime" and then continued (not closed). */
fun startedDowntimes(logs: List<TriggerLog>): List<TriggerLog> =
    logs.filter { it.answer == TriggerLog.ANSWER_DOWNTIME && it.action == TriggerLog.ACTION_CONTINUE }

/**
 * Picks the answer given most often this week. On a tie, "No" comes first,
 * then "Maybe": those are the ones worth noticing. Adds the part of the day
 * when at least half of those answers fell in it (and there are 3 or more).
 */
fun weekSentence(answered: List<TriggerLog>, zone: ZoneId = ZoneId.systemDefault()): WeekSentence? {
    val byAnswer = answered.groupBy { answerOf(it.answer) }
    val top = listOf(Answer.NO, Answer.MAYBE, Answer.YES)
        .maxByOrNull { byAnswer[it]?.size ?: 0 } ?: return null
    val rows = byAnswer[top].orEmpty()
    if (rows.isEmpty()) return null
    val parts = rows.groupingBy { dayPartOf(Instant.ofEpochMilli(it.openedAt).atZone(zone).hour) }.eachCount()
    val (part, times) = parts.maxByOrNull { it.value }!!
    val mostly = if (rows.size >= 3 && times * 2 >= rows.size) part else null
    return WeekSentence(top, rows.size, mostly)
}

/**
 * Groups the last 7 days of pauses by app, busiest first.
 * Every pause counts (app opened or time check).
 */
fun weekByApp(logs: List<TriggerLog>, now: Long): List<AppWeek> =
    lastWeek(logs, now)
        .groupBy { it.packageName }
        .map { (packageName, rows) ->
            AppWeek(packageName, pauses = rows.size, closes = rows.count { it.action == TriggerLog.ACTION_CLOSE })
        }
        .sortedWith(compareByDescending<AppWeek> { it.pauses }.thenBy { it.packageName })

/**
 * How many times an app was opened in the last hour, including this time.
 *
 * [earlierOpenTimes] are the "opened at" times of the logged opens in the last
 * hour. The same open can appear more than once (older versions logged it again
 * when the pause screen was rebuilt), and the current open may already be in the
 * list (the screen was rebuilt after it was logged), so each distinct time counts
 * once and the current one is always counted exactly once.
 */
fun opensThisHour(earlierOpenTimes: List<Long>, currentOpenedAt: Long): Int =
    earlierOpenTimes.filter { it != currentOpenedAt }.distinct().size + 1

/**
 * Gentle escalation: the more often the user opens the same app within an
 * hour, the longer they wait before "Continue" can be tapped.
 * "Close" is always available straight away; this is a pause, not a block.
 *
 * @param opensThisHour how many times the app was opened in the last hour,
 *                      including this time.
 * @return seconds to wait before "Continue" works.
 */
fun continueDelaySeconds(opensThisHour: Int): Int = when {
    opensThisHour >= 5 -> 10
    opensThisHour >= 3 -> 5
    else -> 0
}

/**
 * Returns the value below which [percent] % of the numbers fall
 * ("nearest rank" method). percentile(list, 50) is the median.
 * Returns null for an empty list. (Used by the hidden Debug screen.)
 */
fun percentile(values: List<Long>, percent: Int): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    // Example: 10 values, 90th percentile -> rank 9 -> index 8.
    val rank = Math.ceil(percent / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}
