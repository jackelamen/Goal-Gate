package com.goalgate.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.goalgate.app.ui.theme.HeroAccent
import com.goalgate.app.ui.theme.HeroColors
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.RidgeFar
import com.goalgate.app.ui.theme.RidgeMid
import com.goalgate.app.ui.theme.RidgeNear
import com.goalgate.app.ui.theme.SkyNight
import com.goalgate.app.ui.theme.SkyRose
import com.goalgate.app.ui.theme.SkyViolet
import com.goalgate.app.ui.theme.Sun
import com.goalgate.app.ui.theme.SunGlow
import kotlin.math.sin

/*
 * THE DAWN SKY: where GoalGate shows its character.
 *
 * Most screens are warm paper. The moments that matter (the pause, the
 * welcome, the week card) sit on a sunrise instead:
 *
 *   - the sky, night blue at the top, through violet, to rose at the horizon;
 *   - a few last stars near the top;
 *   - the sun coming up behind the mountains, with its glow;
 *   - three ridges in front, the nearest one darkest. The buttons at the
 *     bottom of the pause sit on the dark ridges, so they stay easy to read.
 *
 * Nothing here moves: the only thing that breathes on these screens is the
 * sun in the summit mark. The shapes come from fixed sine waves, so the
 * picture is identical every time and is worked out once per size.
 */

/**
 * Draws the dawn sky and switches the colours of everything inside it: text
 * becomes white, the main button becomes white, and the accent becomes the
 * sun gold.
 *
 * @param sunX        where the sun rises, as a fraction of the width.
 * @param horizon     where the mountains begin, as a fraction of the height.
 *                    Text below it sits on the dark ridges. The welcome screen
 *                    raises it, so its paragraphs are on the mountains.
 * @param fullScreen  true when this fills the whole window (it then also makes
 *                    the status bar icons light). Use false for a card.
 */
@Composable
fun HeroGround(
    modifier: Modifier = Modifier,
    sunX: Float = 0.8f,
    fullScreen: Boolean = true,
    horizon: Float = if (fullScreen) 0.77f else 0.72f,
    content: @Composable BoxScope.() -> Unit,
) {
    if (fullScreen) LightStatusBarIcons()

    Box(
        modifier
            .clipToBounds()
            .background(Brush.verticalGradient(0f to SkyNight, 0.5f to SkyViolet, 0.86f to SkyRose))
            .sunrise(sunX, horizon, showSun = fullScreen),
    ) {
        // Everything in front uses the "on the dawn sky" colours.
        CompositionLocalProvider(LocalAccent provides HeroAccent) {
            MaterialTheme(colorScheme = HeroColors, content = { content() })
        }
    }
}

/** Stars, the sun and its glow, and the three ridges. */
private fun Modifier.sunrise(sunX: Float, horizonAt: Float, showSun: Boolean): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val horizon = h * horizonAt
    val far = ridge(size, top = horizon, height = h * 0.050f, waves = 2.1f, shift = 0.4f)
    val mid = ridge(size, top = horizon + (h - horizon) * 0.34f, height = h * 0.045f, waves = 3.3f, shift = 2.0f)
    val near = ridge(size, top = horizon + (h - horizon) * 0.64f, height = h * 0.035f, waves = 2.6f, shift = 4.1f)
    // The sun is mostly behind the far ridge: only its top shows, clear of the text above.
    val sun = Offset(w * sunX, horizon + w * 0.035f)
    val glow = Brush.radialGradient(
        0f to SunGlow.copy(alpha = 0.85f), 0.45f to SunGlow.copy(alpha = 0.32f), 1f to Color.Transparent,
        center = sun, radius = w * 0.62f,
    )
    val starSize = 1.3.dp.toPx()
    onDrawBehind {
        // A few last stars, fading towards the horizon. Fixed places, not random.
        for (i in 0 until 16) {
            val x = w * ((i * 0.618f + 0.11f) % 1f)
            val y = h * 0.42f * ((i * 0.377f + 0.07f) % 1f)
            drawCircle(Color.White, radius = starSize * (0.6f + (i % 3) * 0.3f), center = Offset(x, y), alpha = 0.55f * (1f - y / (h * 0.42f)) + 0.1f)
        }
        drawRect(glow)
        // On a card there is only the glow: numbers sit where the sun would be.
        if (showSun) drawCircle(Sun, radius = w * 0.07f, center = sun)
        drawPath(far, RidgeFar)
        drawPath(mid, RidgeMid)
        drawPath(near, RidgeNear)
    }
}

/** One mountain ridge: a wavy line at [top], filled down to the bottom edge. */
private fun ridge(size: Size, top: Float, height: Float, waves: Float, shift: Float): Path = Path().apply {
    val steps = 48
    moveTo(0f, size.height)
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val y = top - height * (0.62f * sin(t * waves * 3.1416f + shift) + 0.38f * sin(t * waves * 2.3f * 3.1416f + shift * 1.7f))
        lineTo(size.width * t, y)
    }
    lineTo(size.width, size.height)
    close()
}

/**
 * Makes the status bar icons (clock, battery) light while a full-screen
 * dawn sky is showing, and puts them back afterwards.
 */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = !darkTheme }
    }
}
