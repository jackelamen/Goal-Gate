package com.goalgate.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalgate.app.ui.theme.LocalAccent

/*
 * Small building blocks shared by several screens.
 */

/**
 * Keeps a screen's content at a comfortable reading width.
 * On phones it fills the width; on wide screens (an unfolded Galaxy Z Fold,
 * a tablet, landscape) it stops at 560dp and sits in the middle, instead of
 * stretching lines and buttons across the whole display.
 */
fun Modifier.readableWidth(maxWidth: Dp = 560.dp): Modifier =
    this.fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)

/**
 * One line of text that never wraps. If the words do not fit (a narrow screen
 * such as the Galaxy Z Fold cover screen, or a large system font size), the
 * text shrinks a little, down to [minFontSize], instead of breaking a word
 * in two ("Vocabular / y").
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    minFontSize: TextUnit = 10.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = constraints.maxWidth
        // Try the normal size first, then step down half a point at a time
        // until one line of text fits the available width.
        val fitted = remember(text, style, maxWidthPx) {
            var size = style.fontSize.value
            while (size > minFontSize.value) {
                val width = measurer.measure(text, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width
                if (width <= maxWidthPx) break
                size -= 0.5f
            }
            style.copy(fontSize = size.sp)
        }
        Text(text, style = fitted, color = color, textAlign = textAlign, maxLines = 1, softWrap = false)
    }
}

/** Makes anything shrink a little while it is pressed, then spring back. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.scale(scale)
}

/**
 * A white card lifted off the paper by a very soft shadow.
 * Cards are how GoalGate groups things; there are no outlined boxes.
 */
@Composable
fun Modifier.card(shape: Shape = MaterialTheme.shapes.medium, elevation: Dp = 10.dp): Modifier =
    this
        .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
        .background(MaterialTheme.colorScheme.surface, shape)

/** The main action on a screen: a solid ink pill. Use at most one per screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(interaction),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A second, quieter action: plain text with no box around it. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** Text style for Korean sentences. */
@Composable
fun koreanStyle(): TextStyle = MaterialTheme.typography.bodyMedium

/**
 * Android may wrap Korean text in the middle of a word (for example "해" at the
 * end of one line and "요." on the next). Korean puts spaces between words, so
 * we glue the letters of each word together with an invisible "word joiner"
 * character. Lines can then only break at the spaces.
 */
fun keepKoreanWordsTogether(text: String): String =
    text.split(' ').joinToString(" ") { word -> word.toList().joinToString("\u2060") }

/** A small label in spaced capitals, e.g. "COOLDOWN". */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * "Pick one of these" control, used all over Settings.
 * A white thumb sits on a grey track under the selected option.
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            val thumb by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "choiceThumb",
            )
            val textColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "choiceText",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isSelected) Modifier.shadow(3.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(thumb)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 46.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            ) {
                FitText(label(option), MaterialTheme.typography.labelLarge, textColor)
            }
        }
    }
}

/**
 * The summit mark: GoalGate's signature. A short ridge line rising to one
 * peak, with a dot on top in the accent (signal orange on the pine ground).
 *
 * With [breathing] the dot swells and settles slowly, like a calm breath.
 * When [celebrate] becomes true the dot lifts once and a soft ring spreads
 * from it. [drawIn] draws the ridge in from left to right when it first appears.
 */
@Composable
fun SummitMark(
    modifier: Modifier = Modifier,
    breathing: Boolean = false,
    celebrate: Boolean = false,
    drawIn: Boolean = false,
    lineColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val accent = LocalAccent.current.accent

    val transition = rememberInfiniteTransition(label = "breathing")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val line = remember { Animatable(if (drawIn) 0f else 1f) }
    LaunchedEffect(drawIn) { if (drawIn) line.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    val lift = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            lift.animateTo(1.6f, tween(160))
            lift.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(celebrate) { if (celebrate) ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Canvas(modifier.size(width = 34.dp, height = 26.dp)) {
        val w = size.width
        val h = size.height
        // The ridge: a low shoulder, a small dip, then the peak.
        val points = listOf(
            Offset(0f, h * 0.92f),
            Offset(w * 0.30f, h * 0.60f),
            Offset(w * 0.42f, h * 0.72f),
            Offset(w * 0.66f, h * 0.36f),
            Offset(w, h * 0.92f),
        )
        val stroke = 1.6.dp.toPx()
        // Draw each segment up to the current progress of the line.
        val total = points.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()
        var left = total * line.value
        for ((a, b) in points.zipWithNext()) {
            if (left <= 0f) break
            val length = (b - a).getDistance()
            val part = (left / length).coerceAtMost(1f)
            drawLine(lineColor, a, a + (b - a) * part, stroke, StrokeCap.Round)
            left -= length
        }

        val peak = Offset(w * 0.66f, h * 0.36f - 6.dp.toPx())
        val radius = 3.2.dp.toPx() * lift.value
        if (breathing) {
            drawCircle(accent, radius = radius + 2.dp.toPx() + 5.dp.toPx() * breath, center = peak, alpha = 0.22f - 0.16f * breath)
        }
        val r = ring.value
        if (r > 0f && r < 1f) {
            drawCircle(accent, radius = radius + 18.dp.toPx() * r, center = peak, alpha = 0.5f * (1f - r), style = Stroke(1.2.dp.toPx()))
        }
        if (line.value > 0.85f) drawCircle(accent, radius = radius, center = peak, alpha = ((line.value - 0.85f) / 0.15f).coerceIn(0f, 1f))
    }
}

/**
 * The Close button on the pause: a solid pill in the signal orange with ink
 * text. It is the one place the spark is used for a control, because Close is
 * the choice GoalGate exists to encourage.
 */
@Composable
fun SignalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val accent = LocalAccent.current
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.accent,
            contentColor = accent.onAccent,
            disabledContainerColor = accent.accent.copy(alpha = 0.3f),
            disabledContentColor = accent.onAccent.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(interaction),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/**
 * Text of any length that must fit in up to [maxLines] lines: a goal on its
 * card, for example. If it does not fit, it shrinks half a point at a time
 * down to [minFontSize] (then the last line ends in "..."). Words are never
 * broken in the middle by this; that is what the shrinking is for.
 */
@Composable
fun FitBlock(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minFontSize: TextUnit = 15.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        val fitted = remember(text, style, maxWidthPx, maxLines) {
            var size = style.fontSize.value
            val ratio = style.lineHeight.value / style.fontSize.value
            while (size > minFontSize.value) {
                val result = measurer.measure(
                    text,
                    style.copy(fontSize = size.sp, lineHeight = (size * ratio).sp),
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                if (!result.hasVisualOverflow) break
                size -= 0.5f
            }
            style.copy(fontSize = size.sp, lineHeight = (size * ratio).sp)
        }
        Text(text, style = fitted, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
