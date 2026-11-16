package com.goalgate.app

import com.goalgate.app.data.Answer
import com.goalgate.app.data.AppWeek
import com.goalgate.app.data.DayPart
import com.goalgate.app.data.GOAL_MAX_CHARS
import com.goalgate.app.data.Goal
import com.goalgate.app.data.GoalCount
import com.goalgate.app.data.MAX_ACTIVE_GOALS
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.data.achievedGoals
import com.goalgate.app.data.activeGoals
import com.goalgate.app.data.canAddGoal
import com.goalgate.app.data.cleanGoalText
import com.goalgate.app.data.computeWeek
import com.goalgate.app.data.continueDelaySeconds
import com.goalgate.app.data.dayPartOf
import com.goalgate.app.data.moveGoal
import com.goalgate.app.data.nextPosition
import com.goalgate.app.data.opensThisHour
import com.goalgate.app.data.parseReflections
import com.goalgate.app.data.percentile
import com.goalgate.app.data.pickLine
import com.goalgate.app.data.renumber
import com.goalgate.app.data.todaysGoal
import com.goalgate.app.data.weekByApp
import com.goalgate.app.data.weekSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Tests for the plain-Kotlin logic. Run them with:  ./gradlew test
 * They run on your computer; no phone is needed.
 */
class LogicTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    // A fixed "now": Monday 16 November 2026, 12:00 UTC.
    private val now = LocalDate.of(2026, 11, 16).atStartOfDay(utc).toInstant().toEpochMilli() + 12 * hour

    private fun goal(id: Long, position: Int, achievedAt: Long? = null, text: String = "Goal $id") =
        Goal(id = id, text = text, position = position, createdAt = 0, achievedAt = achievedAt)

    private fun log(
        at: Long,
        answer: Answer? = null,
        goalId: Long? = null,
        action: String = TriggerLog.ACTION_NONE,
        app: String = "com.instagram.android",
    ) = TriggerLog(
        packageName = app, openedAt = at, delayMs = 100, engine = "USAGE", questionId = "",
        correct = if (answer != null) true else null, action = action, goalId = goalId, answer = answer?.name,
    )

    // --- Content ---

    private val lines = parseReflections(File("src/main/assets/reflections.json").readText())

    @Test
    fun reflectionFile_has40WellFormedLines() {
        assertEquals(40, lines.size)
        assertEquals(40, lines.map { it.id }.toSet().size)
        assertEquals(40, lines.map { it.en }.toSet().size)
        assertEquals(40, lines.map { it.ko }.toSet().size)
        assertEquals(10, lines.count { it.answer == Answer.YES })
        assertEquals(14, lines.count { it.answer == Answer.MAYBE })
        assertEquals(16, lines.count { it.answer == Answer.NO })
        for (line in lines) {
            assertTrue("id matches answer: ${line.id}", line.id.first().uppercaseChar() == line.answer.name.first())
            assertTrue("has Korean: ${line.id}", line.ko.any { it in '가'..'힣' })
            assertFalse("no em dash: ${line.id}", line.en.contains('—') || line.ko.contains('—'))
        }
    }

    @Test
    fun pickLine_matchesTheAnswer_andAvoidsRecentLines() {
        val random = Random(7)
        val recent = lines.filter { it.answer == Answer.NO }.take(8).map { it.id }
        repeat(300) {
            val picked = pickLine(lines, Answer.NO, recent, random)!!
            assertEquals(Answer.NO, picked.answer)
            assertTrue(picked.id !in recent)
        }
        // If every line was shown lately, it still returns one rather than nothing.
        val all = lines.filter { it.answer == Answer.YES }.map { it.id }
        assertEquals(Answer.YES, pickLine(lines, Answer.YES, all, random)!!.answer)
        assertNull(pickLine(emptyList(), Answer.YES, emptyList(), random))
    }

    // --- Goals: ordering and achievement ---

    @Test
    fun activeGoals_areInTheUsersOrder_andAchievedOnesAreSetAside() {
        val goals = listOf(goal(1, 2), goal(2, 0), goal(3, 1, achievedAt = 50), goal(4, 1), goal(5, 0, achievedAt = 90))
        assertEquals(listOf(2L, 4L, 1L), activeGoals(goals).map { it.id })
        assertEquals(listOf(5L, 3L), achievedGoals(goals).map { it.id }) // most recent first
    }

    @Test
    fun moveGoal_swapsNeighbours_andRenumbers() {
        val goals = listOf(goal(1, 0), goal(2, 1), goal(3, 2))
        val changed = moveGoal(goals, id = 3, delta = -1)
        assertEquals(setOf(2L to 2, 3L to 1), changed.map { it.id to it.position }.toSet())
        // Moving past either end does nothing.
        assertTrue(moveGoal(goals, id = 1, delta = -1).isEmpty())
        assertTrue(moveGoal(goals, id = 3, delta = 1).isEmpty())
        assertTrue(moveGoal(goals, id = 99, delta = 1).isEmpty())
        // Achieved goals are ignored when moving.
        val withAchieved = goals + goal(4, 0, achievedAt = 5)
        assertEquals(
            listOf(1L to 1, 2L to 0),
            moveGoal(withAchieved, id = 2, delta = -1).map { it.id to it.position }.sortedBy { it.first },
        )
    }

    @Test
    fun renumber_closesGapsAfterAGoalIsAchieved() {
        val left = listOf(goal(1, 0), goal(3, 2), goal(4, 3))
        assertEquals(listOf(3L to 1, 4L to 2), renumber(left).map { it.id to it.position })
        assertEquals(4, nextPosition(left))
        assertEquals(0, nextPosition(emptyList()))
    }

    @Test
    fun goalLimit_isFiveActiveGoals() {
        val four = (1L..4L).map { goal(it, it.toInt()) }
        assertTrue(canAddGoal(four))
        val five = four + goal(5, 5)
        assertEquals(MAX_ACTIVE_GOALS, five.size)
        assertFalse(canAddGoal(five))
        // An achieved goal frees a place.
        assertTrue(canAddGoal(four + goal(5, 5, achievedAt = 1)))
    }

    @Test
    fun cleanGoalText_keepsTheUsersWords() {
        assertEquals("Walk every morning", cleanGoalText("  Walk every morning  "))
        assertEquals("자격증 공부 끝내기", cleanGoalText("자격증 공부 끝내기"))
        assertEquals("Finish my certificate?", cleanGoalText("Finish my certificate?")) // questions are fine
        assertEquals("one line", cleanGoalText("one\nline"))
        assertNull(cleanGoalText("   "))
        assertEquals(GOAL_MAX_CHARS, cleanGoalText("x".repeat(80))!!.length)
    }

    @Test
    fun todaysGoal_isTheLastOnePickedToday_elseTheFirst() {
        val goals = listOf(goal(1, 0), goal(2, 1), goal(3, 2, achievedAt = 1))
        val today = LocalDate.of(2026, 11, 16)
        assertEquals(1L, todaysGoal(goals, emptyList(), today, utc)?.id)
        val logs = listOf(
            log(now - 2 * hour, Answer.YES, goalId = 2),
            log(now - day, Answer.YES, goalId = 1),          // yesterday: ignored
            log(now - hour, Answer.NO, goalId = 3),          // achieved since: ignored
        )
        assertEquals(2L, todaysGoal(goals, logs, today, utc)?.id)
        assertNull(todaysGoal(listOf(goal(3, 0, achievedAt = 1)), logs, today, utc))
    }

    // --- Reflections: counts and the weekly sentence ---

    @Test
    fun computeWeek_countsAnswersClosesAndGoals() {
        val logs = listOf(
            log(now - hour, Answer.NO, goalId = 1, action = TriggerLog.ACTION_CLOSE),
            log(now - 2 * hour, Answer.NO, goalId = 1, action = TriggerLog.ACTION_CONTINUE),
            log(now - 3 * hour, Answer.YES, goalId = 2, action = TriggerLog.ACTION_CONTINUE),
            log(now - 4 * hour, Answer.MAYBE, goalId = null, action = TriggerLog.ACTION_CLOSE),
            log(now - 5 * hour),                                  // left without answering
            log(now - 8 * day, Answer.NO, goalId = 1),            // older than a week
        )
        val week = computeWeek(logs, now, utc)
        assertEquals(5, week.pauses)
        assertEquals(1, week.yes)
        assertEquals(1, week.maybe)
        assertEquals(2, week.no)
        assertEquals(4, week.answered)
        assertEquals(2, week.closes)
        assertEquals(2, week.continues)
        assertEquals(listOf(GoalCount(1, 2), GoalCount(2, 1), GoalCount(null, 1)), week.goals)
        assertEquals(listOf(AppWeek("com.instagram.android", 5, 2)), week.apps)
    }

    @Test
    fun weekSentence_namesTheMostCommonAnswer_andWhenItHappened() {
        fun at(hourOfDay: Int, daysAgo: Int) =
            LocalDate.of(2026, 11, 16).minusDays(daysAgo.toLong()).atStartOfDay(utc).toInstant().toEpochMilli() + hourOfDay * hour
        // "This week you said No 6 times, mostly in the evening."
        val evenings = (0 until 5).map { log(at(20, it), Answer.NO) } + log(at(9, 1), Answer.NO)
        val yeses = (0 until 3).map { log(at(13, it), Answer.YES) }
        val sentence = weekSentence(evenings + yeses, utc)!!
        assertEquals(Answer.NO, sentence.answer)
        assertEquals(6, sentence.times)
        assertEquals(DayPart.EVENING, sentence.mostly)

        // Spread across the day: no "mostly".
        val spread = listOf(8, 13, 19, 23).map { log(at(it, 1), Answer.YES) }
        assertNull(weekSentence(spread, utc)!!.mostly)

        // Fewer than 3: no "mostly" either.
        assertNull(weekSentence(listOf(log(at(20, 1), Answer.MAYBE), log(at(21, 2), Answer.MAYBE)), utc)!!.mostly)

        // A tie goes to No, then Maybe: those are the ones worth noticing.
        val tie = listOf(log(at(10, 1), Answer.YES), log(at(10, 2), Answer.NO))
        assertEquals(Answer.NO, weekSentence(tie, utc)!!.answer)
        val tie2 = listOf(log(at(10, 1), Answer.YES), log(at(10, 2), Answer.MAYBE))
        assertEquals(Answer.MAYBE, weekSentence(tie2, utc)!!.answer)

        // No answers at all: no sentence.
        assertNull(weekSentence(emptyList(), utc))
    }

    @Test
    fun dayParts_coverTheWholeDay() {
        assertEquals(DayPart.NIGHT, dayPartOf(4))
        assertEquals(DayPart.MORNING, dayPartOf(5))
        assertEquals(DayPart.MORNING, dayPartOf(11))
        assertEquals(DayPart.AFTERNOON, dayPartOf(12))
        assertEquals(DayPart.EVENING, dayPartOf(17))
        assertEquals(DayPart.EVENING, dayPartOf(21))
        assertEquals(DayPart.NIGHT, dayPartOf(22))
        assertEquals(DayPart.NIGHT, dayPartOf(0))
    }

    @Test
    fun weekByApp_groupsAndSortsBusiestFirst() {
        val logs = listOf(
            log(now - day, action = TriggerLog.ACTION_CLOSE, app = "b"),
            log(now - day, app = "a"),
            log(now - day, action = TriggerLog.ACTION_CLOSE, app = "a"),
            log(now - 8 * day, app = "c"),
        )
        assertEquals(listOf(AppWeek("a", 2, 1), AppWeek("b", 1, 1)), weekByApp(logs, now))
    }

    // --- Escalation (unchanged from WordGate, plus the counting fix) ---

    @Test
    fun escalation_waitsLongerTheMoreOftenTheAppIsOpened() {
        assertEquals(0, continueDelaySeconds(1))
        assertEquals(0, continueDelaySeconds(2))
        assertEquals(5, continueDelaySeconds(3))
        assertEquals(5, continueDelaySeconds(4))
        assertEquals(10, continueDelaySeconds(5))
        assertEquals(10, continueDelaySeconds(12))
    }

    @Test
    fun opensThisHour_countsEachOpenOnce() {
        // Nothing logged yet: this is the first open.
        assertEquals(1, opensThisHour(emptyList(), currentOpenedAt = 500))
        // Two earlier opens, plus this one.
        assertEquals(3, opensThisHour(listOf(100, 200), currentOpenedAt = 500))
        // The same open logged twice (rebuilt screen) counts once.
        assertEquals(3, opensThisHour(listOf(100, 100, 200), currentOpenedAt = 500))
        // The current open is already in the log: it is not counted twice.
        assertEquals(3, opensThisHour(listOf(100, 200, 500, 500), currentOpenedAt = 500))
        // Together with the delay: a second open in the hour still has no wait.
        assertEquals(0, continueDelaySeconds(opensThisHour(listOf(100, 100), currentOpenedAt = 500)))
        assertNotEquals(0, continueDelaySeconds(opensThisHour(listOf(100, 200), currentOpenedAt = 500)))
    }

    @Test
    fun percentile_medianAndP90() {
        val values = (1L..10L).toList()
        assertEquals(5L, percentile(values, 50))
        assertEquals(9L, percentile(values, 90))
        assertNull(percentile(emptyList(), 50))
    }
}
