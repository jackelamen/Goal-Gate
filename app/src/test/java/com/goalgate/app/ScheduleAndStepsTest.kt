package com.goalgate.app

import com.goalgate.app.data.DowntimeSchedule
import com.goalgate.app.data.Goal
import com.goalgate.app.data.GoalStep
import com.goalgate.app.data.TimeWindow
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.data.Workday
import com.goalgate.app.data.computeWeek
import com.goalgate.app.data.goalsForNow
import com.goalgate.app.data.isScheduledDowntime
import com.goalgate.app.data.isWorkTime
import com.goalgate.app.data.moveItem
import com.goalgate.app.data.nextStep
import com.goalgate.app.data.nextSteps
import com.goalgate.app.data.parseHolidays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Tests for the workday, scheduled downtime, holidays and steps inside goals. */
class ScheduleAndStepsTest {

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val nineToSix = Workday(weekdays, TimeWindow(9 * 60, 18 * 60))

    // Monday 16 November 2026.
    private fun monday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 16, hour, minute)
    private fun saturday(hour: Int) = LocalDateTime.of(2026, 11, 21, hour, 0)

    // --- Time windows ---

    @Test
    fun timeWindow_coversItsHours_includingPastMidnight() {
        val evening = TimeWindow(21 * 60, 23 * 60 + 30)
        assertTrue(evening.contains(21 * 60))
        assertTrue(evening.contains(23 * 60 + 29))
        assertFalse(evening.contains(23 * 60 + 30)) // the end is not included
        assertFalse(evening.contains(20 * 60 + 59))

        val night = TimeWindow(22 * 60, 1 * 60) // 22:00 to 01:00
        assertTrue(night.crossesMidnight)
        assertTrue(night.contains(23 * 60))
        assertTrue(night.contains(30))
        assertFalse(night.contains(1 * 60))
        assertFalse(night.contains(12 * 60))

        assertTrue(TimeWindow(600, 600).contains(0)) // equal ends: the whole day
    }

    @Test
    fun timeWindows_saveAndLoad_andSkipBadEntries() {
        val windows = listOf(TimeWindow(1260, 1410), TimeWindow(480, 540))
        assertEquals(windows, TimeWindow.decodeList(TimeWindow.encodeList(windows)))
        assertEquals(listOf(TimeWindow(60, 120)), TimeWindow.decodeList("60-120;nonsense;5000-10;-1"))
        assertTrue(TimeWindow.decodeList("").isEmpty())
        assertTrue(TimeWindow.decodeList(null).isEmpty())
    }

    // --- Workday ---

    @Test
    fun workTime_isWorkdaysAtWorkHours_neverAHoliday() {
        assertTrue(isWorkTime(monday(9), nineToSix, emptySet()))
        assertTrue(isWorkTime(monday(17, 59), nineToSix, emptySet()))
        assertFalse(isWorkTime(monday(18), nineToSix, emptySet()))
        assertFalse(isWorkTime(monday(8, 59), nineToSix, emptySet()))
        assertFalse(isWorkTime(saturday(11), nineToSix, emptySet()))
        // A public holiday on a Monday is a day off.
        assertFalse(isWorkTime(monday(11), nineToSix, setOf(LocalDate.of(2026, 11, 16))))
    }

    @Test
    fun workTime_nightShift_belongsToTheDayItStarted() {
        val nights = Workday(setOf(DayOfWeek.FRIDAY), TimeWindow(22 * 60, 6 * 60))
        val friday = LocalDateTime.of(2026, 11, 20, 23, 0)
        val earlySaturday = LocalDateTime.of(2026, 11, 21, 3, 0)
        val earlyFriday = LocalDateTime.of(2026, 11, 20, 3, 0) // Thursday's shift: not a workday
        assertTrue(isWorkTime(friday, nights, emptySet()))
        assertTrue(isWorkTime(earlySaturday, nights, emptySet()))
        assertFalse(isWorkTime(earlyFriday, nights, emptySet()))
    }

    @Test
    fun goalsForNow_hidesWorkdayGoalsOutsideWork() {
        val always = Goal(1, "Walk every morning", null, 0, 0)
        val work = Goal(2, "Finish the report", null, 1, 0, schedule = Goal.SCHEDULE_WORKDAY)
        val achieved = Goal(3, "Done already", null, 2, 0, achievedAt = 5)
        val goals = listOf(work, always, achieved)
        assertEquals(listOf(1L, 2L), goalsForNow(goals, workTime = true).map { it.id })
        assertEquals(listOf(1L), goalsForNow(goals, workTime = false).map { it.id })
    }

    // --- Scheduled downtime ---

    @Test
    fun scheduledDowntime_hoursWeekendsAndHolidays() {
        val evenings = DowntimeSchedule(listOf(TimeWindow(21 * 60, 23 * 60)), weekends = false, holidays = false)
        assertTrue(isScheduledDowntime(monday(21, 30), evenings, emptySet()))
        assertFalse(isScheduledDowntime(monday(12), evenings, emptySet()))
        assertFalse(isScheduledDowntime(saturday(12), evenings, emptySet())) // weekends are off

        val weekends = evenings.copy(weekends = true)
        assertTrue(isScheduledDowntime(saturday(12), weekends, emptySet()))
        assertTrue(isScheduledDowntime(LocalDateTime.of(2026, 11, 22, 9, 0), weekends, emptySet())) // Sunday

        val holiday = setOf(LocalDate.of(2026, 11, 16))
        assertFalse(isScheduledDowntime(monday(12), evenings, holiday)) // holidays switched off
        assertTrue(isScheduledDowntime(monday(12), evenings.copy(holidays = true), holiday))

        val none = DowntimeSchedule(emptyList(), weekends = false, holidays = false)
        assertFalse(isScheduledDowntime(saturday(12), none, holiday))
    }

    @Test
    fun holidayFile_coversEveryCountryAndYear() {
        val table = parseHolidays(File("src/main/assets/holidays.json").readText())
        assertEquals(setOf("KR", "US", "GB", "CA", "AU", "JP"), table.countries.keys)
        assertTrue(table.firstYear <= 2026 && table.lastYear >= 2030)
        for ((code, dates) in table.countries) {
            for (year in table.firstYear..table.lastYear) {
                assertTrue("$code $year New Year's Day", LocalDate.of(year, 1, 1) in dates)
            }
        }
        val korea = table.countries.getValue("KR")
        assertTrue("Chuseok 2026", LocalDate.of(2026, 9, 25) in korea)
        assertTrue("Seollal 2027", LocalDate.of(2027, 2, 7) in korea)
        assertTrue("US Independence Day", LocalDate.of(2026, 7, 4) in table.countries.getValue("US"))
    }

    // --- Steps inside goals ---

    private fun step(id: Long, goalId: Long, position: Int, done: Boolean = false) =
        GoalStep(id = id, goalId = goalId, text = "Step $id", position = position, doneAt = if (done) 100L else null)

    @Test
    fun nextStep_isTheFirstUncheckedOne_andMovesOnWhenChecked() {
        // "Run a 5K": shoes, 1 km, 3 km, 5 km.
        val steps = listOf(step(1, 9, 0), step(2, 9, 1), step(3, 9, 2), step(4, 9, 3))
        assertEquals(1L, nextStep(steps, 9)?.id)
        val afterShoes = steps.map { if (it.id == 1L) it.copy(doneAt = 5) else it }
        assertEquals(2L, nextStep(afterShoes, 9)?.id)
        // Order is by position, not by id or insertion.
        val reordered = listOf(step(1, 9, 2), step(2, 9, 0), step(3, 9, 1))
        assertEquals(2L, nextStep(reordered, 9)?.id)
        // All checked off: no next step.
        assertNull(nextStep(steps.map { it.copy(doneAt = 1) }, 9))
        // No steps at all: no next step.
        assertNull(nextStep(emptyList(), 9))
    }

    @Test
    fun nextSteps_groupsByGoal() {
        val steps = listOf(step(1, 1, 0, done = true), step(2, 1, 1), step(3, 2, 0, done = true), step(4, 3, 0))
        assertEquals(mapOf(1L to 2L, 3L to 4L), nextSteps(steps).mapValues { it.value.id })
    }

    @Test
    fun moveItem_movesOneStepAndStopsAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveItem(list, 1, -1))
        assertEquals(listOf("a", "c", "b"), moveItem(list, 1, 1))
        assertEquals(list, moveItem(list, 0, -1))
        assertEquals(list, moveItem(list, 2, 1))
        assertEquals(list, moveItem(list, 7, 1))
    }

    // --- Downtime in the weekly counts ---

    @Test
    fun week_countsDowntimeSeparately_fromAnswers() {
        val now = LocalDate.of(2026, 11, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 12 * 3_600_000L
        fun log(answer: String?, action: String, minutes: Int? = null) = TriggerLog(
            packageName = "a", openedAt = now - 3_600_000L, delayMs = 1, engine = "USAGE", questionId = "",
            correct = true, action = action, answer = answer, downtimeMinutes = minutes,
        )
        val week = computeWeek(
            listOf(
                log("DOWNTIME", TriggerLog.ACTION_CONTINUE, 30),
                log("DOWNTIME", TriggerLog.ACTION_CONTINUE, 60),
                log("DOWNTIME", TriggerLog.ACTION_CLOSE, 15), // chose it, then closed: not counted
                log("NO", TriggerLog.ACTION_CLOSE),
            ),
            now, ZoneOffset.UTC,
        )
        assertEquals(2, week.downtimes)
        assertEquals(90, week.downtimeMinutes)
        assertEquals(1, week.no)
        assertEquals(1, week.answered) // downtime is not an answer to the question
        assertEquals(4, week.pauses)
    }
}
