package com.goalgate.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.goalgate.app.data.AppAppearance
import com.goalgate.app.data.LanguageChoice
import com.goalgate.app.data.Settings
import com.goalgate.app.data.ThemeMode
import com.goalgate.app.data.isDark
import com.goalgate.app.data.languageChoiceOf
import com.goalgate.app.data.themeModeOf
import com.goalgate.app.data.wrapWithLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The theme and language switches: the rules, the saved choices, and the language actually applied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    // Korean strings carry invisible word joiners (U+2060) so lines break between words only.
    private fun text(c: Context) = c.getString(R.string.settings_title).replace("\u2060", "")

    @Test
    fun theme_followsThePhoneOnlyWhenSetToSystem() {
        for (systemDark in listOf(true, false)) {
            assertEquals(systemDark, isDark(ThemeMode.SYSTEM, systemDark))
            assertFalse(isDark(ThemeMode.LIGHT, systemDark))
            assertTrue(isDark(ThemeMode.DARK, systemDark))
        }
    }

    @Test
    fun savedChoices_defaultToSystem_andIgnoreUnknownValues() {
        assertEquals(ThemeMode.SYSTEM, themeModeOf(null))
        assertEquals(ThemeMode.SYSTEM, themeModeOf("PURPLE"))
        assertEquals(ThemeMode.DARK, themeModeOf("DARK"))
        assertEquals(LanguageChoice.SYSTEM, languageChoiceOf(null))
        assertEquals(LanguageChoice.SYSTEM, languageChoiceOf("KLINGON"))
        assertEquals(LanguageChoice.KOREAN, languageChoiceOf("KOREAN"))
    }

    @Test
    fun settings_saveTheChoices_andTheThemeIsLive() {
        val settings = Settings(context)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(LanguageChoice.SYSTEM, settings.language)
        settings.themeMode = ThemeMode.DARK
        settings.language = LanguageChoice.KOREAN
        assertEquals(ThemeMode.DARK, Settings(context).themeMode) // survives a restart
        assertEquals(LanguageChoice.KOREAN, Settings(context).language)
        assertEquals(ThemeMode.DARK, AppAppearance.theme)         // screens repaint from this
        settings.themeMode = ThemeMode.SYSTEM
        settings.language = LanguageChoice.SYSTEM
    }

    @Test
    @Config(qualifiers = "en")
    fun language_koreanOnAnEnglishPhone_andBack() {
        val settings = Settings(context)
        assertEquals("Settings", text(context))
        assertSame(context, wrapWithLanguage(context)) // System: nothing is changed

        settings.language = LanguageChoice.KOREAN
        assertEquals("설정", text(wrapWithLanguage(context)))
        settings.language = LanguageChoice.ENGLISH
        assertEquals("Settings", text(wrapWithLanguage(context)))
        settings.language = LanguageChoice.SYSTEM
    }

    @Test
    @Config(qualifiers = "ko")
    fun language_englishOnAKoreanPhone() {
        val settings = Settings(context)
        assertEquals("설정", text(context))
        settings.language = LanguageChoice.ENGLISH
        assertEquals("Settings", text(wrapWithLanguage(context)))
        settings.language = LanguageChoice.SYSTEM
        assertEquals("설정", text(wrapWithLanguage(context)))
    }
}
