package com.goalgate.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.goalgate.app.data.AppDatabase
import com.goalgate.app.data.BREAK_LENGTHS
import com.goalgate.app.data.DowntimeSchedule
import com.goalgate.app.data.MAX_BREAKS_PER_DAY
import com.goalgate.app.data.TimeWindow
import com.goalgate.app.data.TriggerLog
import com.goalgate.app.data.breaksLeft
import com.goalgate.app.data.computeWeek
import com.goalgate.app.data.isScheduledDowntime
import com.goalgate.app.data.startOfDayMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Executors

/** Breaks (3 a day, 30 minutes at most) and downtime being offered only in its hours. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreaksTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val background = Executors.newSingleThreadExecutor()
    private val utc: ZoneId = ZoneOffset.UTC

    @After
    fun cleanUp() {
        context.deleteDatabase(AppDatabase.FILE_NAME)
        background.shutdown()
    }

    @Test
    fun theLimits_areThreeADay_andThirtyMinutesAtMost() {
        assertEquals(3, MAX_BREAKS_PER_DAY)
        assertEquals(30, BREAK_LENGTHS.max())
        assertTrue(BREAK_LENGTHS.all { it in 1..30 })
        assertEquals(BREAK_LENGTHS.sorted(), BREAK_LENGTHS)
    }

    @Test
    fun breaksLeft_countsDown_andStopsAtZero() {
        assertEquals(3, breaksLeft(0))
        assertEquals(2, breaksLeft(1))
        assertEquals(1, breaksLeft(2))
        assertEquals(0, breaksLeft(3))
        assertEquals(0, breaksLeft(4)) // never negative, however many were logged
    }

    @Test
    fun startOfDay_isMidnightInTheUsersZone() {
        val noon = LocalDateTime.of(2026, 11, 23, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(utc).toInstant().toEpochMilli(), startOfDayMillis(noon, utc))
        val seoul = ZoneId.of("Asia/Seoul") // 23:30 UTC on the 22nd is already the 23rd in Seoul
        val lateUtc = LocalDateTime.of(2026, 11, 22, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(seoul).toInstant().toEpochMilli(), startOfDayMillis(lateUtc, seoul))
    }

    @Test
    fun usedBreaks_areCountedFromTheLog_forToday_onlyWhenContinued() {
        val now = LocalDateTime.of(2026, 11, 23, 15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val midnight = startOfDayMillis(now, utc)
        val hour = 3_600_000L
        fun row(at: Long, answer: String, action: String) = TriggerLog(
            packageName = "a", openedAt = at, delayMs = 1, engine = "USAGE", questionId = "", correct = true,
            action = action, answer = answer, downtimeMinutes = 20,
        )
        val database = AppDatabase.create(context)
        val dao = database.triggerLogDao()
        background.submit {
            dao.insert(row(midnight + 8 * hour, "BREAK", "CONTINUE"))
            dao.insert(row(midnight + 10 * hour, "BREAK", "CONTINUE"))
            dao.insert(row(midnight + 11 * hour, "BREAK", "CLOSE"))      // chose it, then closed: not used
            dao.insert(row(midnight + 12 * hour, "DOWNTIME", "CONTINUE")) // downtime is not a break
            dao.insert(row(midnight - 2 * hour, "BREAK", "CONTINUE"))     // yesterday evening
            dao.insert(row(midnight + 13 * hour, "NO", "CONTINUE"))
        }.get()

        val used = background.submit<Int> { dao.breakTimesSince(midnight).size }.get()
        assertEquals(2, used)
        assertEquals(1, breaksLeft(used))

        // One more, and that is all three: none left until tomorrow.
        background.submit { dao.insert(row(midnight + 14 * hour, "BREAK", "CONTINUE")) }.get()
        val all = background.submit<Int> { dao.breakTimesSince(midnight).size }.get()
        assertEquals(0, breaksLeft(all))

        // The next day starts again from zero.
        val tomorrow = startOfDayMillis(now + 24 * hour, utc)
        assertEquals(0, background.submit<Int> { dao.breakTimesSince(tomorrow).size }.get())
        assertEquals(3, breaksLeft(0))
        database.close()
    }

    @Test
    fun week_countsBreaksApart_fromDowntime() {
        val now = LocalDate.of(2026, 11, 23).atStartOfDay(utc).toInstant().toEpochMilli() + 12 * 3_600_000L
        fun log(answer: String, action: String, minutes: Int) = TriggerLog(
            packageName = "a", openedAt = now - 3_600_000L, delayMs = 1, engine = "USAGE", questionId = "",
            correct = true, action = action, answer = answer, downtimeMinutes = minutes,
        )
        val week = computeWeek(
            listOf(log("BREAK", "CONTINUE", 20), log("BREAK", "CONTINUE", 30), log("BREAK", "CLOSE", 10), log("DOWNTIME", "CONTINUE", 60)),
            now, utc,
        )
        assertEquals(2, week.breaks)
        assertEquals(50, week.breakMinutes)
        assertEquals(1, week.downtimes)
        assertEquals(60, week.downtimeMinutes)
    }

    @Test
    fun downtime_isOfferedOnlyInItsHours_notAllDay() {
        val evenings = DowntimeSchedule(listOf(TimeWindow(21 * 60, 23 * 60)), weekends = false, holidays = false)
        val monday = { h: Int -> LocalDateTime.of(2026, 11, 23, h, 0) }
        for (hour in 0..23) {
            assertEquals("hour $hour", hour in 21..22, isScheduledDowntime(monday(hour), evenings, emptySet()))
        }
        // With nothing scheduled, downtime is never offered at all.
        val nothing = DowntimeSchedule(emptyList(), weekends = false, holidays = false)
        assertFalse((0..23).any { isScheduledDowntime(monday(it), nothing, emptySet()) })
    }
}
