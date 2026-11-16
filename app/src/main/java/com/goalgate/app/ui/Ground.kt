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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.goalgate.app.ui.theme.Contour
import com.goalgate.app.ui.theme.LocalAccent
import com.goalgate.app.ui.theme.PineColors
import com.goalgate.app.ui.theme.PineDeep
import com.goalgate.app.ui.theme.PineGround
import com.goalgate.app.ui.theme.SignalAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * THE PINE GROUND: where GoalGate shows its character.
 *
 * Most screens are quiet paper. The moments that matter (the pause, the
 * welcome, the week card) sit on a deep pine ground instead, with faint
 * contour lines behind the content, like the lines on a trail map around a
 * summit. They never move: the only thing that breathes on these screens is
 * the dot at the top of the summit mark.
 *
 * The lines are drawn at 6% white (every fifth one a little stronger, as on a
 * real map), so they give depth without competing with the text in front.
 */

/**
 * Draws the pine background and its contour lines, and switches the colours
 * of everything inside it: text becomes white, the main button becomes white,
 * and the accent becomes the signal orange.
 *
 * @param peak        where the summit of the contour lines sits, as fractions
 *                    of the width and height. Keep it away from the main text.
 * @param fullScreen  true when this fills the whole window (it then also makes
 *                    the status bar icons light). Use false for a card.
 */
@Composable
fun HeroGround(
    modifier: Modifier = Modifier,
    peak: Offset = Offset(0.86f, 0.78f),
    fullScreen: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (fullScreen) LightStatusBarIcons()

    Box(
        modifier
            .clipToBounds()
            .background(Brush.verticalGradient(listOf(PineGround, PineDeep))),
    ) {
        Box(Modifier.matchParentSize().contourLines(peak))
        // Everything in front uses the "on pine" colours.
        CompositionLocalProvider(LocalAccent provides SignalAccent) {
            MaterialTheme(colorScheme = PineColors, content = { content() })
        }
    }
}

/**
 * Nested, slightly irregular rings around [peak], like the contour lines of
 * a hill. The shapes come from fixed sine waves, so they are identical every
 * time (no randomness) and are worked out once per size.
 */
private fun Modifier.contourLines(peak: Offset): Modifier = drawWithCache {
    val center = Offset(size.width * peak.x, size.height * peak.y)
    val paths = contourPaths(size, center)
    val thin = Stroke(width = 1.dp.toPx())
    val index = Stroke(width = 1.4.dp.toPx())
    onDrawBehind {
        paths.forEachIndexed { i, path ->
            val isIndex = i % 5 == 4
            drawPath(path, Contour, alpha = if (isIndex) 0.11f else 0.06f, style = if (isIndex) index else thin)
        }
    }
}

private fun contourPaths(size: Size, center: Offset): List<Path> {
    val reach = max(size.width, size.height) * 1.25f
    val step = max(size.width, size.height) * 0.052f
    val rings = (reach / step).toInt().coerceAtMost(40)
    return List(rings) { k ->
        val base = step * (k + 1)
        Path().apply {
            val points = 96
            for (p in 0..points) {
                val t = (p.toFloat() / points) * 2f * PI.toFloat()
                // Two slow waves bend each ring; they drift a little from ring to ring,
                // so the slopes look steeper on one side, as on a real hill.
                val wobble = 1f + 0.10f * sin(3f * t + k * 0.35f) + 0.06f * sin(5f * t - k * 0.6f) +
                    0.05f * cos(2f * t + 1.3f)
                val r = base * wobble
                val x = center.x + r * cos(t) * 1.15f
                val y = center.y + r * sin(t) * 0.9f
                if (p == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}

/**
 * Makes the status bar icons (clock, battery) light while a full-screen
 * pine ground is showing, and puts them back afterwards.
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
