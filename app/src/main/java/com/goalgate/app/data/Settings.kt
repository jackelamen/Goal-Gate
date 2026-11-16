package com.goalgate.app.data

import android.content.Context
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

    /** Has the user read and accepted the accessibility disclosure? */
    var accessibilityDisclosureAccepted: Boolean
        get() = prefs.getBoolean("accessibilityDisclosureAccepted", false)
        set(value) = prefs.edit().putBoolean("accessibilityDisclosureAccepted", value).apply()
}
