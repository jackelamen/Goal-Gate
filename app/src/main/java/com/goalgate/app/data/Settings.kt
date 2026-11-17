package com.goalgate.app.data

import android.content.Context
import java.time.DayOfWeek
import java.util.Locale

/** The two ways GoalGate can detect that an app was opened. */
enum class Engine { USAGE, ACCESSIBILITY }

/**
 * How the pause speaks after an answer. Gentle is softer; Direct is shorter
 * and names the choice plainly. Neither one scolds.
 */
enum class Tone { GENTLE, DIRECT }

/**
 * The user's choices from the Settings screen.
 *
 * These are a handful of small values, so they are stored in SharedPreferences
 * (a simple key/value file on the phone) instead of the database. That also
 * lets the background services read them instantly.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Has the user finished the first-run walkthrough? */
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboardingDone", false)
        set(value) = prefs.edit().putBoolean("onboardingDone", value).apply()

    var engine: Engine
        get() = Engine.valueOf(prefs.getString("engine", Engine.USAGE.name)!!)
        set(value) = prefs.edit().putString("engine", value.name).apply()

    /** Light, dark, or follow the phone. Changing it in Settings repaints at once. */
    var themeMode: ThemeMode
        get() = themeModeOf(prefs.getString("themeMode", null))
        set(value) {
            prefs.edit().putString("themeMode", value.name).apply()
            AppAppearance.theme = value
        }

    /** English, Korean, or follow the phone (see [wrapWithLanguage], which reads this key). */
    var language: LanguageChoice
        get() = languageChoiceOf(prefs.getString("language", null))
        set(value) = prefs.edit().putString("language", value.name).apply()

    var tone: Tone
        get() = Tone.entries.firstOrNull { it.name == prefs.getString("tone", null) } ?: Tone.GENTLE
        set(value) = prefs.edit().putString("tone", value.name).apply()

    /** After "Continue", don't ask again for the same app for this many minutes (1, 5 or 15). */
    var cooldownMinutes: Int
        get() = prefs.getInt("cooldownMinutes", 5)
        set(value) = prefs.edit().putInt("cooldownMinutes", value).apply()

    /**
     * Time check: after "Continue", ask again if the user is still in the app
     * after this many minutes. 0 means off.
     */
    var sessionLimitMinutes: Int
        get() = prefs.getInt("sessionLimitMinutes", 0)
        set(value) = prefs.edit().putInt("sessionLimitMinutes", value).apply()

    /**
     * When the app is in English, also show each reflection line in Korean.
     * On by default when the phone's language is Korean. (When the app itself
     * is in Korean, the lines are already Korean and this is not used.)
     */
    var koreanExplanations: Boolean
        get() = prefs.getBoolean("koreanExplanations", Locale.getDefault().language == "ko")
        set(value) = prefs.edit().putBoolean("koreanExplanations", value).apply()

    // ---- Workday (for goals marked "Workday") ----

    /** Workdays, Monday to Friday by default. */
    var workdayDays: Set<DayOfWeek>
        get() = prefs.getString("workdayDays", "1,2,3,4,5")!!.split(',')
            .mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSet()
        set(value) = prefs.edit().putString("workdayDays", value.map { it.value }.sorted().joinToString(",")).apply()

    /** Workday hours, 09:00 to 18:00 by default (minutes after midnight). */
    var workdayHours: TimeWindow
        get() = TimeWindow(prefs.getInt("workdayStart", 9 * 60), prefs.getInt("workdayEnd", 18 * 60))
        set(value) = prefs.edit().putInt("workdayStart", value.startMinute).putInt("workdayEnd", value.endMinute).apply()

    val workday: Workday get() = Workday(workdayDays, workdayHours)

    // ---- Scheduled downtime ----

    /** Daily downtime hours (up to [MAX_DOWNTIME_WINDOWS]). None by default. */
    var downtimeWindows: List<TimeWindow>
        get() = TimeWindow.decodeList(prefs.getString("downtimeWindows", ""))
        set(value) = prefs.edit().putString("downtimeWindows", TimeWindow.encodeList(value)).apply()

    /** Saturdays and Sundays count as downtime. */
    var downtimeWeekends: Boolean
        get() = prefs.getBoolean("downtimeWeekends", false)
        set(value) = prefs.edit().putBoolean("downtimeWeekends", value).apply()

    /** Public holidays (of [holidayCountry]) count as downtime. */
    var downtimeHolidays: Boolean
        get() = prefs.getBoolean("downtimeHolidays", false)
        set(value) = prefs.edit().putBoolean("downtimeHolidays", value).apply()

    val downtimeSchedule: DowntimeSchedule
        get() = DowntimeSchedule(downtimeWindows, downtimeWeekends, downtimeHolidays)

    /**
     * Whose public holidays to use ("KR", "US"...), or "" for none. Holidays
     * are never workdays, and can count as downtime (see [downtimeHolidays]).
     */
    var holidayCountry: String
        get() = prefs.getString("holidayCountry", "")!!
        set(value) = prefs.edit().putString("holidayCountry", value).apply()

    /** Has the user read and accepted the accessibility disclosure? */
    var accessibilityDisclosureAccepted: Boolean
        get() = prefs.getBoolean("accessibilityDisclosureAccepted", false)
        set(value) = prefs.edit().putBoolean("accessibilityDisclosureAccepted", value).apply()
}
