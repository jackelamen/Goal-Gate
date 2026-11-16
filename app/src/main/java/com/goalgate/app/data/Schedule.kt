package com.goalgate.app.data

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * WHEN THINGS APPLY: the workday (for goals marked "Workday") and the
 * scheduled downtime (hours, weekends, public holidays). Plain rules with no
 * Android code, so they are easy to read and to test. Settings stores the
 * user's choices; ScheduleNow (bottom of this file) applies them to "now".
 */

/**
 * A daily stretch of time, in minutes after midnight (0 to 1439).
 * If [endMinute] is earlier than [startMinute], it runs past midnight
 * (22:00 to 01:00). If both are equal, it covers the whole day.
 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {

    val crossesMidnight get() = endMinute < startMinute

    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    companion object {
        /** "1260-1410;480-540" -> two windows. Bad entries are skipped. */
        fun decodeList(text: String?): List<TimeWindow> =
            text.orEmpty().split(';').mapNotNull { part ->
                val bits = part.split('-')
                val start = bits.getOrNull(0)?.toIntOrNull()
                val end = bits.getOrNull(1)?.toIntOrNull()
                if (bits.size == 2 && start != null && end != null && start in 0..1439 && end in 0..1439) {
                    TimeWindow(start, end)
                } else {
                    null
                }
            }

        fun encodeList(windows: List<TimeWindow>): String =
            windows.joinToString(";") { "${it.startMinute}-${it.endMinute}" }
    }
}

/** At most this many downtime windows. */
const val MAX_DOWNTIME_WINDOWS = 3

/** The minutes after midnight of [at]. */
fun minuteOfDay(at: LocalDateTime): Int = at.hour * 60 + at.minute

/** The workday: which days, and which hours on those days. */
data class Workday(val days: Set<DayOfWeek>, val hours: TimeWindow)

/**
 * True during the workday. A public holiday is never a workday. For hours
 * that run past midnight (a night shift), the early-morning part belongs to
 * the day the shift started.
 */
fun isWorkTime(at: LocalDateTime, workday: Workday, holidays: Set<LocalDate>): Boolean {
    val minute = minuteOfDay(at)
    if (!workday.hours.contains(minute)) return false
    val shiftDay = if (workday.hours.crossesMidnight && minute < workday.hours.endMinute) {
        at.toLocalDate().minusDays(1)
    } else {
        at.toLocalDate()
    }
    return shiftDay.dayOfWeek in workday.days && shiftDay !in holidays
}

/** The downtime the user scheduled in Settings. */
data class DowntimeSchedule(
    val windows: List<TimeWindow>,
    val weekends: Boolean,
    val holidays: Boolean,
)

/**
 * True during scheduled downtime: inside one of the windows, on a weekend
 * (if switched on), or on a public holiday (if switched on and a country is set).
 * In scheduled downtime, "It's my downtime" is offered first on the pause.
 */
fun isScheduledDowntime(at: LocalDateTime, schedule: DowntimeSchedule, holidays: Set<LocalDate>): Boolean {
    val minute = minuteOfDay(at)
    if (schedule.windows.any { it.contains(minute) }) return true
    if (schedule.weekends && at.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return true
    return schedule.holidays && at.toLocalDate() in holidays
}

/** The goals that apply right now: "Always" goals, plus "Workday" goals during the workday. */
fun goalsForNow(goals: List<Goal>, workTime: Boolean): List<Goal> =
    activeGoals(goals).filter { it.schedule != Goal.SCHEDULE_WORKDAY || workTime }

/** The lengths offered for "It's my downtime", in minutes. */
val DOWNTIME_LENGTHS = listOf(15, 30, 60)

// ---------------------------------------------------------------------------
// Public holidays (bundled; nothing is downloaded)
// ---------------------------------------------------------------------------

/** National public holidays per country code ("KR", "US"...), and the years covered. */
data class HolidayTable(val firstYear: Int, val lastYear: Int, val countries: Map<String, Set<LocalDate>>)

/** Reads the JSON written by tools/make_holidays.py. */
fun parseHolidays(json: String): HolidayTable {
    val root = JSONObject(json)
    val countries = root.getJSONObject("countries")
    val map = HashMap<String, Set<LocalDate>>()
    for (code in countries.keys()) {
        val dates = countries.getJSONArray(code)
        map[code] = List(dates.length()) { LocalDate.parse(dates.getString(it)) }.toSet()
    }
    return HolidayTable(root.getInt("firstYear"), root.getInt("lastYear"), map)
}

/** Loads assets/holidays.json once and keeps it in memory. */
object HolidayBank {
    @Volatile private var cache: HolidayTable? = null

    fun load(context: Context): HolidayTable =
        cache ?: synchronized(this) {
            cache ?: parseHolidays(
                context.assets.open("holidays.json").bufferedReader().use { it.readText() }
            ).also { cache = it }
        }

    /** The dates for [country], or none when no country is set. */
    fun dates(context: Context, country: String): Set<LocalDate> =
        if (country.isEmpty()) emptySet() else load(context).countries[country].orEmpty()
}

/** Today's schedule, worked out once from the user's settings. */
data class ScheduleNow(val workTime: Boolean, val downtime: Boolean) {
    companion object {
        fun of(context: Context, settings: Settings, at: LocalDateTime = LocalDateTime.now()): ScheduleNow {
            val holidays = HolidayBank.dates(context, settings.holidayCountry)
            return ScheduleNow(
                workTime = isWorkTime(at, settings.workday, holidays),
                downtime = isScheduledDowntime(at, settings.downtimeSchedule, holidays),
            )
        }
    }
}
