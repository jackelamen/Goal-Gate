package com.goalgate.app.data

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/*
 * APPEARANCE: the theme (light, dark, or follow the phone) and the language
 * (English, Korean, or follow the phone). Both are chosen in Settings and
 * apply at once, on every Android version GoalGate supports.
 *
 * Theme is live Compose state, so every screen repaints the moment it changes.
 * Language is applied when a screen is created (see [wrapWithLanguage]), so
 * changing it restarts the screen the user is on; the pause screen, which
 * opens fresh each time, simply picks it up.
 */

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** English, Korean, or whatever the phone is set to. */
enum class LanguageChoice(val tag: String?) { SYSTEM(null), ENGLISH("en"), KOREAN("ko") }

/** Is the app dark right now? [systemDark] is what the phone is set to. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Reads a saved name back into a choice; anything unknown means "follow the phone". */
fun themeModeOf(name: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
fun languageChoiceOf(name: String?): LanguageChoice = LanguageChoice.entries.firstOrNull { it.name == name } ?: LanguageChoice.SYSTEM

/**
 * What Compose reads to draw the theme. It lives for as long as the app
 * process does; [GoalGateApp] fills it from Settings at start.
 */
object AppAppearance {
    var theme by mutableStateOf(ThemeMode.SYSTEM)
}

/**
 * Returns [base] set to the language chosen in Settings, or [base] unchanged
 * for "System". Screens call this in attachBaseContext, before anything is
 * drawn. It reads the saved choice directly, so it works before the app's
 * own objects exist.
 */
fun wrapWithLanguage(base: Context): Context {
    val choice = languageChoiceOf(base.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", null))
    val tag = choice.tag ?: return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale) // dates, day names and country names follow the same language
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(locale)
    return base.createConfigurationContext(configuration)
}
