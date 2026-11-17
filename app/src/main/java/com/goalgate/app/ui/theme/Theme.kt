package com.goalgate.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.goalgate.app.data.AppAppearance
import com.goalgate.app.data.isDark
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
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.R

/*
 * THE DESIGN SYSTEM (read this before changing any colour or font)
 *
 * The idea: sunrise on the trail. You are partway up, the sky is turning
 * from night blue to rose and gold, and the day is still yours. The app
 * should feel awake and on your side. It never scolds and never shouts.
 *
 * COLOUR:
 *   Dawn sky - the ground of the moments that matter (the pause, the
 *            welcome, the week card): night blue at the top, through violet,
 *            to rose at the horizon, with dark mountain ridges in front and
 *            the sun's glow behind them (see ui/Ground.kt).
 *   Sun    - the spark, a warm gold. On the dawn sky it marks the sun in
 *            the summit mark, the Close button on the pause (the choice
 *            GoalGate exists to encourage) and the answer you chose.
 *   Goal colours - each goal gets its own bright colour (coral, sun, sky,
 *            mint, lilac), kept for as long as the goal exists. It shows as
 *            the stripe on the goal's card, its bar in Reflections and its
 *            check-off circle. Colour here means "this goal", nothing else.
 *   Warm white and night ink - the paper screens (Goals, Apps, Settings).
 *            Ink is a deep night blue, not black, so the paper screens
 *            belong to the same sky.
 *   Rose   - the accent on paper: what is selected or switched on.
 *   There is deliberately no red/green "good/bad" colour. Answering No is
 *   not a failure, so it never looks like one.
 *
 * THE SUMMIT MARK - the app's signature:
 *   A ridge line rising to one peak, with the sun coming up beside it. It
 *   sits in the pause header (the sun is the only thing that breathes), in
 *   the app icon, under the selected tab, and next to an achieved goal.
 *
 * TYPE - two families:
 *   Bricolage Grotesque - goals, questions, headlines and numbers: bold,
 *                         warm and a little quirky. The user's own words
 *                         are always set in it, so they carry weight.
 *   Figtree             - buttons, labels, notes and body text.
 */

// Warm paper and night ink
val Paper = Color(0xFFFFF7EE)
val Ink = Color(0xFF1B1745)
val InkSoft = Color(0xFF5F5A85)
val Hairline = Color(0xFFEFE3D6)

// The accent on paper
val Rose = Color(0xFFE5446D)

// The dawn sky, top to horizon, and the ridges in front of it
val SkyNight = Color(0xFF15164F)
val SkyViolet = Color(0xFF48298C)
val SkyRose = Color(0xFFB03580)
val SunGlow = Color(0xFFFF8A5B)
val RidgeFar = Color(0xFF5B2B86)
val RidgeMid = Color(0xFF2E1E63)
val RidgeNear = Color(0xFF16113F)

// The spark: used only on the dawn sky
val Sun = Color(0xFFFFC043)

/**
 * The five goal colours. A goal keeps its colour for life (it follows the
 * goal's id, not its place in the list), so the user learns "coral is my
 * running goal". All five are bright enough for the dawn sky and for paper.
 */
val GoalColors = listOf(
    Color(0xFFFF6B5E), // coral
    Color(0xFFFFB02E), // sun
    Color(0xFF39A7F2), // sky
    Color(0xFF22B98A), // mint
    Color(0xFF9C7BFF), // lilac
)

fun goalColor(goalId: Long): Color = GoalColors[Math.floorMod(goalId - 1, GoalColors.size.toLong()).toInt()]

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF6EADD),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFD9CBBB),
    outlineVariant = Hairline,
    error = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6F1FF),
    onPrimary = Ink,
    background = Color(0xFF0F0D2B),
    onBackground = Color(0xFFF6F1FF),
    surface = Color(0xFF1C1947),
    onSurface = Color(0xFFF6F1FF),
    surfaceVariant = Color(0xFF28245A),
    onSurfaceVariant = Color(0xFFA9A4D0),
    outline = Color(0xFF443F7A),
    outlineVariant = Color(0xFF2A2660),
    error = Color(0xFFF6F1FF),
)

/**
 * The colours of anything placed on the dawn sky: white text, a white main
 * button with ink text, and white cards. Used by HeroGround.
 * It is the same in light and dark mode: the sky is already dark.
 */
val HeroColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    background = SkyNight,
    onBackground = Color.White,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E9FF),
    onSurfaceVariant = InkSoft,
    outline = Color.White.copy(alpha = 0.5f),
    outlineVariant = Color.White.copy(alpha = 0.2f),
)

/** The accent, and the colour of text placed on top of it. */
data class AccentColors(val accent: Color, val onAccent: Color)

private val LightAccent = AccentColors(accent = Rose, onAccent = Color.White)
private val DarkAccent = AccentColors(accent = Color(0xFFFF8FA8), onAccent = Color(0xFF2A0B1C))

/** On the dawn sky the accent is the sun gold (with ink on top of it). */
val HeroAccent = AccentColors(accent = Sun, onAccent = Ink)

val LocalAccent = staticCompositionLocalOf { LightAccent }

/* Both fonts are "variable": one file holds every weight. We ask each for the ones we use. */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 48f)),
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(bricolage(600), bricolage(700), bricolage(800))
val Sans = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

private val AppTypography = Typography(
    // Big counts
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 84.sp, lineHeight = 88.sp, letterSpacing = (-2).sp),
    // Screen titles and the welcome line
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-0.8).sp),
    // The pause title, and a chosen goal
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    // Goals in lists, numbers in lists
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp),
    // Buttons
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    // Small capital labels (the text is put in capitals by SectionLabel)
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(700), fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp),
    // Notes: the reflection lines, hints
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 23.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/**
 * The same type scale, adjusted for Korean. The two fonts have no Korean
 * letters, so Android draws Korean with the phone's own Korean font, which
 * reads best without tight letter spacing and a little smaller at big sizes.
 */
private val KoreanTypography = AppTypography.copy(
    displayLarge = AppTypography.displayLarge.copy(letterSpacing = 0.sp),
    headlineLarge = AppTypography.headlineLarge.copy(letterSpacing = 0.sp, fontSize = 32.sp, lineHeight = 42.sp),
    headlineMedium = AppTypography.headlineMedium.copy(letterSpacing = 0.sp, fontSize = 25.sp, lineHeight = 35.sp),
    titleLarge = AppTypography.titleLarge.copy(letterSpacing = 0.sp, fontSize = 20.sp, lineHeight = 28.sp),
    labelMedium = AppTypography.labelMedium.copy(letterSpacing = 0.3.sp, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelSmall = AppTypography.labelSmall.copy(fontSize = 15.sp, lineHeight = 23.sp),
)

/**
 * Is the app dark? Follows the choice in Settings (light, dark, or the phone).
 * Use this instead of isSystemInDarkTheme() anywhere the look depends on it.
 */
@Composable
fun isDarkTheme(): Boolean = isDark(AppAppearance.theme, isSystemInDarkTheme())

/** Wrap any screen in this to give it GoalGate's colours, fonts and shapes. */
@Composable
fun GoalGateTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    // The system bars (clock, battery, navigation) follow the app, not only the phone.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
            // The window behind the first frame matches too, so nothing flashes on start.
            window.setBackgroundDrawable(ColorDrawable((if (dark) DarkColors else LightColors).background.toArgb()))
        }
    }
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
