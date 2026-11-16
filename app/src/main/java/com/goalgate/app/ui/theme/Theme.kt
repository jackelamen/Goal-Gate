package com.goalgate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.R

/*
 * THE DESIGN SYSTEM (read this before changing any colour or font)
 *
 * The idea: alpine field notes. A trail map at dusk. Calm, grounded, specific.
 * Nothing shouts and nothing scolds; the app only reminds and asks.
 *
 * COLOUR - five roles, nothing else:
 *   Paper  - backgrounds of the tool screens (Goals, Apps, Settings).
 *            White cards sit on it with a soft shadow.
 *   Ink    - all text, and the one main button on each paper screen.
 *   Pine   - the accent on paper. It marks what is selected or chosen:
 *            the selected tab, the chosen engine, a bar in Reflections.
 *   Pine ground - the deep green-black of the moments that matter: the
 *            pause, the welcome, the week card. Faint contour lines run
 *            across it (see ui/Ground.kt), like a map of the climb.
 *   Signal - the spark: one warm orange, used ONLY on the pine ground and
 *            only for two things: the Close button on the pause (the choice
 *            GoalGate exists to encourage) and the peak of the summit mark.
 *            An achieved goal is the one other place it appears.
 *   There is deliberately no red and no green "good/bad" colour. Answering
 *   No is not a failure, so it never looks like one.
 *
 * THE SUMMIT MARK - the app's signature:
 *   A short ridge line rising to one peak, with a signal-orange dot on top.
 *   It sits in the pause header (the dot is the only thing that breathes),
 *   in the app icon, under the selected tab, and next to an achieved goal.
 *
 * TYPE - two families:
 *   DM Serif Display - goals, questions, headlines and numbers. The user's
 *                      own words are always set in it, so they carry weight.
 *                      Its italic is used for the reflection lines.
 *   Figtree (sans)   - buttons, labels and body text.
 *   Small labels are set in capitals with modest spacing (see SectionLabel).
 */

// Paper and ink
val Paper = Color(0xFFECEFEA)     // lichen mist: a cool paper from the same landscape as the pine
val Ink = Color(0xFF14171A)
val InkSoft = Color(0xFF5C6661)
val Hairline = Color(0xFFD8DED8)

// The accent on paper
val Pine = Color(0xFF1D5B4C)

// The hero ground: a vertical fall from pine to near-black pine
val PineGround = Color(0xFF0F2A26)
val PineDeep = Color(0xFF0A1D1A)
val Contour = Color(0xFFFFFFFF)     // drawn at 6% over the ground

// The spark: used only on the pine ground
val Signal = Color(0xFFFF6A2B)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDFE4DF),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFC2C9C3),
    outlineVariant = Hairline,
    error = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1EFE9),
    onPrimary = Ink,
    background = Color(0xFF0C1110),
    onBackground = Color(0xFFF1EFE9),
    surface = Color(0xFF172120),
    onSurface = Color(0xFFF1EFE9),
    surfaceVariant = Color(0xFF1F2B29),
    onSurfaceVariant = Color(0xFF97A3A0),
    outline = Color(0xFF34413E),
    outlineVariant = Color(0xFF22302D),
    error = Color(0xFFF1EFE9),
)

/**
 * The colours of anything placed on the pine ground: white text, a white
 * main button with ink text, and white cards. Used by PineGroundBox.
 * It is the same in light and dark mode: the ground is already dark.
 */
val PineColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    background = PineGround,
    onBackground = Color.White,
    surface = Color(0xFFF9FAF8),
    onSurface = Ink,
    surfaceVariant = Color(0xFFDFE4DF),
    onSurfaceVariant = InkSoft,
    outline = Color.White.copy(alpha = 0.45f),
    outlineVariant = Color.White.copy(alpha = 0.16f),
)

/** The accent, and the colour of text placed on top of it. */
data class AccentColors(val accent: Color, val onAccent: Color)

private val LightAccent = AccentColors(accent = Pine, onAccent = Color.White)
private val DarkAccent = AccentColors(accent = Color(0xFF7CC2AC), onAccent = Color(0xFF07211B))

/** On the pine ground the accent is the signal orange (with ink on top of it: 6:1 contrast). */
val SignalAccent = AccentColors(accent = Signal, onAccent = Ink)

val LocalAccent = staticCompositionLocalOf { LightAccent }

/** DM Serif Display has one weight (and a true italic). */
val Serif = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
)

/* Figtree is "variable": one file holds every weight. We ask it for the ones we use. */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sans = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

private val AppTypography = Typography(
    // Big counts on the Reflections card
    displayLarge = TextStyle(fontFamily = Serif, fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-2).sp),
    // Screen titles and the welcome line
    headlineLarge = TextStyle(fontFamily = Serif, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp),
    // The pause question, and a chosen goal on its card
    headlineMedium = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.2).sp),
    // Goals in lists, numbers in lists
    titleLarge = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp),
    // Buttons
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    // Small capital labels (the text is put in capitals by SectionLabel)
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp),
    // Italic notes: the reflection lines, hints
    labelSmall = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * The same type scale, adjusted for Korean. DM Serif Display and Figtree have
 * no Korean letters, so Android draws Korean with the phone's own Korean font.
 * That font has no italic (a slanted copy looks wrong), and Korean reads best
 * without the tight or wide letter spacing used for English.
 */
private val KoreanTypography = AppTypography.copy(
    displayLarge = AppTypography.displayLarge.copy(letterSpacing = 0.sp),
    headlineLarge = AppTypography.headlineLarge.copy(letterSpacing = 0.sp, fontSize = 32.sp, lineHeight = 42.sp),
    headlineMedium = AppTypography.headlineMedium.copy(letterSpacing = 0.sp, fontSize = 25.sp, lineHeight = 35.sp),
    titleLarge = AppTypography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    labelMedium = AppTypography.labelMedium.copy(letterSpacing = 0.3.sp, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelSmall = AppTypography.labelSmall.copy(fontStyle = FontStyle.Normal, fontFamily = Sans, fontSize = 15.sp, lineHeight = 23.sp),
)

/** Wrap any screen in this to give it GoalGate's colours, fonts and shapes. */
@Composable
fun GoalGateTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // The app's language follows the phone (or the per-app language in Android settings).
    val korean = LocalConfiguration.current.locales[0].language == "ko"
    CompositionLocalProvider(LocalAccent provides if (dark) DarkAccent else LightAccent) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = if (korean) KoreanTypography else AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
